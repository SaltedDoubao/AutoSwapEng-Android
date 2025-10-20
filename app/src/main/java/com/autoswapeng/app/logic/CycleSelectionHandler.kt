package com.autoswapeng.app.logic

import com.autoswapeng.app.accessibility.ScreenCaptureHelper
import com.autoswapeng.app.accessibility.NodeText
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.ocr.MiniProgramRegions
import kotlinx.coroutines.delay
import android.graphics.Rect

/**
 * 循环选择题处理器 - 适配"5学习+5答题"模式
 * 
 * 完整流程（25个单词）：
 * 1. 学习5个单词（点击显示释义 -> 上滑下一个）
 * 2. 答题5道题（选择正确答案 -> 上滑下一题）
 * 3. 重复5次（共25个单词）
 */
class CycleSelectionHandler(
    private val screenCapture: ScreenCaptureHelper,
    private val tap: suspend (Int, Int) -> Unit,
    private val swipeUp: suspend () -> Unit,  // 上滑手势
    private val onProgress: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "CycleSelectionHandler"
        private const val WORDS_PER_CYCLE = 5      // 每轮学习单词数
        private const val QUESTIONS_PER_CYCLE = 5   // 每轮答题数
        private const val TOTAL_CYCLES = 5          // 总轮数
        private const val CLICK_DELAY = 800L        // 点击后延迟
        private const val SWIPE_DELAY = 1000L       // 滑动后延迟
        private const val OCR_RETRY = 2             // OCR重试次数
    }
    
    private val wordMatcher = WordMatcher()
    private val detector = QuestionTypeDetector()
    
    // 状态跟踪
    private var currentCycle = 0
    private var wordsLearned = 0
    private var questionsAnswered = 0
    private var totalCorrect = 0
    
    /**
     * 执行完整的25个单词流程
     */
    suspend fun executeFullSession() {
        LogManager.i(TAG, "========== 开始完整学习流程（25个单词） ==========")
        onProgress?.invoke("📚 开始学习流程（共25个单词）")
        
        try {
            for (cycle in 1..TOTAL_CYCLES) {
                currentCycle = cycle
                LogManager.i(TAG, "===== 第 $cycle/$TOTAL_CYCLES 轮 =====")
                onProgress?.invoke("📖 第 $cycle/5 轮学习")
                
                // 1. 学习阶段：5个单词
                if (!executeLearnPhase()) {
                    LogManager.e(TAG, "学习阶段失败")
                    return
                }
                
                // 2. 答题阶段：5道题
                if (!executeQuizPhase()) {
                    LogManager.e(TAG, "答题阶段失败")
                    return
                }
                
                LogManager.i(TAG, "✓ 第 $cycle 轮完成")
            }
            
            LogManager.i(TAG, "========== 学习流程完成 ==========")
            onProgress?.invoke("🎉 完成！共学习 $wordsLearned 个单词，答对 $totalCorrect 题")
            
        } catch (e: Exception) {
            LogManager.e(TAG, "流程执行失败: ${e.message}")
            e.printStackTrace()
            onProgress?.invoke("❌ 流程中断")
        }
    }
    
    /**
     * 学习阶段：学习5个单词
     */
    private suspend fun executeLearnPhase(): Boolean {
        LogManager.i(TAG, "进入学习阶段")
        
        for (i in 1..WORDS_PER_CYCLE) {
            try {
                LogManager.i(TAG, "学习第 $i/$WORDS_PER_CYCLE 个单词")
                
                // 1. OCR识别当前单词（第一次点击前）
                val wordBeforeClick = captureWord()
                if (wordBeforeClick.isEmpty()) {
                    LogManager.w(TAG, "未能识别单词，尝试继续")
                }
                
                // 2. 点击卡片显示释义
                clickCardCenter()
                delay(CLICK_DELAY)
                
                // 3. OCR识别释义
                val definition = captureDefinition()
                if (wordBeforeClick.isNotEmpty() && definition.isNotEmpty()) {
                    // 存储到记忆库
                    wordMatcher.learn(wordBeforeClick, listOf(definition))
                    wordsLearned++
                    LogManager.i(TAG, "✓ 学习: $wordBeforeClick -> $definition")
                    onProgress?.invoke("📝 学习 ($wordsLearned): $wordBeforeClick")
                }
                
                // 4. 上滑到下一个单词（最后一个不滑）
                if (i < WORDS_PER_CYCLE) {
                    swipeUp()
                    delay(SWIPE_DELAY)
                }
                
            } catch (e: Exception) {
                LogManager.e(TAG, "学习单词 $i 失败: ${e.message}")
                // 继续下一个
                swipeUp()
                delay(SWIPE_DELAY)
            }
        }
        
        // 学习完5个后，需要上滑进入答题
        LogManager.i(TAG, "学习阶段完成，上滑进入答题")
        swipeUp()
        delay(SWIPE_DELAY * 2)  // 给更多时间加载答题页面
        
        return true
    }
    
    /**
     * 答题阶段：回答5道选择题
     */
    private suspend fun executeQuizPhase(): Boolean {
        LogManager.i(TAG, "进入答题阶段")
        questionsAnswered = 0
        
        for (i in 1..QUESTIONS_PER_CYCLE) {
            try {
                LogManager.i(TAG, "回答第 $i/$QUESTIONS_PER_CYCLE 题")
                
                // 1. 识别题目和选项
                val quizData = captureQuizData()
                if (quizData == null) {
                    LogManager.e(TAG, "无法识别题目数据")
                    // 随机选一个
                    clickRandomOption()
                } else {
                    // 2. 智能选择或顺序尝试
                    val success = answerQuestion(quizData)
                    if (success) {
                        totalCorrect++
                        onProgress?.invoke("✅ 第 $i 题正确 (总计 $totalCorrect)")
                    } else {
                        onProgress?.invoke("❓ 第 $i 题")
                    }
                }
                
                questionsAnswered++
                
                // 3. 上滑到下一题（最后一题后也要滑，进入下一轮学习）
                delay(CLICK_DELAY)
                swipeUp()
                delay(SWIPE_DELAY)
                
            } catch (e: Exception) {
                LogManager.e(TAG, "答题 $i 失败: ${e.message}")
                // 继续下一题
                swipeUp()
                delay(SWIPE_DELAY)
            }
        }
        
        LogManager.i(TAG, "答题阶段完成")
        return true
    }
    
    /**
     * OCR识别单词
     */
    private suspend fun captureWord(): String {
        repeat(OCR_RETRY) { attempt ->
            try {
                val raw = screenCapture.captureAndRecognize(
                    MiniProgramRegions.Selection.WORD_AREA,
                    "word-$attempt"
                ).trim()
                
                // 提取纯英文单词
                val word = Regex("[A-Za-z]{2,32}").find(raw)?.value
                if (!word.isNullOrEmpty()) {
                    return word
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "OCR识别单词失败: ${e.message}")
            }
            
            if (attempt < OCR_RETRY - 1) delay(200)
        }
        return ""
    }
    
    /**
     * OCR识别释义
     */
    private suspend fun captureDefinition(): String {
        try {
            // 使用更大的区域捕获释义，返回单个字符串
            val text = screenCapture.captureAndRecognize(
                MiniProgramRegions.Selection.OPTIONS_AREA,  // 复用选项区域
                "definition"
            )
            
            // 解析文本行
            val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            
            // 找出包含中文和词性的行
            for (line in lines) {
                if (line.contains(Regex("[nvadj]{1,4}\\.")) && 
                    line.any { it in '\u4e00'..'\u9fff' }) {
                    return line
                }
            }
            
            // 回退：返回第一个包含中文的
            return lines.firstOrNull { line -> 
                line.any { c -> c in '\u4e00'..'\u9fff' } 
            } ?: ""
            
        } catch (e: Exception) {
            LogManager.e(TAG, "OCR识别释义失败: ${e.message}")
            return ""
        }
    }
    
    /**
     * OCR识别答题页面数据
     */
    private suspend fun captureQuizData(): QuizData? {
        try {
            // 识别题目单词
            val word = captureWord()
            if (word.isEmpty()) {
                LogManager.w(TAG, "未识别到题目单词")
                return null
            }
            
            // 识别四个选项
            val optionsText = screenCapture.captureAndRecognize(
                MiniProgramRegions.Selection.OPTIONS_AREA,
                "quiz-options"
            )
            
            // 解析文本行
            val allLines = optionsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            
            // 解析选项（A-D开头的行）
            val options = mutableListOf<String>()
            for (line in allLines) {
                when {
                    line.startsWith("A.") -> {
                        if (options.size == 0) options.add(line.substring(2).trim())
                    }
                    line.startsWith("B.") -> {
                        if (options.size == 1) options.add(line.substring(2).trim())
                    }
                    line.startsWith("C.") -> {
                        if (options.size == 2) options.add(line.substring(2).trim())
                    }
                    line.startsWith("D.") -> {
                        if (options.size == 3) options.add(line.substring(2).trim())
                    }
                }
            }
            
            // 如果没找到标准格式，尝试按位置分割
            if (options.size < 4) {
                val chineseTexts = allLines.filter { line ->
                    line.any { c -> c in '\u4e00'..'\u9fff' } 
                }.take(4)
                
                if (chineseTexts.size >= 4) {
                    return QuizData(word, chineseTexts)
                }
            }
            
            return if (options.size >= 4) {
                QuizData(word, options)
            } else {
                LogManager.w(TAG, "选项不足4个: ${options.size}")
                null
            }
            
        } catch (e: Exception) {
            LogManager.e(TAG, "识别答题数据失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 回答问题
     */
    private suspend fun answerQuestion(quiz: QuizData): Boolean {
        LogManager.i(TAG, "题目: ${quiz.word}")
        LogManager.i(TAG, "选项: ${quiz.options.joinToString(" | ")}")
        
        // 1. 尝试智能匹配
        val bestIndex = wordMatcher.match(quiz.word, quiz.options)
        
        // 2. 计算置信度（简化版）
        val confidence = if (wordMatcher.getLearnedCount() > 0) 0.7f else 0.0f
        
        if (confidence >= 0.6f && bestIndex >= 0) {
            // 高置信度，直接点击
            LogManager.i(TAG, "智能选择: 选项 ${'A' + bestIndex} (置信度: $confidence)")
            clickOption(bestIndex)
            return true
        } else {
            // 低置信度，顺序尝试
            LogManager.w(TAG, "置信度低，使用顺序尝试")
            return tryOptionsSequentially(quiz.word)
        }
    }
    
    /**
     * 顺序尝试选项
     */
    private suspend fun tryOptionsSequentially(initialWord: String): Boolean {
        for (i in 0..3) {
            clickOption(i)
            delay(CLICK_DELAY)
            
            // 检查题目是否变化
            val newWord = captureWord()
            if (newWord != initialWord && newWord.isNotEmpty()) {
                LogManager.i(TAG, "✓ 选项 ${'A' + i} 正确")
                return true
            }
        }
        
        LogManager.w(TAG, "所有选项都不对？")
        return false
    }
    
    /**
     * 点击卡片中心（显示释义）
     */
    private suspend fun clickCardCenter() {
        // 点击单词区域的中心
        val (x, y) = MiniProgramRegions.Selection.WORD_AREA.let {
            val rect = it.toPixelRect(screenCapture.screenWidth, screenCapture.screenHeight)
            Pair(
                (rect.left + rect.right) / 2,
                (rect.top + rect.bottom) / 2
            )
        }
        
        LogManager.d(TAG, "点击卡片中心: ($x, $y)")
        tap(x, y)
    }
    
    /**
     * 点击指定选项
     */
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
    
    /**
     * 随机点击一个选项
     */
    private suspend fun clickRandomOption() {
        val randomIndex = (0..3).random()
        LogManager.w(TAG, "随机选择: 选项 ${'A' + randomIndex}")
        clickOption(randomIndex)
    }
    
    /**
     * 答题数据
     */
    data class QuizData(
        val word: String,
        val options: List<String>
    )
}
