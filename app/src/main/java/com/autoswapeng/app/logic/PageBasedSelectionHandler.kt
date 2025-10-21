package com.autoswapeng.app.logic

import com.autoswapeng.app.accessibility.ScreenCaptureHelper
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.ocr.MiniProgramRegions
import kotlinx.coroutines.delay
import android.graphics.Rect

/**
 * 基于页面状态识别的选择题处理器
 * 
 * 设计思想：智能识别当前所处页面类型，然后执行对应操作
 * 不再使用固定循环流程，而是根据实际页面状态动态响应
 * 
 * 支持的页面状态：
 * - 状态1 (t1): 仅显示单词和音标
 * - 状态2 (t2): 显示单词、音标和释义
 * - 状态3 (t3): 选择题页面（单词 + 四个选项）
 * - 状态4 (t4): 提示框"必须要作答哦"
 * - 状态5 (t5): 强化完成页面
 */
class PageBasedSelectionHandler(
    private val screenCapture: ScreenCaptureHelper,
    private val tap: suspend (Int, Int) -> Unit,
    private val swipeUp: suspend () -> Unit,
    private val onProgress: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "PageBasedHandler"
        
        // 延迟配置
        private const val CLICK_DELAY = 800L        // 点击后延迟
        private const val SWIPE_DELAY = 1200L       // 滑动后延迟
        private const val STATE_CHECK_DELAY = 500L  // 状态检查延迟
        
        // 重试配置
        private const val MAX_RETRIES = 3           // 最大重试次数
        private const val MAX_ITERATIONS = 50       // 最大迭代次数（防止死循环）
    }
    
    /**
     * 页面状态枚举
     */
    enum class PageState {
        WORD_ONLY,          // t1: 仅单词
        WORD_WITH_DEF,      // t2: 单词+释义
        QUIZ,               // t3: 选择题
        ANSWER_PROMPT,      // t4: "必须要作答哦"提示
        COMPLETION,         // t5: 强化完成
        UNKNOWN             // 未知状态
    }
    
    private val wordMatcher = WordMatcher()
    private var iterationCount = 0
    private var wordsLearned = 0
    private var questionsAnswered = 0
    
    /**
     * 执行自动学习流程
     * 持续识别页面状态并执行对应操作，直到完成或达到迭代上限
     */
    suspend fun executeAutoFlow() {
        LogManager.i(TAG, "========== 开始基于页面状态的自动流程 ==========")
        onProgress?.invoke("🚀 启动智能学习流程")
        
        iterationCount = 0
        
        try {
            while (iterationCount < MAX_ITERATIONS) {
                iterationCount++
                LogManager.i(TAG, "--- 迭代 $iterationCount/$MAX_ITERATIONS ---")
                
                // 等待页面稳定
                delay(STATE_CHECK_DELAY)
                
                // 识别当前页面状态
                val state = detectPageState()
                LogManager.i(TAG, "检测到页面状态: $state")
                
                // 根据状态执行对应操作
                val shouldContinue = handlePageState(state)
                
                if (!shouldContinue) {
                    LogManager.i(TAG, "流程结束")
                    break
                }
            }
            
            if (iterationCount >= MAX_ITERATIONS) {
                LogManager.w(TAG, "达到最大迭代次数，停止流程")
                onProgress?.invoke("⚠️ 达到最大操作次数")
            }
            
            LogManager.i(TAG, "========== 流程完成 ==========")
            LogManager.i(TAG, "学习单词数: $wordsLearned")
            LogManager.i(TAG, "回答题目数: $questionsAnswered")
            onProgress?.invoke("✅ 完成！学习 $wordsLearned 词，答题 $questionsAnswered 道")
            
        } catch (e: Exception) {
            LogManager.e(TAG, "流程执行失败: ${e.message}")
            e.printStackTrace()
            onProgress?.invoke("❌ 流程中断: ${e.message}")
        }
    }
    
    /**
     * 检测当前页面状态
     * 通过OCR识别页面特征来判断
     */
    private suspend fun detectPageState(): PageState {
        try {
            // 捕获页面文本
            val wordAreaText = captureText(MiniProgramRegions.Selection.WORD_AREA, "word-area")
            val optionsAreaText = captureText(MiniProgramRegions.Selection.OPTIONS_AREA, "options-area")
            
            LogManager.d(TAG, "单词区: $wordAreaText")
            LogManager.d(TAG, "选项区: $optionsAreaText")
            
            // 特征1: 检查是否有"强化"字样（完成页面）
            if (optionsAreaText.contains("强化") && optionsAreaText.contains("继续")) {
                return PageState.COMPLETION
            }
            
            // 特征2: 检查是否有"必须要作答哦"提示
            if (optionsAreaText.contains("必须要作答") || optionsAreaText.contains("必须作答")) {
                return PageState.ANSWER_PROMPT
            }
            
            // 特征3: 检查是否有选项标记 A. B. C. D.
            val hasOptions = optionsAreaText.contains(Regex("[ABCD]\\."))
            
            // 提取单词（纯英文）
            val word = Regex("[A-Za-z]{2,32}").find(wordAreaText)?.value
            
            // 提取中文释义
            val hasChinese = optionsAreaText.any { it in '\u4e00'..'\u9fff' }
            
            return when {
                // 选择题：有单词 + 有选项标记
                word != null && hasOptions -> PageState.QUIZ
                
                // 单词+释义：有单词 + 有中文但无选项标记
                word != null && hasChinese && !hasOptions -> PageState.WORD_WITH_DEF
                
                // 仅单词：有单词但无中文
                word != null && !hasChinese -> PageState.WORD_ONLY
                
                else -> PageState.UNKNOWN
            }
            
        } catch (e: Exception) {
            LogManager.e(TAG, "检测页面状态失败: ${e.message}")
            return PageState.UNKNOWN
        }
    }
    
    /**
     * 根据页面状态执行对应操作
     * @return 是否应该继续流程
     */
    private suspend fun handlePageState(state: PageState): Boolean {
        return when (state) {
            PageState.WORD_ONLY -> handleWordOnly()
            PageState.WORD_WITH_DEF -> handleWordWithDefinition()
            PageState.QUIZ -> handleQuiz()
            PageState.ANSWER_PROMPT -> handleAnswerPrompt()
            PageState.COMPLETION -> handleCompletion()
            PageState.UNKNOWN -> handleUnknown()
        }
    }
    
    /**
     * 操作一：仅单词页面 (t1)
     * 特征：页面只有单词和音标
     * 操作：单击屏幕一次（显示释义）
     */
    private suspend fun handleWordOnly(): Boolean {
        LogManager.i(TAG, "▶ 操作一：点击显示释义")
        onProgress?.invoke("📖 显示释义...")
        
        // 点击屏幕中心区域
        val centerX = screenCapture.screenWidth / 2
        val centerY = screenCapture.screenHeight / 2
        
        tap(centerX, centerY)
        delay(CLICK_DELAY)
        
        return true // 继续流程
    }
    
    /**
     * 操作二：单词+释义页面 (t2)
     * 特征：页面有单词、音标和释义
     * 操作：OCR识别并记录单词与释义，然后上滑
     */
    private suspend fun handleWordWithDefinition(): Boolean {
        LogManager.i(TAG, "▶ 操作二：学习单词")
        
        try {
            // 识别单词
            val wordText = captureText(MiniProgramRegions.Selection.WORD_AREA, "word")
            val word = Regex("[A-Za-z]{2,32}").find(wordText)?.value
            
            // 识别释义
            val defText = captureText(MiniProgramRegions.Selection.OPTIONS_AREA, "definition")
            val definition = extractDefinition(defText)
            
            if (word != null && definition != null) {
                // 存储到记忆库
                wordMatcher.learn(word, listOf(definition))
                wordsLearned++
                
                LogManager.i(TAG, "✓ 学习: $word -> $definition")
                onProgress?.invoke("📝 学习($wordsLearned): $word")
            } else {
                LogManager.w(TAG, "未能完整识别单词或释义")
            }
            
            // 上滑到下一个
            swipeUp()
            delay(SWIPE_DELAY)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "学习单词失败: ${e.message}")
            // 尝试继续
            swipeUp()
            delay(SWIPE_DELAY)
        }
        
        return true // 继续流程
    }
    
    /**
     * 操作三：选择题页面 (t3)
     * 特征：页面有单词和四个选项
     * 操作：识别题目和选项，选择正确答案，然后上滑
     */
    private suspend fun handleQuiz(): Boolean {
        LogManager.i(TAG, "▶ 操作三：回答选择题")
        
        try {
            // 识别题目
            val wordText = captureText(MiniProgramRegions.Selection.WORD_AREA, "quiz-word")
            val word = Regex("[A-Za-z]{2,32}").find(wordText)?.value
            
            if (word == null) {
                LogManager.w(TAG, "未识别到题目单词，随机选择")
                clickRandomOption()
                delay(CLICK_DELAY)
                swipeUp()
                delay(SWIPE_DELAY)
                return true
            }
            
            // 识别选项
            val optionsText = captureText(MiniProgramRegions.Selection.OPTIONS_AREA, "quiz-options")
            val options = extractOptions(optionsText)
            
            if (options.size < 4) {
                LogManager.w(TAG, "选项不足4个: ${options.size}，使用顺序尝试")
                tryOptionsSequentially(word)
            } else {
                // 智能匹配
                val bestIndex = wordMatcher.match(word, options)
                LogManager.i(TAG, "题目: $word")
                LogManager.i(TAG, "选项: ${options.joinToString(" | ")}")
                LogManager.i(TAG, "选择: ${'A' + bestIndex}")
                
                clickOption(bestIndex)
                questionsAnswered++
                onProgress?.invoke("✅ 答题($questionsAnswered): 选项 ${'A' + bestIndex}")
                delay(CLICK_DELAY)
            }
            
            // 上滑到下一题
            swipeUp()
            delay(SWIPE_DELAY)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "回答选择题失败: ${e.message}")
            swipeUp()
            delay(SWIPE_DELAY)
        }
        
        return true // 继续流程
    }
    
    /**
     * 操作四：提示框页面 (t4)
     * 特征：页面中间有"必须要作答哦"提示框
     * 操作：点击"确认"按钮
     */
    private suspend fun handleAnswerPrompt(): Boolean {
        LogManager.i(TAG, "▶ 操作四：点击确认按钮")
        onProgress?.invoke("⚠️ 点击确认...")
        
        // 点击屏幕中心区域（确认按钮通常在中心偏下）
        val centerX = screenCapture.screenWidth / 2
        val centerY = (screenCapture.screenHeight * 0.6).toInt()
        
        tap(centerX, centerY)
        delay(CLICK_DELAY)
        
        return true // 继续流程
    }
    
    /**
     * 操作五：强化完成页面 (t5)
     * 特征：页面有"强化"大字和"继续"按钮
     * 操作：点击"继续"按钮
     */
    private suspend fun handleCompletion(): Boolean {
        LogManager.i(TAG, "▶ 操作五：点击继续按钮")
        onProgress?.invoke("🎉 本轮完成，继续...")
        
        // 点击屏幕下方（继续按钮位置）
        val centerX = screenCapture.screenWidth / 2
        val bottomY = (screenCapture.screenHeight * 0.85).toInt()
        
        tap(centerX, bottomY)
        delay(CLICK_DELAY * 2) // 给更多时间加载
        
        return true // 继续流程（可能还有下一轮）
    }
    
    /**
     * 处理未知状态
     */
    private suspend fun handleUnknown(): Boolean {
        LogManager.w(TAG, "⚠ 未知页面状态，尝试通用操作")
        onProgress?.invoke("❓ 未知页面，尝试继续...")
        
        // 尝试点击中心
        val centerX = screenCapture.screenWidth / 2
        val centerY = screenCapture.screenHeight / 2
        tap(centerX, centerY)
        delay(CLICK_DELAY)
        
        // 如果连续多次未知状态，则停止
        return iterationCount < MAX_ITERATIONS - 5
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 捕获指定区域的文本
     */
    private suspend fun captureText(region: MiniProgramRegions.NormalizedRect, tag: String): String {
        return try {
            screenCapture.captureAndRecognize(region, tag).trim()
        } catch (e: Exception) {
            LogManager.e(TAG, "OCR识别失败[$tag]: ${e.message}")
            ""
        }
    }
    
    /**
     * 从文本中提取释义
     */
    private fun extractDefinition(text: String): String? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        // 优先查找包含词性和中文的行
        for (line in lines) {
            if (line.contains(Regex("[nvadj]{1,4}\\.")) && 
                line.any { it in '\u4e00'..'\u9fff' }) {
                return line
            }
        }
        
        // 回退：返回第一个包含中文的行
        return lines.firstOrNull { line -> 
            line.any { c -> c in '\u4e00'..'\u9fff' } 
        }
    }
    
    /**
     * 从文本中提取四个选项
     * 支持多种格式：单行、多行、混合格式
     */
    private fun extractOptions(text: String): List<String> {
        // 方法1: 使用正则表达式提取 A. B. C. D. 选项
        val optionPattern = Regex("[ABCD]\\.(.*?)(?=[ABCD]\\.|$)")
        val matches = optionPattern.findAll(text)
        val extractedOptions = mutableMapOf<Char, String>()
        
        for (match in matches) {
            val fullText = match.value.trim()
            if (fullText.isNotEmpty()) {
                val label = fullText[0]  // A, B, C, or D
                val content = fullText.substring(2).trim()  // 去掉 "X." 
                if (content.isNotEmpty() && content.any { it in '\u4e00'..'\u9fff' }) {
                    extractedOptions[label] = content
                }
            }
        }
        
        // 按 A B C D 顺序构建选项列表
        val orderedOptions = mutableListOf<String>()
        for (label in listOf('A', 'B', 'C', 'D')) {
            extractedOptions[label]?.let { orderedOptions.add(it) }
        }
        
        if (orderedOptions.size >= 4) {
            LogManager.d(TAG, "正则提取到 ${orderedOptions.size} 个选项")
            return orderedOptions
        }
        
        // 方法2: 回退到按行分割
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val lineOptions = mutableListOf<String>()
        
        for (line in lines) {
            when {
                line.startsWith("A.") && lineOptions.size == 0 -> 
                    lineOptions.add(line.substring(2).trim())
                line.startsWith("B.") && lineOptions.size == 1 -> 
                    lineOptions.add(line.substring(2).trim())
                line.startsWith("C.") && lineOptions.size == 2 -> 
                    lineOptions.add(line.substring(2).trim())
                line.startsWith("D.") && lineOptions.size == 3 -> 
                    lineOptions.add(line.substring(2).trim())
            }
        }
        
        if (lineOptions.size >= 4) {
            LogManager.d(TAG, "按行提取到 ${lineOptions.size} 个选项")
            return lineOptions
        }
        
        // 方法3: 最后的回退 - 提取所有包含中文的文本片段
        val chineseTexts = lines.filter { line ->
            line.any { c -> c in '\u4e00'..'\u9fff' } && line.length >= 3
        }.take(4)
        
        if (chineseTexts.size >= 4) {
            LogManager.d(TAG, "中文文本提取到 ${chineseTexts.size} 个选项")
            return chineseTexts
        }
        
        LogManager.w(TAG, "所有方法都未能提取到足够选项，返回: ${orderedOptions.size + lineOptions.size + chineseTexts.size} 个")
        return orderedOptions.ifEmpty { lineOptions.ifEmpty { chineseTexts } }
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
     * 顺序尝试选项（直到题目变化）
     */
    private suspend fun tryOptionsSequentially(initialWord: String) {
        for (i in 0..3) {
            clickOption(i)
            delay(CLICK_DELAY)
            
            // 检查题目是否变化
            val newWordText = captureText(MiniProgramRegions.Selection.WORD_AREA, "check-word")
            val newWord = Regex("[A-Za-z]{2,32}").find(newWordText)?.value
            
            if (newWord != initialWord && newWord != null) {
                LogManager.i(TAG, "✓ 选项 ${'A' + i} 正确")
                questionsAnswered++
                return
            }
        }
        
        LogManager.w(TAG, "所有选项都未导致题目变化")
    }
}


