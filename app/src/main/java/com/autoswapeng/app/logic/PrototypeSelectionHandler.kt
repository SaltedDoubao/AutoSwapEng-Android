package com.autoswapeng.app.logic

import com.autoswapeng.app.accessibility.ScreenCaptureHelper
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.ocr.MiniProgramRegions
import kotlinx.coroutines.delay

/**
 * 严格按照桌面端原型实现的选择题处理器（学习→测试循环）
 * - ROI 基于 .local/AutoSwapEng-python 中的 PWORD/KWORD/PCHIN/PTEST/PSELC 比例
 * - 学习阶段：记录 单词(WORD_AREA) + 释义(DEFINITION_AREA)
 * - 测试阶段：
 *   a) 英→中：题目英文单词 + 中文选项，用 WordMatcher.match
 *   b) 中→英：题目中文释义 + 英文选项，用 WordMatcher.matchByDefinition
 */
class PrototypeSelectionHandler(
    private val screenCapture: ScreenCaptureHelper,
    private val tap: suspend (Int, Int) -> Unit,
    private val swipeUp: suspend () -> Unit,
    private val onProgress: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "PrototypeHandler"
        private const val CLICK_DELAY = 800L
        private const val SWIPE_DELAY = 800L
        private const val MAX_LOOPS = 80
    }

    private val matcher = WordMatcher()

    suspend fun run() {
        LogManager.i(TAG, "========== 原型化选择题流程启动 ==========")
        var learned = 0
        var answered = 0

        repeat(MAX_LOOPS) { loopIdx ->
            LogManager.i(TAG, "--- 循环 ${loopIdx + 1}/$MAX_LOOPS ---")

            // 1) 点击进入/继续（与原型 CLICK 点一致）
            val (cx, cy) = MiniProgramRegions.Selection.NEXT_TAP
                .toPixelPoint(screenCapture.screenWidth, screenCapture.screenHeight)
            tap(cx, cy)
            delay(CLICK_DELAY)

            // 2) 读取测试判断区域（原型 PTEST）：空 => 学习；非空 => 测试
            val testProbe = screenCapture.captureAndRecognize(
                MiniProgramRegions.Selection.TEST_AREA,
                "probe-test"
            ).trim()

            val isLearning = testProbe.isBlank()
            if (isLearning) {
                com.autoswapeng.app.log.LogManager.event(
                    code = "FLOW_PHASE",
                    tag = TAG,
                    message = "进入学习阶段",
                    data = mapOf("loop" to (loopIdx + 1), "probeLen" to testProbe.length)
                )
                // 学习阶段：读取单词与释义，写入记忆库
                val wordRaw = screenCapture.captureAndRecognize(
                    MiniProgramRegions.Selection.WORD_AREA,
                    "learn-word"
                ).trim()
                val word = Regex("[A-Za-z]{2,32}").find(wordRaw)?.value

                val defText = screenCapture.captureAndRecognize(
                    MiniProgramRegions.Selection.DEFINITION_AREA,
                    "learn-definition"
                )
                val definition = extractDefinition(defText)

                if (word != null && definition != null) {
                    matcher.learn(word, listOf(definition))
                    learned++
                    onProgress?.invoke("📝 学习($learned): $word")
                    LogManager.i(TAG, "✓ 学习: $word -> $definition")
                    com.autoswapeng.app.log.LogManager.event(
                        code = "LEARN_ADD",
                        tag = TAG,
                        message = "记忆库增加",
                        data = mapOf("word" to word, "defLen" to definition.length, "count" to learned)
                    )
                } else {
                    LogManager.w(TAG, "学习阶段信息不完整: word=$word, def=${definition != null}")
                    com.autoswapeng.app.log.LogManager.event(
                        code = "LEARN_MISS",
                        tag = TAG,
                        message = "学习信息不完整",
                        data = mapOf("hasWord" to (word != null), "hasDef" to (definition != null)),
                        level = com.autoswapeng.app.log.LogManager.LogEntry.Level.WARN
                    )
                }

                swipeUp(); delay(SWIPE_DELAY)
                return@repeat
            }

            // 测试阶段：判定题型并作答
            val optionsRaw = screenCapture.captureAndRecognize(
                MiniProgramRegions.Selection.OPTIONS_AREA,
                "quiz-options"
            )
            val isChineseOptions = containsChinese(optionsRaw)

            if (isChineseOptions) {
                com.autoswapeng.app.log.LogManager.event(
                    code = "QUIZ_MODE",
                    tag = TAG,
                    message = "英→中",
                    data = mapOf("loop" to (loopIdx + 1))
                )
                // 英→中：提取英文单词 + 中文选项
                val wordRaw = screenCapture.captureAndRecognize(
                    MiniProgramRegions.Selection.QUESTION_WORD_AREA,
                    "quiz-word"
                ).trim()
                val word = Regex("[A-Za-z]{2,32}").find(wordRaw)?.value
                val options = extractChineseOptions(optionsRaw)

                if (word.isNullOrEmpty() || options.size < 4) {
                    LogManager.w(TAG, "选择题数据不足(英→中): word=$word, options=${options.size}")
                    fallbackSequentialClick()
                    com.autoswapeng.app.log.LogManager.event(
                        code = "QUIZ_FALLBACK",
                        tag = TAG,
                        message = "数据不足，顺序尝试",
                        data = mapOf("hasWord" to (!word.isNullOrEmpty()), "opt" to options.size),
                        level = com.autoswapeng.app.log.LogManager.LogEntry.Level.WARN
                    )
                } else {
                    val idx = matcher.match(word, options)
                    clickOption(idx)
                    answered++
                    onProgress?.invoke("✅ 答题($answered): 选项 ${'A' + idx}")
                    delay(CLICK_DELAY)
                    com.autoswapeng.app.log.LogManager.event(
                        code = "QUIZ_MATCH",
                        tag = TAG,
                        message = "完成作答",
                        data = mapOf("word" to word, "optCount" to options.size, "choose" to idx, "answered" to answered)
                    )
                }
            } else {
                com.autoswapeng.app.log.LogManager.event(
                    code = "QUIZ_MODE",
                    tag = TAG,
                    message = "中→英",
                    data = mapOf("loop" to (loopIdx + 1))
                )
                // 中→英：提取中文释义 + 英文选项
                val defText = screenCapture.captureAndRecognize(
                    MiniProgramRegions.Selection.DEFINITION_AREA,
                    "quiz-definition"
                )
                val definition = extractDefinition(defText)
                val englishOptions = extractEnglishOptions(optionsRaw)

                if (definition == null || englishOptions.size < 4) {
                    LogManager.w(TAG, "选择题数据不足(中→英): def=${definition != null}, options=${englishOptions.size}")
                    fallbackSequentialClick()
                    com.autoswapeng.app.log.LogManager.event(
                        code = "QUIZ_FALLBACK",
                        tag = TAG,
                        message = "数据不足，顺序尝试",
                        data = mapOf("hasDef" to (definition != null), "opt" to englishOptions.size),
                        level = com.autoswapeng.app.log.LogManager.LogEntry.Level.WARN
                    )
                } else {
                    val idx = matcher.matchByDefinition(definition, englishOptions)
                    clickOption(idx)
                    answered++
                    onProgress?.invoke("✅ 答题($answered): 选项 ${'A' + idx}")
                    delay(CLICK_DELAY)
                    com.autoswapeng.app.log.LogManager.event(
                        code = "QUIZ_MATCH",
                        tag = TAG,
                        message = "完成作答",
                        data = mapOf("defLen" to definition.length, "optCount" to englishOptions.size, "choose" to idx, "answered" to answered)
                    )
                }
            }

            swipeUp(); delay(SWIPE_DELAY)
        }

        LogManager.i(TAG, "完成：学习 $learned，答题 $answered")
    }

    // ============== 工具函数 ==============

    private fun containsChinese(text: String): Boolean = text.any { it in '\u4e00'..'\u9fff' }

    private fun extractDefinition(raw: String): String? {
        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (line.contains(Regex("[nvadj]{1,4}\\.")) && containsChinese(line)) {
                return line
            }
        }
        return lines.firstOrNull { containsChinese(it) }
    }

    private fun extractChineseOptions(raw: String): List<String> {
        // 优先按 A./B./C./D. 切片
        val pattern = Regex("[ABCD]\\.(.*?)(?=[ABCD]\\.|$)")
        val parts = pattern.findAll(raw).map { it.value.substring(2).trim() }.filter { containsChinese(it) }.toList()
        if (parts.size >= 4) return parts.take(4)

        // 回退：逐行取中文
        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() && containsChinese(it) }
        return lines.take(4)
    }

    private fun extractEnglishOptions(raw: String): List<String> {
        val englishWordRegex = Regex("^[A-Za-z]{2,32}$")
        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        // 优先按 A./B./C./D.
        val pattern = Regex("[ABCD]\\.([A-Za-z]{2,32})")
        val parts = pattern.findAll(raw).map { it.groupValues.getOrNull(1) ?: "" }.filter { it.matches(englishWordRegex) }.toList()
        if (parts.size >= 4) return parts.take(4)

        // 回退：逐行取英文词
        return lines.filter { it.matches(englishWordRegex) }.take(4)
    }

    private suspend fun clickOption(index: Int) {
        val point = when (index) {
            0 -> MiniProgramRegions.Selection.OPTION_A
            1 -> MiniProgramRegions.Selection.OPTION_B
            2 -> MiniProgramRegions.Selection.OPTION_C
            3 -> MiniProgramRegions.Selection.OPTION_D
            else -> MiniProgramRegions.Selection.OPTION_A
        }
        val (x, y) = point.toPixelPoint(screenCapture.screenWidth, screenCapture.screenHeight)
        LogManager.d(TAG, "点击选项 ${'A' + index}: ($x, $y)")
        tap(x, y)
    }

    private suspend fun fallbackSequentialClick() {
        LogManager.w(TAG, "置信度不足或数据缺失，回退顺序尝试")
        for (i in 0..3) {
            clickOption(i)
            delay(CLICK_DELAY)
        }
    }
}


