package com.autoswapeng.app.logic

import com.autoswapeng.app.accessibility.NodeText
import com.autoswapeng.app.log.LogManager

/**
 * 选择题批处理编排器
 * - 智能检测当前页面，支持用户中途开启服务
 * - 执行“学N题 -> 选N题”循环，默认 N=5
 * - 不直接做 UI 操作，委托给传入的回调以复用现有实现
 */
class SelectionHandler(
    private val batchSize: Int = 5,
    private val onLearn: suspend (QuestionTypeDetector.DetectionResult) -> Unit,
    private val onSelect: suspend (QuestionTypeDetector.DetectionResult, List<NodeText>) -> Unit,
    private val onSkip: suspend () -> Unit,
    private val onLog: (String) -> Unit = { LogManager.i(TAG, it) }
) {
    companion object { private const val TAG = "SelectionHandler" }

    private enum class Phase { LEARN, SELECT }

    private var currentPhase: Phase? = null
    private var learnedInBatch: Int = 0
    private var selectedInBatch: Int = 0

    @Volatile
    private var cancelled: Boolean = false

    fun cancel() { cancelled = true }
    fun isRunning(): Boolean = !cancelled

    fun reset() {
        learnedInBatch = 0
        selectedInBatch = 0
        currentPhase = null
        cancelled = false
    }

    /**
     * 处理一次页面检测结果；返回是否已消费（即由本调度器执行了具体动作）。
     */
    suspend fun handle(
        detection: QuestionTypeDetector.DetectionResult,
        texts: List<NodeText>
    ): Boolean {
        if (cancelled) return false

        // 初次/异常场景：根据当前页面类型推断阶段
        if (currentPhase == null) {
            currentPhase = when (detection.type) {
                QuestionTypeDetector.QuestionType.LEARNING -> Phase.LEARN
                QuestionTypeDetector.QuestionType.WORD_SELECTION -> Phase.SELECT
                else -> null
            }
            currentPhase?.let { onLog("初始化阶段=$it") }
        }

        return when (detection.type) {
            // 学习页面：记录并进入下一题
            QuestionTypeDetector.QuestionType.LEARNING -> {
                onLearn(detection)
                if (currentPhase == null) currentPhase = Phase.LEARN
                if (currentPhase == Phase.LEARN) {
                    learnedInBatch++
                    onLog("学习计数: $learnedInBatch/$batchSize")
                    if (learnedInBatch >= batchSize) {
                        // 切换到选择阶段
                        learnedInBatch = 0
                        currentPhase = Phase.SELECT
                        onLog("切换阶段 -> SELECT")
                    }
                }
                true
            }

            // 选择题页面：作答并进入下一题
            QuestionTypeDetector.QuestionType.WORD_SELECTION -> {
                onSelect(detection, texts)
                if (currentPhase == null) currentPhase = Phase.SELECT
                if (currentPhase == Phase.SELECT) {
                    selectedInBatch++
                    onLog("选择计数: $selectedInBatch/$batchSize")
                    if (selectedInBatch >= batchSize) {
                        // 切换到学习阶段
                        selectedInBatch = 0
                        currentPhase = Phase.LEARN
                        onLog("切换阶段 -> LEARN")
                    }
                }
                true
            }

            // 可跳过的类型：统一使用 onSkip 进入下一题
            QuestionTypeDetector.QuestionType.UNKNOWN,
            QuestionTypeDetector.QuestionType.LISTENING -> {
                onSkip()
                true
            }

            // 由外层按原逻辑处理（如拼写、完成页等）
            else -> false
        }
    }
}


