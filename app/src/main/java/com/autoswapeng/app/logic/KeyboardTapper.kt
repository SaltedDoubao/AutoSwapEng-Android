package com.autoswapeng.app.logic

import com.autoswapeng.app.log.LogManager
import kotlinx.coroutines.delay

/**
 * 键盘点击器
 * 通过点击屏幕上的虚拟键盘来输入文本
 */
class KeyboardTapper(
    private val tap: suspend (Int, Int) -> Unit,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    companion object {
        private const val TAG = "KeyboardTapper"
        
        // QWERTY 键盘布局（归一化坐标）
        // 基于标准Android键盘布局
        private val KEYBOARD_LAYOUT = mapOf(
            // 第一行
            'q' to Pair(0.055f, 0.755f),
            'w' to Pair(0.159f, 0.755f),
            'e' to Pair(0.263f, 0.755f),
            'r' to Pair(0.367f, 0.755f),
            't' to Pair(0.471f, 0.755f),
            'y' to Pair(0.575f, 0.755f),
            'u' to Pair(0.679f, 0.755f),
            'i' to Pair(0.783f, 0.755f),
            'o' to Pair(0.887f, 0.755f),
            'p' to Pair(0.920f, 0.755f),  // 向左收敛，避免边界误触
            
            // 第二行
            'a' to Pair(0.107f, 0.822f),
            's' to Pair(0.211f, 0.822f),
            'd' to Pair(0.315f, 0.822f),
            'f' to Pair(0.419f, 0.822f),
            'g' to Pair(0.523f, 0.822f),
            'h' to Pair(0.627f, 0.822f),
            'j' to Pair(0.731f, 0.822f),
            'k' to Pair(0.835f, 0.822f),
            'l' to Pair(0.939f, 0.822f),
            
            // 第三行
            'z' to Pair(0.211f, 0.889f),
            'x' to Pair(0.315f, 0.889f),
            'c' to Pair(0.419f, 0.889f),
            'v' to Pair(0.523f, 0.889f),
            'b' to Pair(0.627f, 0.889f),
            'n' to Pair(0.731f, 0.889f),
            'm' to Pair(0.835f, 0.889f)
        )
        
        // 删除键（退格键）
        private val BACKSPACE = Pair(0.945f, 0.889f)
        
        // 确认键（键盘右下角）
        private val ENTER = Pair(0.875f, 0.956f)
    }
    
    /**
     * 输入文本（通过点击键盘）
     */
    suspend fun typeText(
        text: String,
        dedupeFirstChar: Boolean = false,
        initialDelayMs: Long = 140
    ) {
        val lowerText = text.lowercase()
        LogManager.i(TAG, "开始输入文本: $text (使用挂起式点击)")
        
        // 开始输入前稍微等待（确保键盘动画/焦点稳定）
        delay(initialDelayMs)
        
        for ((index, char) in lowerText.withIndex()) {
            var coord = KEYBOARD_LAYOUT[char]
            if (coord != null) {
                val x = (coord.first * screenWidth).toInt()
                val y = (coord.second * screenHeight).toInt()
                
                LogManager.d(TAG, "[$index] 挂起式点击键盘 '$char' at ($x, $y)")
                
                // 使用挂起式点击，等待手势完成
                // 注意：tap函数内部已有Mutex锁和150ms防抖延迟，手势duration为30ms
                tap(x, y)
                
                // 根据 Android Accessibility 最佳实践：
                // 手势短促（30ms），间隔适中（150-200ms）
                // tap函数已有150ms防抖，这里额外延迟确保输入法稳定
                if (index == 0) {
                    // 首键需要更长延迟，让输入法完全初始化
                    LogManager.d(TAG, "首键延迟 500ms（输入法初始化）")
                    delay(500)
                } else if (index == 1) {
                    // 第二键也容易重复，适当延长
                    LogManager.d(TAG, "第二键延迟 300ms")
                    delay(300)
                } else {
                    // 后续键使用文档建议的标准间隔
                    delay(200)
                }
            } else {
                LogManager.w(TAG, "字符 '$char' 不在键盘映射中")
            }
        }
        
        LogManager.i(TAG, "✓ 文本输入完成")
    }
    
    /**
     * 清空输入（点击退格键多次）
     */
    suspend fun clearInput(times: Int = 20) {
        LogManager.d(TAG, "清空输入（点击退格${times}次）")
        val x = (BACKSPACE.first * screenWidth).toInt()
        val y = (BACKSPACE.second * screenHeight).toInt()
        
        LogManager.d(TAG, "退格键坐标: ($x, $y)")
        
        repeat(times) {
            tap(x, y)
            // tap函数已有150ms防抖，这里再延迟80ms，总计约230ms确保退格生效
            delay(80)
        }
        
        LogManager.d(TAG, "✓ 输入已清空（已点击退格${times}次）")
    }
    
    /**
     * 点击确认键
     */
    suspend fun clickEnter() {
        val x = (ENTER.first * screenWidth).toInt()
        val y = (ENTER.second * screenHeight).toInt()
        
        LogManager.d(TAG, "点击确认键 at ($x, $y)")
        tap(x, y)
    }
}

