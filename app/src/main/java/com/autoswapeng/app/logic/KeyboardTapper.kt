package com.autoswapeng.app.logic

import com.autoswapeng.app.log.LogManager
import kotlinx.coroutines.delay

/**
 * 键盘点击器
 * 通过点击屏幕上的虚拟键盘来输入文本
 */
class KeyboardTapper(
    private val tap: (Int, Int) -> Unit,
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
            'p' to Pair(0.945f, 0.755f),
            
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
    suspend fun typeText(text: String, dedupeFirstChar: Boolean = false) {
        val lowerText = text.lowercase()
        LogManager.i(TAG, "开始输入文本: $text")
        
        // 开始输入前稍微等待（确保键盘动画/焦点稳定）。常驻键盘场景稍短
        delay(140)
        
        for ((index, char) in lowerText.withIndex()) {
            var coord = KEYBOARD_LAYOUT[char]
            if (coord != null) {
                // 首键坐标微调：p/l 等右侧键向左收敛，降低边界抖动
                if (index == 0) {
                    coord = when (char) {
                        'p' -> Pair(0.930f, coord.second) // 原约0.945，向左收敛
                        'l' -> Pair(0.925f, coord.second) // 原约0.939
                        else -> coord
                    }
                }
                val x = (coord.first * screenWidth).toInt()
                val y = (coord.second * screenHeight).toInt()
                
                LogManager.d(TAG, "[$index] 点击键盘 '$char' at ($x, $y)")
                if (index == 0 && dedupeFirstChar) {
                    // 无退格首键稳定：轻点 -> 稳定等待(280ms)（给内置键盘完成合成）
                    tap(x, y)
                    delay(280)
                 } else {
                    tap(x, y)
                     // 为第一个按键设置更长的延迟，避免因系统预测/抬起去抖导致的重复
                    if (index == 0) delay(150) else delay(90)
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
            delay(80)  // 增加间隔，确保每次退格都生效
        }
        
        LogManager.d(TAG, "✓ 输入已清空（已点击退格${times}次）")
    }
    
    /**
     * 点击确认键
     */
    fun clickEnter() {
        val x = (ENTER.first * screenWidth).toInt()
        val y = (ENTER.second * screenHeight).toInt()
        
        LogManager.d(TAG, "点击确认键 at ($x, $y)")
        tap(x, y)
    }
}

