package com.autoswapeng.app.logic

import com.autoswapeng.app.accessibility.ScreenCaptureHelper
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.ocr.MiniProgramRegions
import kotlinx.coroutines.delay

/**
 * 单词拼写题处理器
 * 
 * 流程：
 * 1. 首次进入点击确认关闭弹窗
 * 2. 循环25次：
 *    a. 输入错误答案（通过点击键盘）
 *    b. 点击确认（小程序会显示正确答案）
 *    c. OCR识别显示的正确答案
 *    d. 清空输入（点击退格键）
 *    e. 输入正确答案（通过点击键盘）
 *    f. 确认进入下一题
 */
class SpellingHandler(
    private val screenCapture: ScreenCaptureHelper,
    private val tap: suspend (Int, Int) -> Unit,
    private val onProgress: ((String) -> Unit)? = null
) {
    // 通过服务写入文本（由 AppAccessibilityService 提供）
    private val setTextOnInput: ((String) -> Boolean)? get() =
        com.autoswapeng.app.accessibility.AppAccessibilityService.instance?.let { svc ->
            { text -> svc.setTextOnInput(text) }
        }
    
    // 延迟初始化KeyboardTapper（使用实际屏幕尺寸）
    private val keyboardTapper: KeyboardTapper by lazy {
        KeyboardTapper(
            tap = tap,
            screenWidth = screenCapture.screenWidth,
            screenHeight = screenCapture.screenHeight
        )
    }
    
    companion object {
        private const val TAG = "SpellingHandler"
        private const val TOTAL_QUESTIONS = 25
        // 用于触发提示的错误答案（填满可能的最长单词）
        private const val DUMMY_INPUT = "aaaaaaaaaaaaaaaa"  // 16个a，应该够大多数单词
    }
    
    private var isFirstTime = true
    
    // 中断标志
    @Volatile
    private var isCancelled = false
    
    /**
     * 更新进度
     */
    private fun updateProgress(current: Int, total: Int, word: String = "") {
        val message = if (word.isNotEmpty()) {
            "✍️ 拼写 ($current/$total) - $word"
        } else {
            "✍️ 拼写 ($current/$total)"
        }
        onProgress?.invoke(message)
        LogManager.i(TAG, message)
    }
    
    /**
     * 处理拼写题
     */
    suspend fun handleSpelling() {
        try {
            // 重置状态
            isCancelled = false
            
            LogManager.i(TAG, "========== 拼写题流程启动 ==========")
            
            // 等待悬浮窗折叠动画完成
            delay(500)
            LogManager.d(TAG, "等待悬浮窗折叠完成")
            
            // 首次进入，关闭温馨提示弹窗
            if (isFirstTime) {
                updateProgress(0, TOTAL_QUESTIONS)
                closeInitialDialog()
                isFirstTime = false
                delay(800)  // 增加等待时间，确保弹窗关闭
            }
            
            LogManager.i(TAG, "开始处理拼写题，共 $TOTAL_QUESTIONS 题")
            onProgress?.invoke("✍️ 开始拼写 (0/$TOTAL_QUESTIONS)")
            
            for (questionNum in 1..TOTAL_QUESTIONS) {
                // 检查是否被中断
                if (isCancelled) {
                    LogManager.w(TAG, "拼写流程被用户中断")
                    onProgress?.invoke("⏹️ 已中断")
                    return
                }
                
                try {
                    updateProgress(questionNum, TOTAL_QUESTIONS)
                    handleSingleQuestion(questionNum)
                    delay(800)  // 等待页面切换
                } catch (e: Exception) {
                    LogManager.e(TAG, "处理第 $questionNum 题失败: ${e.message}")
                    e.printStackTrace()
                    // 继续下一题
                }
            }
            
            onProgress?.invoke("✅ 拼写完成！")
            LogManager.i(TAG, "拼写题处理完成！")
            
        } catch (e: Exception) {
            onProgress?.invoke("❌ 拼写失败")
            LogManager.e(TAG, "拼写题处理失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 关闭初始弹窗
     */
    private suspend fun closeInitialDialog() {
        LogManager.i(TAG, "关闭温馨提示弹窗")
        LogManager.d(TAG, "屏幕尺寸: ${screenCapture.screenWidth}x${screenCapture.screenHeight}")
        
        val (x, y) = MiniProgramRegions.Spelling.INITIAL_DIALOG_CONFIRM.toPixelPoint(
            screenCapture.screenWidth,
            screenCapture.screenHeight
        )
        LogManager.d(TAG, "弹窗确认按钮坐标: ($x, $y)")
        tap(x, y)
    }
    
    /**
     * 处理单个拼写题
     */
    private suspend fun handleSingleQuestion(questionNum: Int) {
        LogManager.i(TAG, "处理第 $questionNum 题")
        
        // 步骤1：先尝试直接写入错误答案，失败再坐标敲击
        LogManager.d(TAG, "通过点击键盘输入错误答案: $DUMMY_INPUT")
        // 点击输入框以确保焦点
        run {
            val rect = MiniProgramRegions.Spelling.INPUT_AREA.toPixelRect(
                screenCapture.screenWidth,
                screenCapture.screenHeight
            )
            val fx = (rect.left + rect.right) / 2
            val fy = (rect.top + rect.bottom) / 2
            LogManager.d(TAG, "点击输入框以确保焦点: ($fx, $fy)")
            tap(fx, fy)
        }
        val wroteDummy = setTextOnInput?.invoke(DUMMY_INPUT) ?: false
        if (!wroteDummy) {
            keyboardTapper.typeText(DUMMY_INPUT)
        }
        delay(500)  // 等待输入动画完成
        
        // 点击键盘上的确认键
        LogManager.d(TAG, "点击键盘确认键")
        keyboardTapper.clickEnter()
        LogManager.d(TAG, "已点击确认，等待小程序显示正确答案...")
        delay(2500)  // 等待小程序显示正确答案
        
        // 步骤2：OCR识别提示中的正确答案
        LogManager.d(TAG, "开始OCR识别提示区域（等待画面稳定）")
        delay(500)  // 额外等待，确保画面完全稳定
        
        // 尝试多个区域查找提示（优先扫描最可能的区域）
        val regions = listOf(
            "区域1-提示区域（输入框下方）" to MiniProgramRegions.Spelling.HINT,
            "区域2-扩大区域（含输入框+提示）" to MiniProgramRegions.Spelling.HINT_LARGE,
            "区域3-屏幕中部大范围" to MiniProgramRegions.NormalizedRect(0.1f, 0.35f, 0.9f, 0.6f),
            "区域4-输入框到播放按钮" to MiniProgramRegions.NormalizedRect(0.1f, 0.36f, 0.9f, 0.56f)
        )
        
        var hintText = ""
        var correctWord = ""
        var foundRegion = ""
        
        for ((regionName, region) in regions) {
            hintText = screenCapture.captureAndRecognize(region, regionName)
            // OCR结果已在ScreenCaptureHelper中记录
            
            if (hintText.isNotEmpty()) {
                correctWord = extractWordFromHint(hintText)
                if (correctWord.isNotEmpty() && isValidEnglishWord(correctWord)) {
                    foundRegion = regionName
                    LogManager.i(TAG, "✓ 在 $regionName 找到答案: $correctWord")
                    break
                }
            }
            
            delay(200)  // 每次尝试间隔200ms
        }
        
        if (correctWord.isEmpty() || !isValidEnglishWord(correctWord)) {
            LogManager.w(TAG, "⚠️ 所有区域都无法识别正确答案")
            LogManager.w(TAG, "   请检查调试截图，手动确认提示位置")
            keyboardTapper.clickEnter()  // 尝试进入下一题
            return
        }
        
        LogManager.i(TAG, "✓ 识别到正确答案: $correctWord")
        
        // 步骤3：清空输入（优先直接覆盖为空，失败则少量退格）
        LogManager.d(TAG, "清空输入：优先直接覆盖为空，失败则退格5次")
        val cleared = setTextOnInput?.invoke("") ?: false
        if (!cleared) {
            // 只需清空错误答案（16个a），退格5次足够（错误答案会被小程序自动清除大部分）
            keyboardTapper.clearInput(times = 5)
        }
        delay(500)  // 等待清空完成
        
        // 再次点击输入框以确保焦点（某些设备清空后焦点会短暂丢失）
        run {
            val rect = MiniProgramRegions.Spelling.INPUT_AREA.toPixelRect(
                screenCapture.screenWidth,
                screenCapture.screenHeight
            )
            val fx = (rect.left + rect.right) / 2
            val fy = (rect.top + rect.bottom) / 2
            LogManager.d(TAG, "再次点击输入框以确保焦点: ($fx, $fy)")
            tap(fx, fy)
            delay(150)
        }

        // 步骤4：输入正确答案（优先直接写入，失败再使用退格预热策略）
        LogManager.d(TAG, "通过点击键盘输入正确答案: $correctWord")
        val wrote = setTextOnInput?.invoke(correctWord) ?: false
        if (!wrote) {
            // 退格预热策略：少量退格让输入法稳定，避免与新输入冲突
            LogManager.d(TAG, "退格预热策略：退格3次 + 长延迟")
            keyboardTapper.clearInput(times = 3)
            kotlinx.coroutines.delay(800)  // 长延迟确保输入法完全稳定
            
            // 现在输入法已完全稳定，一次性整词输入
            keyboardTapper.typeText(
                text = correctWord,
                dedupeFirstChar = false,
                initialDelayMs = 300  // 额外初始延迟
            )
        }
        delay(600)  // 等待输入完成
        
        // 步骤5：点击键盘确认键，进入下一题
        LogManager.d(TAG, "点击键盘确认键")
        keyboardTapper.clickEnter()
        
        LogManager.i(TAG, "✓ 第 $questionNum 题完成")
    }
    
    /**
     * 从提示文本中提取单词
     * 例如："a a a 提示 photographic a a" -> "photographic"
     */
    private fun extractWordFromHint(hintText: String): String {
        LogManager.d(TAG, "原始OCR结果: '$hintText'")
        
        // 方法1：查找"提示"关键字，提取后面的单词
        val hintIndex = hintText.indexOf("提示")
        if (hintIndex >= 0) {
            // 找到"提示"后面的内容
            val afterHint = hintText.substring(hintIndex + 2).trim()
            LogManager.d(TAG, "提示之后的内容: '$afterHint'")
            
            // 提取连续的英文字母（忽略空格和单个a）
            val words = afterHint.split(Regex("\\s+"))
                .map { it.filter { c -> c in 'a'..'z' || c in 'A'..'Z' } }
                .filter { it.length >= 3 }  // 至少3个字母
                .filter { !it.all { c -> c == 'a' || c == 'A' } }  // 排除全是a的
            
            if (words.isNotEmpty()) {
                val word = words.first().lowercase()
                LogManager.d(TAG, "提取单词（方法1）: '$word'")
                return word
            }
        }
        
        // 方法2：如果没有"提示"，查找最长的非a单词
        val words = hintText.split(Regex("\\s+"))
            .map { it.filter { c -> c in 'a'..'z' || c in 'A'..'Z' } }
            .filter { it.length >= 3 }
            .filter { !it.all { c -> c == 'a' || c == 'A' } }
            .sortedByDescending { it.length }  // 按长度排序
        
        if (words.isNotEmpty()) {
            val word = words.first().lowercase()
            LogManager.d(TAG, "提取单词（方法2-最长）: '$word'")
            return word
        }
        
        // 方法3：提取所有连续的英文字母（最后的备用方案）
        val allLetters = hintText.filter { it in 'a'..'z' || it in 'A'..'Z' }.lowercase()
        LogManager.d(TAG, "提取单词（方法3-全部字母）: '$allLetters'")
        return allLetters
    }
    
    /**
     * 验证是否是有效的英文单词
     */
    private fun isValidEnglishWord(word: String): Boolean {
        // 必须全是英文字母
        if (!word.all { it in 'a'..'z' || it in 'A'..'Z' }) {
            LogManager.d(TAG, "验证失败: 包含非英文字符 '$word'")
            return false
        }
        
        // 长度必须在3-20之间（合理的单词长度）
        // photographic有12个字母，所以上限20足够
        if (word.length !in 3..20) {
            LogManager.d(TAG, "验证失败: 长度不合理 '$word' (${word.length})")
            return false
        }
        
        // 不能全是相同字母（排除 "aaaaaa"）
        if (word.all { it == word[0] }) {
            LogManager.d(TAG, "验证失败: 全是相同字母 '$word'")
            return false
        }
        
        LogManager.d(TAG, "验证通过: '$word'")
        return true
    }
    
    /**
     * 中断答题流程
     */
    fun cancel() {
        isCancelled = true
        LogManager.i(TAG, "收到中断指令")
    }
    
    /**
     * 检查是否正在运行
     */
    fun isRunning(): Boolean {
        return !isCancelled
    }
    
    /**
     * 重置状态（用于新任务）
     */
    fun reset() {
        isFirstTime = true
        isCancelled = false
        LogManager.d(TAG, "状态已重置")
    }
}

