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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.app.NotificationCompat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
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
import com.autoswapeng.app.logic.SelectionHandler
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
            service.spellingJob?.cancel()
            service.spellingJob = service.serviceScope.launch {
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
            return instance?.spellingJob?.isActive == true
        }
        
        /**
         * 开始选择题
         */
        fun startSelection() {
            val service = instance ?: run {
                LogManager.w(TAG, "服务未运行")
                return
            }
            // 开启测试模式，事件循环将自动识别并作答
            service.isTestMode = true
            service.isServiceEnabled = true
            service.selectionHandler.reset()
            LogManager.i(TAG, "开始选择题流程（测试模式）")
        }
        
        /**
         * 停止选择题
         */
        fun stopSelection() {
            instance?.selectionHandler?.cancel()
            LogManager.i(TAG, "请求停止选择题流程")
        }
        
        /**
         * 检查选择题是否正在运行
         */
        fun isSelectionRunning(): Boolean {
            return instance?.selectionHandler?.isRunning() == true
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
    private val selectionHandler = SelectionHandler(
        batchSize = 5,
        onLearn = { detection -> handleLearningPage(detection) },
        onSelect = { detection, texts -> handleWordSelection(detection, texts) },
        onSkip = { clickAndSwipe() },
        onLog = { LogManager.i(TAG, it) }
    )
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
            
            // 日志优化：仅在开发模式或文本节点变化时记录
            if (texts.isEmpty()) {
                LogManager.d(TAG, "未检测到文本节点")
                // 不立即返回，让后续逻辑有机会处理空页面（可能正在加载）
            } else {
                LogManager.d(TAG, "检测到 ${texts.size} 个文本节点")
                // 调试：记录前几个文本内容
                texts.take(5).forEach { 
                    LogManager.d(TAG, "  节点: '${it.text}' at ${it.bounds}")
                }
            }

            // 使用题型检测器识别当前题型
            val detection = questionDetector.detectQuestionType(texts)
            LogManager.i(TAG, "题型: ${detection.type} (置信度: ${detection.confidence})")
            
            // 记录检测结果详情
            detection.word?.let { LogManager.d(TAG, "  word='$it'") }
            detection.definition?.let { LogManager.d(TAG, "  definition='$it'") }
            if (detection.options.isNotEmpty()) {
                LogManager.d(TAG, "  options=${detection.options}")
            }

            // 交给选择题批处理器优先处理（学5题→选5题），若返回true表示已消费
            try {
                val consumed = selectionHandler.handle(detection, texts)
                LogManager.d(TAG, "SelectionHandler.handle() consumed=$consumed")
                if (consumed) return@launch
            } catch (e: Exception) {
                LogManager.e(TAG, "SelectionHandler 处理出错: ${e.message}")
                e.printStackTrace()
            }
            
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
        val options = detection.options
        if (options.size < 4) {
            LogManager.w(TAG, "选项不足4个")
            return
        }

        if (isTestMode) {
            // 根据方向决定匹配方式
            val isCnOptions = options.any { it.any { ch -> ch in '\u4e00'..'\u9fff' } }
            val isEnOptions = options.all { it.matches(Regex("^[A-Za-z]+$")) }

            val bestIndex = when {
                detection.word != null && isCnOptions -> {
                    matcher.match(detection.word, options)
                }
                detection.definition != null && isEnOptions -> {
                    matcher.matchByDefinition(detection.definition, options)
                }
                else -> {
                    // 容错：如果无法判断方向，则默认按 learn 过的信息进行中文匹配
                    detection.word?.let { matcher.match(it, options) } ?: 0
                }
            }

            // 1) 优先通过文本节点精确匹配到可点击区域
            val targetNode = findOptionNodeForIndex(bestIndex, options, texts)
            if (targetNode != null) {
                val (cx, cy) = centerOf(targetNode.bounds)
                tap(cx, cy)
                LogManager.i(TAG, "选择题命中 index=$bestIndex 文本='${options[bestIndex]}' at (${cx}, ${cy})")
            } else {
                // 2) 退化：按预设行坐标点击
                val rowY = Regions.SELEC_Y.getOrNull(bestIndex + 1) ?: Regions.SELEC_Y.last()
                val (fx, fy) = ratioToScreen(Regions.SELEC_X, rowY)
                tap(fx, fy)
                LogManager.w(TAG, "未定位到目标节点，按行坐标兜底点击 index=$bestIndex at (${fx}, ${fy})")
            }
        } else {
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
        val englishRegex = Regex("^[A-Za-z]+$")
        val hasEnglishOnly = texts.any { it.text.matches(englishRegex) } &&
            texts.none { it.text.any { ch -> ch in '\u4e00'..'\u9fff' } }

        if (hasEnglishOnly) {
            // 仅有英文单词，推测处于学习卡片未展开状态：点击卡片显示中文释义
            val (fx, fy) = ratioToScreen(Regions.CLICK.x, Regions.CLICK.y)
            tap(fx, fy)
            LogManager.i(TAG, "学习页未展开，点击卡片以显示中文释义")
            return
        }

        val wordNode = texts.minByOrNull { distanceTo(Regions.PWORD, it.bounds) }
        val chinNode = texts.minByOrNull { distanceTo(Regions.PCHIN, it.bounds) }

        if (wordNode != null && chinNode != null && wordNode.text.matches(Regex("[A-Za-z]+"))) {
            matcher.learn(wordNode.text, listOf(chinNode.text))
        }

        clickAndSwipe()
    }

    /**
     * 在文本节点中定位第 index 个选项的节点，考虑前缀（A./B./C./D.）与空白差异
     */
    private fun findOptionNodeForIndex(index: Int, options: List<String>, texts: List<NodeText>): NodeText? {
        if (index !in options.indices) return null
        val target = normalizeOpt(options[index])

        // 先按中文/英文分别策略匹配
        val candidates = texts.filter { n ->
            val norm = normalizeOpt(n.text)
            norm == target || norm.contains(target) || target.contains(norm)
        }
        if (candidates.isNotEmpty()) return candidates.minByOrNull { distanceTo(Regions.PSELC, it.bounds) }

        // 如果按文本匹配不到，退化：根据纵向行序点击
        return null
    }

    private fun normalizeOpt(text: String): String {
        // 去除选项前缀，如 "A. ", "B) " 等
        val noPrefix = text.replace(Regex("^[A-Da-d][\\.．、\"\\)\\)]\\s*"), "")
        return noPrefix
            .replace("\u00A0", " ") // nbsp
            .replace(Regex("\\s+"), " ")
            .trim()
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
        // 保持一个极短的点击以触发单次按键
        val stroke = GestureDescription.StrokeDescription(path, 0, 20)
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null,
            null
        )
    }

    /**
     * 带回调的点击，挂起直到系统完成手势（防止快速连续点击导致键盘重复上屏）
     * 根据 Android Accessibility 最佳实践：短促点击，长延迟
     */
    private suspend fun tapAwait(x: Float, y: Float) {
        // 创建静态的单点路径（无抖动）
        val path = Path().apply { moveTo(x, y) }
        
        // 使用短促的手势持续时间（30ms），避免被输入法误判为长按
        // 参考文档：手势持续时间过长会导致输入法重复处理
        val stroke = GestureDescription.StrokeDescription(path, 0, 30)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        
        val startTime = System.currentTimeMillis()
        
        return suspendCancellableCoroutine { cont ->
            dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    val duration = System.currentTimeMillis() - startTime
                    if (!cont.isCompleted) {
                        LogManager.d(TAG, "✓ 手势完成: ($x, $y), 耗时: ${duration}ms")
                        cont.resume(Unit)
                    } else {
                        LogManager.w(TAG, "⚠️ 手势回调重复: ($x, $y)")
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    val duration = System.currentTimeMillis() - startTime
                    if (!cont.isCompleted) {
                        LogManager.w(TAG, "✗ 手势取消: ($x, $y), 耗时: ${duration}ms")
                        cont.resume(Unit)
                    }
                }
            }, null)
        }
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    /**
     * 直接向当前输入框写入文本（优先使用，无需坐标敲击）。
     * 返回是否写入成功。
     */
    fun setTextOnInput(text: String): Boolean {
        LogManager.d(TAG, "尝试写入文本到输入框: '$text'")
        val root = rootInActiveWindow ?: return false

        // 1) 优先取当前焦点输入框
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            LogManager.d(TAG, "找到焦点输入框")
            val ok = performSetText(focused, text)
            if (ok) {
                LogManager.i(TAG, "✓ 通过焦点输入框成功写入文本")
                return true
            }
        }

        // 2) 按区域和可编辑性兜底查找
        val fallback = findEditableNodeNearInput(root)
        if (fallback != null) {
            LogManager.d(TAG, "找到可编辑节点（区域匹配）")
            val ok = performSetText(fallback, text)
            if (ok) {
                LogManager.i(TAG, "✓ 通过区域匹配成功写入文本")
                return true
            }
        }

        // 3) 终极备选：剪贴板+粘贴
        LogManager.w(TAG, "ACTION_SET_TEXT 失败，尝试剪贴板粘贴")
        return tryClipboardPaste(text)
    }

    private fun performSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        LogManager.d(TAG, "performAction(ACTION_SET_TEXT) = $result")
        return result
    }

    /**
     * 通过剪贴板粘贴文本（终极备选）
     */
    private fun tryClipboardPaste(text: String): Boolean {
        return try {
            // 将文本放入剪贴板
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) {
                LogManager.e(TAG, "无法获取剪贴板服务")
                return false
            }
            
            val clip = ClipData.newPlainText("autoswapeng", text)
            clipboard.setPrimaryClip(clip)
            LogManager.d(TAG, "文本已放入剪贴板")
            
            // 查找输入框并执行粘贴
            val root = rootInActiveWindow ?: return false
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: findEditableNodeNearInput(root)
            
            if (focused != null) {
                // 先清空（如果有内容）
                performSetText(focused, "")
                Thread.sleep(50)
                
                // 执行粘贴
                val pasteOk = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                LogManager.d(TAG, "performAction(ACTION_PASTE) = $pasteOk")
                
                if (pasteOk) {
                    LogManager.i(TAG, "✓ 通过剪贴板粘贴成功写入文本")
                    return true
                }
            }
            
            LogManager.w(TAG, "找不到可粘贴的输入框节点")
            false
        } catch (e: Exception) {
            LogManager.e(TAG, "剪贴板粘贴失败: ${e.message}")
            false
        }
    }

    private fun findEditableNodeNearInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val dm = resources.displayMetrics
        val region = com.autoswapeng.app.ocr.MiniProgramRegions.Spelling.INPUT_AREA
            .toPixelRect(dm.widthPixels, dm.heightPixels)

        val q: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        q.add(root)
        val bounds = android.graphics.Rect()
        var found: AccessibilityNodeInfo? = null
        
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            n.getBoundsInScreen(bounds)
            val className = n.className?.toString() ?: ""
            val editable = (n.isEditable) || className.contains("EditText", true)
            if (editable && android.graphics.Rect.intersects(bounds, region)) {
                found = n
                LogManager.d(TAG, "找到可编辑节点: class=${className}, editable=${n.isEditable}, bounds=$bounds")
                break
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
        }
        
        if (found == null) {
            LogManager.w(TAG, "未找到可编辑节点（区域: $region）")
        }
        return found
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
                    tap = { x, y -> tapAwait(x.toFloat(), y.toFloat()) },
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


