package com.autoswapeng.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Path
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.autoswapeng.app.logic.WordMatcher
import com.autoswapeng.app.R
import com.autoswapeng.app.logic.Regions
import com.autoswapeng.app.config.TargetApps
import com.autoswapeng.app.logic.QuestionTypeDetector
import com.autoswapeng.app.logic.SpellingHandler
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.debug.NodeDebugger

class AppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoSwapEng"
        private const val NOTIFICATION_CHANNEL_ID = "autoswapeng_service"
        private const val NOTIFICATION_ID = 1001
        
        @Volatile
        var instance: AppAccessibilityService? = null
            private set
        
        /**
         * 启用或禁用自动操作功能
         */
        fun setEnabled(enabled: Boolean) {
            instance?.isServiceEnabled = enabled
            LogManager.i(TAG, "自动操作${if (enabled) "已启用" else "已禁用"}")
        }
        
        /**
         * 检查服务是否正在运行
         */
        fun isRunning(): Boolean = instance != null
        
        /**
         * 检查自动操作是否启用
         */
        fun isEnabled(): Boolean = instance?.isServiceEnabled ?: false
        
        /**
         * 设置运行模式
         * @param testMode true=测试模式（自动答题），false=学习模式（仅学习）
         */
        fun setTestMode(testMode: Boolean) {
            instance?.isTestMode = testMode
            LogManager.i(TAG, "切换到${if (testMode) "测试模式" else "学习模式"}")
        }
        
        /**
         * 获取当前运行模式
         */
        fun isTestMode(): Boolean = instance?.isTestMode ?: false
        
        /**
         * 获取学习的单词数量
         */
        fun getLearnedCount(): Int = instance?.matcher?.getLearnedCount() ?: 0
        
        /**
         * 调试：检测当前页面的所有节点
         */
        fun debugDetectNodes() {
            val service = instance ?: run {
                LogManager.e(TAG, "服务未运行，无法检测节点")
                return
            }
            
            val rootNode = service.rootInActiveWindow
            if (rootNode == null) {
                LogManager.e(TAG, "无法获取根节点")
                return
            }
            
            LogManager.i(TAG, "开始检测节点...")
            val nodes = NodeDebugger.detectNodes(rootNode)
            
            // 记录详细信息
            NodeDebugger.logNodes(nodes)
            
            // 生成统计报告
            NodeDebugger.generateReport(nodes)
            
            // 导出JSON（记录到日志）
            val json = NodeDebugger.toJson(nodes)
            LogManager.i(TAG, "JSON数据:")
            LogManager.i(TAG, json)
        }
        
        /**
         * 开始拼写题
         */
        fun startSpelling() {
            val service = instance ?: run {
                LogManager.w(TAG, "服务未运行")
                return
            }
            
            if (service.spellingHandler == null) {
                LogManager.w(TAG, "拼写处理器未初始化，请先授权OCR")
                return
            }
            
            // 重置状态
            service.spellingHandler?.reset()
            
            LogManager.i(TAG, "开始拼写题流程")
            service.serviceScope.launch {
                try {
                    service.spellingHandler?.handleSpelling()
                    LogManager.i(TAG, "拼写题流程完成")
                } catch (e: Exception) {
                    LogManager.e(TAG, "拼写题流程出错: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        
        /**
         * 停止拼写题
         */
        fun stopSpelling() {
            instance?.let { svc ->
                svc.spellingHandler?.cancel()
                svc.spellingJob?.cancel()
            }
            LogManager.i(TAG, "请求停止拼写题流程")
        }
        
        /**
         * 检查拼写题是否正在运行
         */
        fun isSpellingRunning(): Boolean {
            return instance?.spellingHandler?.isRunning() ?: false
        }
        
        /**
         * 开始选择题
         */
        fun startSelection() {
            LogManager.w(TAG, "选择题功能开发中，等待UI截图")
            // TODO: 实现选择题流程
        }
        
        /**
         * 开始听力题
         */
        fun startListening() {
            LogManager.w(TAG, "听力题功能开发中，等待UI截图")
            // TODO: 实现听力题流程
        }
        
    }

    private val matcher = WordMatcher()
    private val questionDetector = QuestionTypeDetector()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var spellingJob: Job? = null
    
    // OCR相关
    private var screenCaptureHelper: ScreenCaptureHelper? = null
    private var spellingHandler: SpellingHandler? = null
    
    // 前台服务状态
    @Volatile
    private var isForegroundServiceRunning = false
    
    // 防抖：避免短时间内重复处理
    private var lastProcessTime = 0L
    private val minProcessInterval = 500L  // 最小处理间隔（毫秒）
    
    // 服务启用状态
    @Volatile
    private var isServiceEnabled = true
    
    // 运行模式：true=测试模式（自动答题），false=学习模式（仅学习）
    @Volatile
    private var isTestMode = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        // 创建通知渠道（但暂不启动前台服务）
        createNotificationChannel()
        
        LogManager.i(TAG, "无障碍服务已连接")
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "AutoSwapEng 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持应用运行以提供无障碍和OCR功能"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        if (isForegroundServiceRunning) {
            LogManager.d(TAG, "前台服务已在运行中")
            return
        }
        
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AutoSwapEng 正在运行")
            .setContentText("无障碍和OCR功能已启用")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundServiceRunning = true
            LogManager.i(TAG, "✓ 前台服务已启动")
        } catch (e: Exception) {
            isForegroundServiceRunning = false
            LogManager.e(TAG, "✗ 启动前台服务失败: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 检查服务是否启用
        if (!isServiceEnabled) return
        
        // 检查是否是目标应用
        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in TargetApps.PACKAGE_NAMES) return
        
        // 记录窗口状态变化
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            LogManager.i(TAG, "页面切换: ${event.className}")
        }
        
        // 防抖处理
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < minProcessInterval) return
        lastProcessTime = currentTime
        
        val snapshotRoot = rootInActiveWindow ?: return
        serviceScope.launch {
            // 读取所有文本节点
            val texts = snapshotRoot.collectTextNodes()
            if (texts.isEmpty()) {
                LogManager.d(TAG, "未检测到文本节点")
                return@launch
            }
            
            LogManager.d(TAG, "检测到 ${texts.size} 个文本节点")

            // 使用题型检测器识别当前题型
            val detection = questionDetector.detectQuestionType(texts)
            LogManager.i(TAG, "题型: ${detection.type} (置信度: ${detection.confidence})")
            
            when (detection.type) {
                QuestionTypeDetector.QuestionType.COMPLETION -> {
                    // 完成页面，点击完成按钮
                    LogManager.i(TAG, "学习任务完成！")
                    val (fx, fy) = ratioToScreen(Regions.FINI.x, Regions.FINI.y)
                    tap(fx, fy)
                }
                
                QuestionTypeDetector.QuestionType.WORD_SELECTION -> {
                    // 单词选择题
                    handleWordSelection(detection, texts)
                }
                
                QuestionTypeDetector.QuestionType.LEARNING -> {
                    // 学习模式页面
                    handleLearningPage(detection)
                }
                
                QuestionTypeDetector.QuestionType.WORD_SPELLING -> {
                    // 拼写题：使用OCR处理
                    if (isScreenCaptureReady()) {
                        LogManager.i(TAG, "检测到拼写题，使用OCR处理")
                        spellingHandler?.let { handler ->
                            spellingJob?.cancel()
                            spellingJob = serviceScope.launch {
                                handler.handleSpelling()
                            }
                        }
                    } else {
                        LogManager.w(TAG, "检测到拼写题，但OCR未初始化")
                        clickAndSwipe()
                    }
                }
                
                QuestionTypeDetector.QuestionType.LISTENING -> {
                    // 听力题：暂不支持
                    LogManager.w(TAG, "检测到听力题，暂不支持")
                    clickAndSwipe()
                }
                
                QuestionTypeDetector.QuestionType.UNKNOWN -> {
                    // 未知类型，尝试通用处理
                    LogManager.w(TAG, "未知题型，尝试通用处理")
                    handleUnknownType(texts)
                }
            }
        }
    }
    
    /**
     * 处理单词选择题
     */
    private suspend fun handleWordSelection(
        detection: QuestionTypeDetector.DetectionResult,
        texts: List<NodeText>
    ) {
        val word = detection.word ?: return
        val options = detection.options
        
        if (options.size < 4) {
            LogManager.w(TAG, "选项不足4个")
            return
        }
        
        if (isTestMode) {
            // 测试模式：自动答题
            val bestIndex = matcher.match(word, options)
            val optionNodes = texts.filter { it.text in options }
            if (bestIndex < optionNodes.size) {
                val target = optionNodes[bestIndex]
                val (cx, cy) = centerOf(target.bounds)
                tap(cx, cy)
                LogManager.i(TAG, "[$word] 选择答案: ${options[bestIndex]}")
            }
        } else {
            // 学习模式：跳过
            LogManager.d(TAG, "学习模式，跳过选择题")
        }
        
        clickAndSwipe()
    }
    
    /**
     * 处理学习页面
     */
    private suspend fun handleLearningPage(detection: QuestionTypeDetector.DetectionResult) {
        val word = detection.word
        val definition = detection.definition
        
        if (word != null && definition != null) {
            matcher.learn(word, listOf(definition))
            LogManager.i(TAG, "学习单词: $word -> $definition")
        }
        
        clickAndSwipe()
    }
    
    /**
     * 处理未知类型（兼容旧逻辑）
     */
    private suspend fun handleUnknownType(texts: List<NodeText>) {
        val wordNode = texts.minByOrNull { distanceTo(Regions.PWORD, it.bounds) }
        val chinNode = texts.minByOrNull { distanceTo(Regions.PCHIN, it.bounds) }
        
        if (wordNode != null && chinNode != null && wordNode.text.matches(Regex("[A-Za-z]+"))) {
            matcher.learn(wordNode.text, listOf(chinNode.text))
        }
        
        clickAndSwipe()
    }
    
    /**
     * 点击并滑动到下一题
     */
    private suspend fun clickAndSwipe() {
        val (cx, cy) = ratioToScreen(Regions.CLICK.x, Regions.CLICK.y)
        tap(cx, cy)
        
        kotlinx.coroutines.delay(300)
        val (sx, sy1) = ratioToScreen(0.5060f, 0.8257f)
        val (_, sy2) = ratioToScreen(0.5060f, 0.2772f)
        swipe(sx, sy1, sx, sy2)
    }

    override fun onInterrupt() {}
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        instance = null
        LogManager.i(TAG, "无障碍服务已断开")
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        // 缩短点击持续时间，避免被识别为双击或长按
        val stroke = GestureDescription.StrokeDescription(path, 0, 20)
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null,
            null
        )
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun ratioToScreen(rx: Float, ry: Float): Pair<Float, Float> {
        val dm = resources.displayMetrics
        return dm.widthPixels * rx to dm.heightPixels * ry
    }

    private fun distanceTo(r: Regions.RectF, b: Rect): Float {
        val dm = resources.displayMetrics
        val cx = b.exactCenterX() / dm.widthPixels
        val cy = b.exactCenterY() / dm.heightPixels
        val rx = when {
            cx < r.left -> r.left - cx
            cx > r.right -> cx - r.right
            else -> 0f
        }
        val ry = when {
            cy < r.top -> r.top - cy
            cy > r.bottom -> cy - r.bottom
            else -> 0f
        }
        return rx * rx + ry * ry
    }
    
    /**
     * 初始化屏幕截图功能
     */
    fun initScreenCapture(resultCode: Int, data: Intent) {
        try {
            LogManager.i(TAG, "========== 开始初始化屏幕截图 ==========")
            LogManager.i(TAG, "ResultCode: $resultCode")
            
            // 步骤1：启动前台服务
            LogManager.d(TAG, "步骤1: 启动前台服务")
            startForegroundService()
            
            if (!isForegroundServiceRunning) {
                LogManager.e(TAG, "✗ 前台服务启动失败，无法继续")
                return
            }
            LogManager.i(TAG, "✓ 前台服务运行中")
            
            // 步骤2：获取 MediaProjection
            LogManager.d(TAG, "步骤2: 获取 MediaProjection")
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            LogManager.i(TAG, "✓ MediaProjection 获取成功")
            
            // 步骤3：创建 ScreenCaptureHelper
            LogManager.d(TAG, "步骤3: 创建 ScreenCaptureHelper")
            if (screenCaptureHelper == null) {
                screenCaptureHelper = ScreenCaptureHelper(applicationContext)
                LogManager.i(TAG, "✓ ScreenCaptureHelper 创建成功")
            } else {
                LogManager.d(TAG, "ScreenCaptureHelper 已存在")
            }
            
            // 步骤4：初始化 MediaProjection
            LogManager.d(TAG, "步骤4: 初始化 MediaProjection")
            screenCaptureHelper?.initMediaProjection(mediaProjection)
            
            // 步骤5：验证初始化状态
            val isReady = screenCaptureHelper?.isInitialized() ?: false
            LogManager.i(TAG, "步骤5: 初始化状态检查 = $isReady")
            
            if (!isReady) {
                LogManager.e(TAG, "✗ ScreenCaptureHelper 初始化失败")
                return
            }
            
            // 步骤6：初始化拼写处理器
            LogManager.d(TAG, "步骤6: 初始化拼写处理器")
            screenCaptureHelper?.let { helper ->
                spellingHandler = SpellingHandler(
                    screenCapture = helper,
                    tap = { x, y -> tap(x.toFloat(), y.toFloat()) },
                    onProgress = { message ->
                        LogManager.i(TAG, "拼写进度: $message")
                    }
                )
                LogManager.i(TAG, "✓ 拼写处理器创建成功")
            }
            
            LogManager.i(TAG, "========== ✓ 屏幕截图功能初始化完成 ==========")
            LogManager.i(TAG, "OCR 状态: isReady = $isReady")
        } catch (e: Exception) {
            LogManager.e(TAG, "========== ✗ 初始化屏幕截图失败 ==========")
            LogManager.e(TAG, "错误: ${e.message}")
            LogManager.e(TAG, "堆栈: ${e.stackTraceToString()}")
            e.printStackTrace()
        }
    }
    
    /**
     * 检查屏幕截图是否就绪
     */
    fun isScreenCaptureReady(): Boolean {
        return screenCaptureHelper?.isInitialized() ?: false
    }
    
    /**
     * 获取OCR状态详情（用于调试）
     */
    fun getOcrStatusDetails(): String {
        return buildString {
            appendLine("前台服务: ${if (isForegroundServiceRunning) "✓" else "✗"}")
            appendLine("ScreenCaptureHelper: ${if (screenCaptureHelper != null) "✓" else "✗"}")
            appendLine("OCR就绪: ${if (screenCaptureHelper?.isInitialized() == true) "✓" else "✗"}")
            appendLine("拼写处理器: ${if (spellingHandler != null) "✓" else "✗"}")
        }
    }
    
}


