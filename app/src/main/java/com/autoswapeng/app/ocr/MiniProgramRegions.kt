package com.autoswapeng.app.ocr

import android.graphics.Rect

/**
 * 微信小程序"翻转外语"的UI区域定义
 * 使用归一化坐标（0.0-1.0），适配不同屏幕尺寸
 */
object MiniProgramRegions {
    
    /**
     * 单词拼写题型区域
     * 坐标基于1080x2400屏幕，已归一化
     */
    object Spelling {
        // 单词释义区域（上方的中文释义）
        // 例如："n.电话；电话系统 v.打电话"
        val DEFINITION = NormalizedRect(
            left = 0.10f,
            top = 0.27f,
            right = 0.90f,
            bottom = 0.32f
        )
        
        // 提示区域（显示正确答案）
        // 例如："提示 photographic"
        // 根据UI截图，这个区域在输入框下方、播放按钮上方
        val HINT = NormalizedRect(
            left = 0.10f,
            top = 0.46f,
            right = 0.90f,
            bottom = 0.52f
        )
        
        // 提示区域（扩大版，包含输入框和提示）
        val HINT_LARGE = NormalizedRect(
            left = 0.10f,
            top = 0.36f,
            right = 0.90f,
            bottom = 0.54f
        )
        
        // 输入框区域（5个字母框）
        val INPUT_AREA = NormalizedRect(
            left = 0.15f,
            top = 0.36f,
            right = 0.85f,
            bottom = 0.40f
        )
        
        // 初始弹窗"确认"按钮
        val INITIAL_DIALOG_CONFIRM = NormalizedPoint(0.5f, 0.62f)
        
        // 键盘"确认"按钮
        val KEYBOARD_CONFIRM = NormalizedPoint(0.85f, 0.96f)
        
        // 播放发音按钮
        val PLAY_AUDIO = NormalizedPoint(0.5f, 0.49f)
    }
    
    /**
     * 单词选择题型区域（待实现）
     */
    object Selection {
        // 单词区域（题目英文单词）
        val WORD_AREA = NormalizedRect(
            left = 0.1698f,
            top = 0.3056f,
            right = 0.9150f,
            bottom = 0.4053f
        )

        // 中文选项区域（四个选项整体包围框）
        val OPTIONS_AREA = NormalizedRect(
            left = 0.0883f,
            top = 0.4865f,
            right = 0.9150f,
            bottom = 0.8316f
        )

        // 四个选项的点击位置（A-D）
        val OPTION_A = NormalizedPoint(0.5060f, 0.5284f)
        val OPTION_B = NormalizedPoint(0.5060f, 0.5938f)
        val OPTION_C = NormalizedPoint(0.5060f, 0.6633f)
        val OPTION_D = NormalizedPoint(0.5060f, 0.7353f)

        // 进入下一题的通用点击位置（保底）
        val NEXT_TAP = NormalizedPoint(0.5060f, 0.3006f)
    }
    
    /**
     * 听力题型区域（待实现）
     */
    object Listening {
        // TODO: 等待UI截图后实现
    }
    
    /**
     * 完成页面按钮
     */
    object Completion {
        // "完成"按钮位置
        val FINISH_BUTTON = NormalizedPoint(0.5f, 0.85f)
    }
    
    /**
     * 归一化矩形区域（0.0-1.0）
     */
    data class NormalizedRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        /**
         * 转换为像素坐标
         */
        fun toPixelRect(screenWidth: Int, screenHeight: Int): Rect {
            return Rect(
                (left * screenWidth).toInt(),
                (top * screenHeight).toInt(),
                (right * screenWidth).toInt(),
                (bottom * screenHeight).toInt()
            )
        }
    }
    
    /**
     * 归一化点坐标（0.0-1.0）
     */
    data class NormalizedPoint(
        val x: Float,
        val y: Float
    ) {
        /**
         * 转换为像素坐标
         */
        fun toPixelPoint(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
            return Pair(
                (x * screenWidth).toInt(),
                (y * screenHeight).toInt()
            )
        }
    }
}

