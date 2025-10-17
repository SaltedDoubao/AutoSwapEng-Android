package com.autoswapeng.app.logic

/**
 * 将 Python 版比例坐标映射为 Android 使用的归一化区域。
 * 坐标均为 [0,1] 范围，后续根据屏幕实际尺寸换算。
 */
object Regions {
    // 学习/测试划分与文本区域（对应 PySE_Select.py）
    val PWORD  = RectF(0.1698f, 0.3056f, 0.9150f, 0.4053f) // 单词
    val KWORD  = RectF(0.1005f, 0.2877f, 0.9098f, 0.3805f) // 题目单词
    val PCHIN  = RectF(0.1421f, 0.4691f, 0.9081f, 0.7788f) // 中文释义
    val PTEST  = RectF(0.0935f, 0.3785f, 0.9029f, 0.4497f) // 测试判断区
    val PSELC  = RectF(0.0883f, 0.4865f, 0.9150f, 0.8316f) // 选项区域

    val CLICK  = PointF(0.5060f, 0.3006f)
    val FINI   = PointF(0.5069f, 0.8755f)
    val SELEC_Y = floatArrayOf(0f, 0.5284f, 0.5938f, 0.6633f, 0.7353f)
    const val SELEC_X = 0.5060f

    data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)
    data class PointF(val x: Float, val y: Float)
}


