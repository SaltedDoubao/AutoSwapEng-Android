package com.autoswapeng.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import com.autoswapeng.app.config.TargetApps
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.debug.NodeDebugger
import com.autoswapeng.app.ocr.MiniProgramRegions

/**
 * 无障碍服务
 * 
 * 保留基础框架，自动处理逻辑已清空，待重写
 * 
 * TODO: 待实现功能
 * - [ ] 题型识别与检测
 * - [ ] 单词学习与记忆
 * - [ ] 选择题自动答题
 * - [ ] 拼写题自动处理
 * - [ ] 听力题自动处理
 */
class AppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoSwapEng"
        private const val NOTIFICATION_CHANNEL_ID = "autoswapeng_service"
        private const val NOTIFICATION_ID = 1001
        
        @Volatile
        var instance: AppAccessibilityService? = null
            private set
        
        /**
         * 检查服务是否正在运行
         */
        fun isRunning(): Boolean = instance != null
        
        /**
         * 检查自动操作是否启用
         */
        fun isEnabled(): Boolean = instance?.isServiceEnabled ?: false
        
        /**
         * 启用或禁用自动操作功能
         */
        fun setEnabled(enabled: Boolean) {
            instance?.isServiceEnabled = enabled
            LogManager.i(TAG, "自动操作${if (enabled) "已启用" else "已禁用"}")
        }
        
        /**
         * 获取当前运行模式
         */
        fun isTestMode(): Boolean = instance?.isTestMode ?: false
        
        /**
         * 设置运行模式
         */
        fun setTestMode(testMode: Boolean) {
            instance?.isTestMode = testMode
            LogManager.i(TAG, "切换到${if (testMode) "测试模式" else "学习模式"}")
        }
        
        /**
         * 获取学习的单词数量（TODO: 待实现）
         */
        fun getLearnedCount(): Int = 0
        
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
         * TODO: 开始拼写题流程
         */
        fun startSpelling() {
            val service = instance ?: run {
                LogManager.e(TAG, "服务未运行，无法开始拼写题")
                return
            }
            if (service.spellingJob?.isActive == true) {
                LogManager.w(TAG, "拼写题任务已在运行中")
                return
            }
            service.spellingJob = service.serviceScope.launch {
                service.handleSpellingOnce()
            }
        }
        
        /**
         * TODO: 停止拼写题流程
         */
        fun stopSpelling() {
            val service = instance ?: return
            service.spellingJob?.cancel()
            service.spellingJob = null
            LogManager.i(TAG, "拼写题任务已停止")
        }
        
        /**
         * TODO: 检查拼写题是否正在运行
         */
        fun isSpellingRunning(): Boolean = instance?.spellingJob?.isActive == true
        
        /**
         * TODO: 开始选择题流程
         */
        fun startSelection() {
            val service = instance ?: run {
                LogManager.e(TAG, "服务未运行，无法开始选择题")
                return
            }
            if (service.selectionJob?.isActive == true) {
                LogManager.w(TAG, "选择题任务已在运行中")
                return
            }
            service.selectionJob = service.serviceScope.launch {
                service.handleSelectionOnce()
            }
        }
        
        /**
         * TODO: 停止选择题流程
         */
        fun stopSelection() {
            val service = instance ?: return
            service.selectionJob?.cancel()
            service.selectionJob = null
            LogManager.i(TAG, "选择题任务已停止")
        }
        
        /**
         * TODO: 检查选择题是否正在运行
         */
        fun isSelectionRunning(): Boolean = instance?.selectionJob?.isActive == true
        
        /**
         * TODO: 开始听力题流程
         */
        fun startListening() {
            val service = instance ?: run {
                LogManager.e(TAG, "服务未运行，无法开始听力题")
                return
            }
            if (service.listeningJob?.isActive == true) {
                LogManager.w(TAG, "听力题任务已在运行中")
                return
            }
            service.listeningJob = service.serviceScope.launch {
                service.handleListeningOnce()
            }
        }
        fun stopListening() {
            val service = instance ?: return
            service.listeningJob?.cancel()
            service.listeningJob = null
            LogManager.i(TAG, "听力题任务已停止")
        }
        fun isListeningRunning(): Boolean = instance?.listeningJob?.isActive == true
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 手势互斥锁：确保同一时间只有一个手势在执行，防止重复操作
    private val gestureMutex = Mutex()
    
    // OCR相关
    private var screenCaptureHelper: ScreenCaptureHelper? = null
    
    // 选择题任务
    private var selectionJob: Job? = null
    // 拼写题任务
    private var spellingJob: Job? = null
    // 听力题任务
    private var listeningJob: Job? = null
    
    // 前台服务状态
    @Volatile
    private var isForegroundServiceRunning = false
    
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
        
        LogManager.i(TAG, "✓ 无障碍服务已连接")
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

    /**
     * 无障碍事件处理
     * 
     * TODO: 实现自动处理逻辑
     * - 检测目标应用
     * - 识别题型
     * - 执行相应操作
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 检查服务是否启用
        if (!isServiceEnabled) return
        
        // 检查是否是目标应用
        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in TargetApps.PACKAGE_NAMES) return
        
        // 记录窗口状态变化
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            LogManager.d(TAG, "页面切换: ${event.className}")
        }
        
        // TODO: 在这里实现自动处理逻辑
        // 1. 读取文本节点
        // 2. 检测题型
        // 3. 执行相应操作
    }

    override fun onInterrupt() {
        LogManager.w(TAG, "服务被中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        instance = null
        LogManager.i(TAG, "✗ 无障碍服务已断开")
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
        }
    }

    /**
     * 对指定坐标执行一次轻点手势（挂起直到完成/失败）
     * 
     * 改进点（防止重复操作）：
     * 1. 使用Mutex锁确保同一时间只有一个手势在执行（关键修复）
     * 2. 使用严格单点路径（避免微移动导致的拖动识别）
     * 3. 缩短手势持续时间到30ms（更接近真实点击，避免被识别为长按或多次触摸）
     * 4. 增加防抖延迟到150ms（确保UI完全响应，防止重复触发）
     * 5. 添加详细日志（追踪每次点击的完整生命周期）
     * 6. 检查手势分发状态（确保手势被正确提交）
     */
    suspend fun tapSuspending(x: Int, y: Int) {
        // 使用互斥锁确保同一时间只有一个手势在执行
        gestureMutex.withLock {
            val tapId = System.currentTimeMillis() % 10000  // 生成简短ID用于追踪同一次点击
            
            try {
                LogManager.d(TAG, "[$tapId] 🔒 获取手势锁，开始点击 ($x, $y)")
                
                // 使用严格单点路径，避免被识别为拖动
                val path = Path().apply { 
                    moveTo(x.toFloat(), y.toFloat())
                    // 不添加 lineTo，保持单点击
                }
                
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 30))  // 30ms 真实点击时长
                    .build()
                
                // 等待手势完成
                val gestureCompleted = suspendCancellableCoroutine { cont ->
                    val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            LogManager.d(TAG, "[$tapId] 手势已完成")
                            cont.resume(true)
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            LogManager.w(TAG, "[$tapId] 手势被取消")
                            cont.resume(false)
                        }
                    }, null)
                    
                    if (!dispatched) {
                        LogManager.e(TAG, "[$tapId] 手势分发失败")
                        cont.resume(false)
                    }
                }
                
                if (gestureCompleted) {
                    // 关键修复：等待UI完全响应，防止重复触发
                    // 真实手指操作后系统有自然的处理时间，模拟需要人为等待
                    kotlinx.coroutines.delay(150)  // 150ms 防抖延迟
                    LogManager.d(TAG, "[$tapId] ✓ 点击完成，释放锁")
                } else {
                    LogManager.w(TAG, "[$tapId] ✗ 点击未成功")
                }
                
            } catch (e: Exception) {
                LogManager.e(TAG, "[$tapId] tap 失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 执行上滑手势（用于切换到下一个单词/题目）
     * 
     * 改进点（防止重复操作）：
     * 1. 使用Mutex锁确保手势串行执行
     * 2. 添加手势追踪ID
     * 3. 检查分发状态
     * 4. 增加稳定延迟，等待滑动动画完成
     * 5. 修复：提前检查screenCapture状态，避免静默失败
     */
    suspend fun swipeUpGesture() {
        val swipeId = System.currentTimeMillis() % 10000
        
        // 提前检查屏幕捕获状态（在获取锁之前）
        val capture = screenCaptureHelper
        if (capture == null) {
            LogManager.e(TAG, "[$swipeId] ✗ 上滑失败: ScreenCaptureHelper未初始化")
            return
        }
        
        val screenWidth = capture.screenWidth
        val screenHeight = capture.screenHeight
        if (screenWidth <= 0 || screenHeight <= 0) {
            LogManager.e(TAG, "[$swipeId] ✗ 上滑失败: 屏幕尺寸无效 (${screenWidth}x${screenHeight})")
            return
        }
        
        // 使用互斥锁确保同一时间只有一个手势在执行
        gestureMutex.withLock {
            try {
                LogManager.d(TAG, "[$swipeId] 🔒 获取手势锁，开始上滑手势")
                LogManager.d(TAG, "[$swipeId] 屏幕尺寸: ${screenWidth}x${screenHeight}")
                
                // 从屏幕中下方向上滑动
                val startX = screenWidth / 2f
                val startY = screenHeight * 0.7f
                val endY = screenHeight * 0.3f
                
                LogManager.d(TAG, "[$swipeId] 滑动路径: (${startX.toInt()}, ${startY.toInt()}) -> (${startX.toInt()}, ${endY.toInt()})")
                
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(startX, endY)
                }
                
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 300))  // 300ms滑动
                    .build()
                    
                val gestureCompleted = suspendCancellableCoroutine { cont ->
                    val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            LogManager.d(TAG, "[$swipeId] 上滑手势已完成")
                            cont.resume(true)
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            LogManager.w(TAG, "[$swipeId] 上滑手势被取消")
                            cont.resume(false)
                        }
                    }, null)
                    
                    if (!dispatched) {
                        LogManager.e(TAG, "[$swipeId] 上滑手势分发失败")
                        cont.resume(false)
                    } else {
                        LogManager.d(TAG, "[$swipeId] 上滑手势已分发")
                    }
                }
                
                if (gestureCompleted) {
                    // 等待滑动动画完全完成，防止过早进行下一个操作
                    kotlinx.coroutines.delay(200)  // 滑动需要更长的稳定时间
                    LogManager.d(TAG, "[$swipeId] ✓ 上滑完成，释放锁")
                } else {
                    LogManager.w(TAG, "[$swipeId] ✗ 上滑未成功")
                }
                
            } catch (e: Exception) {
                LogManager.e(TAG, "[$swipeId] swipeUp 失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 尝试直接向当前可编辑输入框写入文本
     * 返回是否成功
     */
    fun setTextOnInput(text: String): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val candidates = mutableListOf<AccessibilityNodeInfo>()
            fun dfs(n: AccessibilityNodeInfo?) {
                if (n == null) return
                val cls = n.className?.toString() ?: ""
                if ((cls.contains("EditText") || n.inputType != 0) && n.isEditable) {
                    candidates.add(n)
                }
                for (i in 0 until n.childCount) dfs(n.getChild(i))
            }
            dfs(root)
            val target = candidates.firstOrNull() ?: return false
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            LogManager.d(TAG, "setTextOnInput(${text.length}) => $ok")
            ok
        } catch (e: Exception) {
            LogManager.e(TAG, "setTextOnInput 失败: ${e.message}")
            false
        }
    }

    /**
     * 单次选择题处理：使用基于页面状态的智能处理器
     */
    private suspend fun handleSelectionOnce() {
        val capture = screenCaptureHelper
        if (capture?.isInitialized() != true) {
            LogManager.e(TAG, "OCR未就绪，无法进行选择题")
            return
        }

        // 使用严格按原型实现的处理器
        val handler = com.autoswapeng.app.logic.PrototypeSelectionHandler(
            screenCapture = capture,
            tap = { x, y -> tapSuspending(x, y) },
            swipeUp = { swipeUpGesture() },
            onProgress = { msg -> LogManager.i(TAG, msg) }
        )
        
        try {
            LogManager.i(TAG, "========== 开始原型化选择题模式 ==========")
            handler.run()
        } catch (e: Exception) {
            LogManager.e(TAG, "智能学习失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 单次拼写题处理：按原型的拼写流程
     */
    private suspend fun handleSpellingOnce() {
        val capture = screenCaptureHelper
        if (capture?.isInitialized() != true) {
            LogManager.e(TAG, "OCR未就绪，无法进行拼写题")
            return
        }
        try {
            val handler = com.autoswapeng.app.logic.SpellingHandler(
                screenCapture = capture,
                tap = { x, y -> tapSuspending(x, y) },
                onProgress = { msg -> LogManager.i(TAG, msg) }
            )
            LogManager.i(TAG, "========== 开始拼写题模式 ==========")
            handler.handleSpelling()
        } catch (e: Exception) {
            LogManager.e(TAG, "拼写题失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 单次听力题处理：按原型的监听规则
     */
    private suspend fun handleListeningOnce() {
        val capture = screenCaptureHelper
        if (capture?.isInitialized() != true) {
            LogManager.e(TAG, "OCR未就绪，无法进行听力题")
            return
        }
        try {
            val handler = com.autoswapeng.app.logic.ListeningHandler(
                screenCapture = capture,
                tap = { x, y -> tapSuspending(x, y) },
                onProgress = { msg -> LogManager.i(TAG, msg) }
            )
            LogManager.i(TAG, "========== 开始听力题模式 ==========")
            handler.handleListening()
        } catch (e: Exception) {
            LogManager.e(TAG, "听力题失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 回退处理：顺序点击（保留原逻辑）
     */
    private suspend fun handleSelectionFallback(capture: ScreenCaptureHelper) {
        try {
            LogManager.w(TAG, "使用回退策略：顺序点击")
            val wordRaw = capture.captureAndRecognize(MiniProgramRegions.Selection.WORD_AREA, "select-word").trim()
            val word = Regex("[A-Za-z]{2,32}").find(wordRaw)?.value ?: wordRaw
            
            val clicks = listOf(
                MiniProgramRegions.Selection.OPTION_A,
                MiniProgramRegions.Selection.OPTION_B,
                MiniProgramRegions.Selection.OPTION_C,
                MiniProgramRegions.Selection.OPTION_D
            ).map { it.toPixelPoint(capture.screenWidth, capture.screenHeight) }

            val initialWord = word
            for ((idx, pt) in clicks.withIndex()) {
                LogManager.i(TAG, "尝试点击选项 ${'A' + idx}: (${pt.first}, ${pt.second})")
                tapSuspending(pt.first, pt.second)
                kotlinx.coroutines.delay(900)
                val newWordRaw = capture.captureAndRecognize(MiniProgramRegions.Selection.WORD_AREA, "select-word").trim()
                val newWord = Regex("[A-Za-z]{2,32}").find(newWordRaw)?.value ?: newWordRaw
                if (!newWord.equals(initialWord, ignoreCase = true) && newWord.isNotEmpty()) {
                    LogManager.i(TAG, "✓ 题目已变化 -> 选择成功: '$initialWord' -> '$newWord'")
                    return
                }
            }

            val (nx, ny) = MiniProgramRegions.Selection.NEXT_TAP
                .toPixelPoint(capture.screenWidth, capture.screenHeight)
            LogManager.w(TAG, "未检测到题目变化，保底点击: ($nx, $ny)")
            tapSuspending(nx, ny)
        } catch (e: Exception) {
            LogManager.e(TAG, "回退处理失败: ${e.message}")
        }
    }
}
