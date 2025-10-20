package com.autoswapeng.app.logic

import com.autoswapeng.app.accessibility.ScreenCaptureHelper
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.ocr.MiniProgramRegions
import kotlinx.coroutines.delay

/**
 * 选择题处理器V2 - 智能匹配版本
 * 
 * 核心流程：
 * 1. 学习阶段：积累单词-释义映射
 * 2. 选择阶段：OCR识别 -> 匹配打分 -> 验证点击
 */
class SelectionHandlerV2(
    private val screenCapture: ScreenCaptureHelper,
    private val tap: suspend (Int, Int) -> Unit,
    private val onProgress: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "SelectionHandlerV2"
        private const val MIN_CONFIDENCE = 0.75f  // 最低置信度阈值
        private const val OCR_RETRY_COUNT = 3     // OCR重试次数
        private const val CLICK_VERIFY_DELAY = 1000L  // 点击后验证延迟
    }
    
    private val wordMatcher = WordMatcher()
    private val detector = QuestionTypeDetector()
    
    // 状态跟踪
    private var lastProcessedWord: String = ""
    private var consecutiveErrors = 0
    
    /**
     * 处理单个页面（学习或选择）
     */
    suspend fun processPage(): Boolean {
        try {
            // 1. 多次OCR确保准确识别
            val pageTexts = capturePageWithRetry()
            if (pageTexts.isEmpty()) {
                LogManager.w(TAG, "OCR未能识别到有效内容")
                return false
            }
            
            // 2. 识别题型
            val detection = detector.detectQuestionType(pageTexts)
            LogManager.i(TAG, "识别题型: ${detection.type}, 置信度: ${detection.confidence}")
            
            return when (detection.type) {
                QuestionTypeDetector.QuestionType.LEARNING -> {
                    handleLearning(detection)
                }
                QuestionTypeDetector.QuestionType.WORD_SELECTION -> {
                    handleSelection(detection, pageTexts)
                }
                else -> {
                    LogManager.w(TAG, "未知题型，尝试通用点击")
                    tapNextDefault()
                    false
                }
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "处理页面失败: ${e.message}")
            consecutiveErrors++
            if (consecutiveErrors > 3) {
                LogManager.e(TAG, "连续错误过多，停止处理")
                return false
            }
            return false
        }
    }
    
    /**
     * 处理学习页面 - 积累记忆
     */
    private suspend fun handleLearning(detection: QuestionTypeDetector.DetectionResult): Boolean {
        val word = detection.word ?: return false
        val definition = detection.definition ?: return false
        
        // 验证数据有效性
        if (!isValidWord(word) || !isValidDefinition(definition)) {
            LogManager.w(TAG, "学习数据无效: word='$word', def='$definition'")
            return false
        }
        
        // 存储到记忆库
        wordMatcher.learn(word, listOf(definition))
        LogManager.i(TAG, "✓ 学习: $word -> $definition")
        onProgress?.invoke("📚 已学习 ${wordMatcher.getLearnedCount()} 个单词")
        
        // 点击进入下一题
        return tapNextWithVerification()
    }
    
    /**
     * 处理选择题页面 - 智能匹配
     */
    private suspend fun handleSelection(
        detection: QuestionTypeDetector.DetectionResult,
        pageTexts: List<com.autoswapeng.app.accessibility.NodeText>
    ): Boolean {
        // 1. 提取题目和选项
        val question = extractQuestion(detection, pageTexts)
        val options = extractOptions(detection, pageTexts)
        
        if (question == null || options.size < 4) {
            LogManager.w(TAG, "选择题数据不完整: question=$question, options=${options.size}")
            return fallbackSequentialClick()
        }
        
        // 2. 防止重复处理同一题
        if (question == lastProcessedWord) {
            LogManager.w(TAG, "检测到重复题目，可能上次点击未生效")
            return fallbackSequentialClick()
        }
        lastProcessedWord = question
        
        // 3. 智能匹配
        val matchResult = performMatching(question, options, detection)
        
        // 4. 根据置信度决策
        return if (matchResult.confidence >= MIN_CONFIDENCE) {
            // 高置信度：直接点击最佳选项
            clickOptionWithVerification(matchResult.bestIndex, matchResult.confidence)
        } else {
            // 低置信度：回退到顺序尝试
            LogManager.w(TAG, "置信度过低(${matchResult.confidence})，使用顺序尝试")
            fallbackSequentialClick()
        }
    }
    
    /**
     * 多次OCR捕获并验证一致性
     */
    private suspend fun capturePageWithRetry(): List<com.autoswapeng.app.accessibility.NodeText> {
        val results = mutableListOf<List<String>>()
        
        repeat(OCR_RETRY_COUNT) { attempt ->
            delay(if (attempt > 0) 200L else 0L)  // 首次不延迟
            
            try {
                // OCR整个内容区域
                val texts = mutableListOf<String>()
                
                // 题目区域
                val wordText = screenCapture.captureAndRecognize(
                    MiniProgramRegions.Selection.WORD_AREA, 
                    "word-$attempt"
                ).trim()
                if (wordText.isNotEmpty()) texts.add(wordText)
                
                // 选项区域（使用像素坐标版本，返回List<String>）
                val optionsRect = MiniProgramRegions.Selection.OPTIONS_AREA
                    .toPixelRect(screenCapture.screenWidth, screenCapture.screenHeight)
                val optionsText = screenCapture.captureAndRecognize(optionsRect)
                texts.addAll(optionsText)
                
                results.add(texts)
            } catch (e: Exception) {
                LogManager.e(TAG, "OCR尝试 $attempt 失败: ${e.message}")
            }
        }
        
        // 选择出现最多的结果（投票机制）
        val mostCommon = findMostCommonResult(results)
        return mostCommon.map { text ->
            com.autoswapeng.app.accessibility.NodeText(
                text = text,
                bounds = android.graphics.Rect()  // 简化处理
            )
        }
    }
    
    /**
     * 提取题目（单词或释义）
     */
    private fun extractQuestion(
        detection: QuestionTypeDetector.DetectionResult,
        texts: List<com.autoswapeng.app.accessibility.NodeText>
    ): String? {
        // 优先使用检测结果
        detection.word?.let { return it }
        detection.definition?.let { return it }
        
        // 回退：从文本中提取
        val englishRegex = Regex("^[A-Za-z]{2,32}$")
        return texts.firstOrNull { it.text.matches(englishRegex) }?.text
    }
    
    /**
     * 提取四个选项
     */
    private fun extractOptions(
        detection: QuestionTypeDetector.DetectionResult,
        texts: List<com.autoswapeng.app.accessibility.NodeText>
    ): List<String> {
        // 优先使用检测结果
        if (detection.options.size >= 4) {
            return detection.options.take(4)
        }
        
        // 回退：从文本中提取（包含中文的行）
        return texts
            .filter { it.text.any { c -> c in '\u4e00'..'\u9fff' } }
            .map { it.text }
            .take(4)
    }
    
    /**
     * 执行匹配算法
     */
    private fun performMatching(
        question: String,
        options: List<String>,
        detection: QuestionTypeDetector.DetectionResult
    ): MatchResult {
        return when {
            // 情况1：英文单词选中文释义
            isEnglishWord(question) -> {
                val bestIdx = wordMatcher.match(question, options)
                val confidence = calculateConfidence(question, options[bestIdx])
                MatchResult(bestIdx, confidence)
            }
            
            // 情况2：中文释义选英文单词
            isChinseDefinition(question) -> {
                val bestIdx = wordMatcher.matchByDefinition(question, options)
                val confidence = calculateConfidence(question, options[bestIdx])
                MatchResult(bestIdx, confidence)
            }
            
            else -> {
                LogManager.w(TAG, "无法确定题目类型: $question")
                MatchResult(0, 0.0f)
            }
        }
    }
    
    /**
     * 点击选项并验证结果
     */
    private suspend fun clickOptionWithVerification(
        optionIndex: Int,
        confidence: Float
    ): Boolean {
        LogManager.i(TAG, "点击选项 ${'A' + optionIndex} (置信度: $confidence)")
        
        // 1. 记录点击前状态
        val beforeWord = captureCurrentWord()
        
        // 2. 执行点击
        val clickPoint = when (optionIndex) {
            0 -> MiniProgramRegions.Selection.OPTION_A
            1 -> MiniProgramRegions.Selection.OPTION_B
            2 -> MiniProgramRegions.Selection.OPTION_C
            3 -> MiniProgramRegions.Selection.OPTION_D
            else -> MiniProgramRegions.Selection.OPTION_A
        }.toPixelPoint(screenCapture.screenWidth, screenCapture.screenHeight)
        
        tap(clickPoint.first, clickPoint.second)
        
        // 3. 等待并验证
        delay(CLICK_VERIFY_DELAY)
        val afterWord = captureCurrentWord()
        
        // 4. 判断是否成功
        val success = beforeWord != afterWord && afterWord.isNotEmpty()
        if (success) {
            LogManager.i(TAG, "✓ 答题成功: $beforeWord -> $afterWord")
            consecutiveErrors = 0
            onProgress?.invoke("✅ 正确 (置信度: ${(confidence * 100).toInt()}%)")
        } else {
            LogManager.w(TAG, "✗ 题目未变化，可能答错")
            consecutiveErrors++
            onProgress?.invoke("❌ 可能错误")
        }
        
        return success
    }
    
    /**
     * 回退策略：顺序点击
     */
    private suspend fun fallbackSequentialClick(): Boolean {
        LogManager.i(TAG, "使用顺序点击策略")
        
        val initialWord = captureCurrentWord()
        val clicks = listOf(
            MiniProgramRegions.Selection.OPTION_A,
            MiniProgramRegions.Selection.OPTION_B,
            MiniProgramRegions.Selection.OPTION_C,
            MiniProgramRegions.Selection.OPTION_D
        )
        
        for ((idx, point) in clicks.withIndex()) {
            val (x, y) = point.toPixelPoint(screenCapture.screenWidth, screenCapture.screenHeight)
            LogManager.d(TAG, "尝试选项 ${'A' + idx}")
            
            tap(x, y)
            delay(800)
            
            val newWord = captureCurrentWord()
            if (newWord != initialWord && newWord.isNotEmpty()) {
                LogManager.i(TAG, "✓ 顺序点击成功: 选项 ${'A' + idx}")
                return true
            }
        }
        
        // 全部失败，点击默认位置
        return tapNextDefault()
    }
    
    /**
     * 点击下一题并验证
     */
    private suspend fun tapNextWithVerification(): Boolean {
        val beforeWord = captureCurrentWord()
        
        val (x, y) = MiniProgramRegions.Selection.NEXT_TAP
            .toPixelPoint(screenCapture.screenWidth, screenCapture.screenHeight)
        tap(x, y)
        
        delay(800)
        val afterWord = captureCurrentWord()
        
        return beforeWord != afterWord
    }
    
    /**
     * 默认点击下一题
     */
    private suspend fun tapNextDefault(): Boolean {
        val (x, y) = MiniProgramRegions.Selection.NEXT_TAP
            .toPixelPoint(screenCapture.screenWidth, screenCapture.screenHeight)
        tap(x, y)
        delay(800)
        return true
    }
    
    /**
     * 捕获当前题目单词
     */
    private suspend fun captureCurrentWord(): String {
        return try {
            val raw = screenCapture.captureAndRecognize(
                MiniProgramRegions.Selection.WORD_AREA,
                "current-word"
            ).trim()
            Regex("[A-Za-z]{2,32}").find(raw)?.value ?: raw
        } catch (e: Exception) {
            ""
        }
    }
    
    // ========== 工具函数 ==========
    
    private fun isValidWord(word: String): Boolean {
        return word.length in 2..32 && word.matches(Regex("^[A-Za-z]+$"))
    }
    
    private fun isValidDefinition(def: String): Boolean {
        return def.length >= 3 && def.any { it in '\u4e00'..'\u9fff' }
    }
    
    private fun isEnglishWord(text: String): Boolean {
        return text.matches(Regex("^[A-Za-z]{2,32}$"))
    }
    
    private fun isChinseDefinition(text: String): Boolean {
        return text.any { it in '\u4e00'..'\u9fff' }
    }
    
    private fun calculateConfidence(question: String, answer: String): Float {
        // 简化的置信度计算
        return when {
            wordMatcher.getLearnedCount() == 0 -> 0.0f
            question.length < 2 || answer.isEmpty() -> 0.0f
            else -> 0.8f  // 基础置信度，实际应根据匹配分数计算
        }
    }
    
    private fun findMostCommonResult(results: List<List<String>>): List<String> {
        if (results.isEmpty()) return emptyList()
        if (results.size == 1) return results[0]
        
        // 简化：返回最长的结果（通常OCR更完整）
        return results.maxByOrNull { it.size } ?: results[0]
    }
    
    data class MatchResult(
        val bestIndex: Int,
        val confidence: Float
    )
}
