package com.autoswapeng.app.logic

import com.autoswapeng.app.accessibility.ScreenCaptureHelper
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.ocr.MiniProgramRegions
import kotlinx.coroutines.delay

/**
 * 听力题处理器（按桌面端原型迁移）
 * 规则：
 * - 关键词匹配："温馨提示" → 点击 STAR；"当前卡包已完成" → 点击 BUTTON3 结束；
 * - "内容会慢慢呈现" 或 "已播放" → 点击 BUTTON1；
 * - "点击按钮" 或 "不能暂停" → 点击 BUTTON2；
 */
class ListeningHandler(
    private val screenCapture: ScreenCaptureHelper,
    private val tap: suspend (Int, Int) -> Unit,
    private val onProgress: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "ListeningHandler"
        private const val LOOP_MAX = 80
        private const val OCR_DELAY = 400L
        private const val CLICK_DELAY = 600L
    }

    private suspend fun clickPoint(p: MiniProgramRegions.NormalizedPoint) {
        val (x, y) = p.toPixelPoint(screenCapture.screenWidth, screenCapture.screenHeight)
        LogManager.d(TAG, "点击: ($x, $y)")
        tap(x, y)
    }

    suspend fun handleListening() {
        LogManager.i(TAG, "========== 听力题流程启动 ==========")
        delay(500)
        for (i in 1..LOOP_MAX) {
            onProgress?.invoke("🎧 听力处理中 ($i/$LOOP_MAX)")
            // 截屏+OCR（全屏，混合识别）
            val text = screenCapture.captureAndRecognize(MiniProgramRegions.Listening.FULL, "listening-full")

            when {
                // 完成
                text.contains("当前卡包已完成") -> {
                    LogManager.i(TAG, "检测到完成，点击 BUTTON3")
                    com.autoswapeng.app.log.LogManager.event(
                        code = "LISTEN_DONE",
                        tag = TAG,
                        message = "卡包完成",
                        data = mapOf("loop" to i)
                    )
                    clickPoint(MiniProgramRegions.Listening.BUTTON3)
                    delay(CLICK_DELAY)
                    return
                }
                // 初次温馨提示
                text.contains("温馨提示") -> {
                    LogManager.i(TAG, "检测到温馨提示，点击 STAR")
                    com.autoswapeng.app.log.LogManager.event(
                        code = "LISTEN_TIP",
                        tag = TAG,
                        message = "温馨提示",
                        data = mapOf("loop" to i)
                    )
                    clickPoint(MiniProgramRegions.Listening.STAR)
                    delay(CLICK_DELAY)
                }
                // 播放相关
                text.contains("内容会慢慢呈现") || text.contains("已播放") -> {
                    LogManager.i(TAG, "检测到播放提示，点击 BUTTON1")
                    com.autoswapeng.app.log.LogManager.event(
                        code = "LISTEN_PLAY",
                        tag = TAG,
                        message = "播放/重播",
                        data = mapOf("loop" to i)
                    )
                    // 1) 点击播放按钮
                    clickPoint(MiniProgramRegions.Listening.BUTTON1)
                    delay(CLICK_DELAY)

                    // 2) 按需求：点击右下角下一题按钮，频率为播放按钮的两倍
                    repeat(2) { idx ->
                        com.autoswapeng.app.log.LogManager.event(
                            code = "LISTEN_NEXT",
                            tag = TAG,
                            message = "点击右下角下一题",
                            data = mapOf("seq" to (idx + 1), "of" to 2, "loop" to i)
                        )
                        clickPoint(MiniProgramRegions.Listening.NEXT)
                        delay(CLICK_DELAY / 2)
                    }
                }
                // 录音/确定类提示
                text.contains("点击按钮") || text.contains("不能暂停") -> {
                    LogManager.i(TAG, "检测到录音/确定提示，点击 BUTTON2")
                    com.autoswapeng.app.log.LogManager.event(
                        code = "LISTEN_RECORD",
                        tag = TAG,
                        message = "录音/确定",
                        data = mapOf("loop" to i)
                    )
                    clickPoint(MiniProgramRegions.Listening.BUTTON2)
                    delay(CLICK_DELAY)
                }
                else -> {
                    LogManager.d(TAG, "未匹配到关键字，等待...")
                    delay(OCR_DELAY)
                }
            }
        }
        LogManager.w(TAG, "达到循环上限，结束听力题流程")
        com.autoswapeng.app.log.LogManager.event(
            code = "LISTEN_TIMEOUT",
            tag = TAG,
            message = "循环上限",
            data = mapOf("max" to LOOP_MAX),
            level = com.autoswapeng.app.log.LogManager.LogEntry.Level.WARN
        )
    }
}


