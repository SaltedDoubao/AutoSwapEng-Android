package com.autoswapeng.app.logic

import com.autoswapeng.app.accessibility.NodeText

/**
 * 题型检测器
 * 识别不同类型的题目：单词选择、单词拼写、听力等
 */
class QuestionTypeDetector {
    
    /**
     * 题型枚举
     */
    enum class QuestionType {
        WORD_SELECTION,    // 单词选择题（看单词选释义/看释义选单词）
        WORD_SPELLING,     // 单词拼写题
        LISTENING,         // 听力题
        LEARNING,          // 学习模式（展示单词和释义）
        COMPLETION,        // 完成提示
        UNKNOWN            // 未知类型
    }
    
    data class DetectionResult(
        val type: QuestionType,
        val word: String? = null,              // 题目单词
        val definition: String? = null,        // 单词释义
        val options: List<String> = emptyList(), // 选项列表
        val confidence: Float = 0f             // 置信度 0.0-1.0
    )
    
    /**
     * 检测题型
     */
    fun detectQuestionType(texts: List<NodeText>): DetectionResult {
        // 检查是否完成
        if (isCompletionPage(texts)) {
            return DetectionResult(QuestionType.COMPLETION, confidence = 1.0f)
        }
        
        // 提取关键信息
        val hasEnglishWord = texts.any { it.text.matches(Regex("^[A-Za-z]+$")) && it.text.length in 2..32 }
        val hasChineseDefinition = texts.any { containsChineseDefinition(it.text) }
        val hasOptions = texts.count { containsChineseDefinition(it.text) } >= 4
        val hasSpellingInput = texts.any { it.text.contains("拼写") || it.text.contains("输入") }
        val hasListenButton = texts.any { 
            it.text.contains("听") || it.text.contains("播放") || it.text.contains("🔊")
        }
        
        // 判断题型
        return when {
            // 单词拼写题
            hasSpellingInput && hasChineseDefinition -> {
                val definition = texts.firstOrNull { containsChineseDefinition(it.text) }?.text
                DetectionResult(
                    type = QuestionType.WORD_SPELLING,
                    definition = definition,
                    confidence = 0.9f
                )
            }
            
            // 听力题
            hasListenButton && hasOptions -> {
                val options = texts.filter { containsChineseDefinition(it.text) }
                    .take(4)
                    .map { it.text }
                DetectionResult(
                    type = QuestionType.LISTENING,
                    options = options,
                    confidence = 0.85f
                )
            }
            
            // 单词选择题（看单词选释义）
            hasEnglishWord && hasOptions -> {
                val word = texts.firstOrNull { 
                    it.text.matches(Regex("^[A-Za-z]+$")) && it.text.length in 2..32 
                }?.text
                val options = texts.filter { containsChineseDefinition(it.text) }
                    .take(4)
                    .map { it.text }
                DetectionResult(
                    type = QuestionType.WORD_SELECTION,
                    word = word,
                    options = options,
                    confidence = 0.95f
                )
            }
            
            // 学习模式（单词+释义展示）
            hasEnglishWord && hasChineseDefinition && !hasOptions -> {
                val word = texts.firstOrNull { 
                    it.text.matches(Regex("^[A-Za-z]+$")) && it.text.length in 2..32 
                }?.text
                val definition = texts.firstOrNull { containsChineseDefinition(it.text) }?.text
                DetectionResult(
                    type = QuestionType.LEARNING,
                    word = word,
                    definition = definition,
                    confidence = 0.9f
                )
            }
            
            else -> DetectionResult(QuestionType.UNKNOWN, confidence = 0f)
        }
    }
    
    /**
     * 检查是否是完成页面
     */
    private fun isCompletionPage(texts: List<NodeText>): Boolean {
        val completionKeywords = listOf(
            "恭喜你完成",
            "任务已完成",
            "学习完成",
            "全部完成",
            "打卡成功"
        )
        return texts.any { text ->
            completionKeywords.any { keyword -> text.text.contains(keyword) }
        }
    }
    
    /**
     * 检查文本是否包含中文释义
     * 通常包含词性标记和中文
     */
    private fun containsChineseDefinition(text: String): Boolean {
        // 包含词性标记
        val hasPartOfSpeech = text.contains(Regex("[nvadj]{1,3}\\."))
        // 包含中文
        val hasChinese = text.any { it in '\u4e00'..'\u9fff' }
        // 不能太短
        val hasProperLength = text.length >= 3
        
        return hasPartOfSpeech && hasChinese && hasProperLength
    }
    
    /**
     * 检测是否是翻转外语小程序页面
     */
    fun isFlipEnglishMiniProgram(texts: List<NodeText>): Boolean {
        val indicators = listOf(
            "翻转外语",
            "翻转记忆",
            "今日任务",
            "学习计划"
        )
        return texts.any { text ->
            indicators.any { indicator -> text.text.contains(indicator) }
        }
    }
}

