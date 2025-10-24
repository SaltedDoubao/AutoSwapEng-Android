package com.autoswapeng.app.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.autoswapeng.app.accessibility.AppAccessibilityService
import com.autoswapeng.app.utils.EdgeToEdgeUtils
import kotlin.math.roundToInt

class FloatingWindowService : Service(), 
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private var windowManager: WindowManager? = null
    private var floatingView: ComposeView? = null
    
    override val viewModelStore: ViewModelStore = ViewModelStore()
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createFloatingWindow()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    private fun createFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 获取安全区域信息（避开刘海/挖孔）
        // 注意：Service不是Activity，这里使用默认值
        val safeInsets = EdgeToEdgeUtils.SafeInsets()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 100  // 默认在右侧
            // 避开顶部刘海/挖孔区域
            val topSafeArea = maxOf(safeInsets.top, 100)
            y = topSafeArea + screenHeight / 6
        }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            
            setContent {
                FloatingWindowContent(
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    onMove = { offsetX, offsetY ->
                        val newX = params.x + offsetX.roundToInt()
                        val newY = params.y + offsetY.roundToInt()
                        
                        // 限制在屏幕范围内，留出悬浮球的空间（48dp），并避开刘海区域
                        val fabSize = (48 * displayMetrics.density).toInt()
                        val topSafeArea = maxOf(safeInsets.top, 50)
                        val bottomSafeArea = maxOf(safeInsets.bottom, 0)
                        params.x = newX.coerceIn(-fabSize / 2, screenWidth - fabSize / 2)
                        params.y = newY.coerceIn(topSafeArea, screenHeight - fabSize * 2 - bottomSafeArea)
                        
                        windowManager?.updateViewLayout(this, params)
                    },
                    onMoveEnd = { 
                        // 松手后自动贴边
                        val fabSize = (48 * displayMetrics.density).toInt()
                        val centerX = params.x + fabSize / 2
                        
                        // 判断靠近哪一边
                        val targetX = if (centerX < screenWidth / 2) {
                            -fabSize / 2  // 贴左边
                        } else {
                            screenWidth - fabSize / 2  // 贴右边
                        }
                        
                        // 使用动画贴边
                        animateToPosition(params, targetX, params.y, windowManager, this)
                    },
                    onClose = {
                        stopSelf()
                    }
                )
            }
        }

        windowManager?.addView(floatingView, params)
    }
    
    private fun animateToPosition(
        params: WindowManager.LayoutParams,
        targetX: Int,
        targetY: Int,
        windowManager: WindowManager?,
        view: ComposeView
    ) {
        val startX = params.x
        val startY = params.y
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                params.x = (startX + (targetX - startX) * fraction).toInt()
                params.y = (startY + (targetY - startY) * fraction).toInt()
                windowManager?.updateViewLayout(view, params)
            }
        }
        animator.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        floatingView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingWindowContent(
    screenWidth: Int,
    screenHeight: Int,
    onMove: (Float, Float) -> Unit,
    onMoveEnd: () -> Unit,
    onClose: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isServiceEnabled by remember { mutableStateOf(AppAccessibilityService.isEnabled()) }
    var isServiceRunning by remember { mutableStateOf(AppAccessibilityService.isRunning()) }
    var isTestMode by remember { mutableStateOf(AppAccessibilityService.isTestMode()) }
    var learnedCount by remember { mutableIntStateOf(AppAccessibilityService.getLearnedCount()) }
    var isDragging by remember { mutableStateOf(false) }
    var hasOcrPermission by remember { mutableStateOf(AppAccessibilityService.instance?.isScreenCaptureReady() ?: false) }
    var isSpellingRunning by remember { mutableStateOf(false) }
    var isSelectionRunning by remember { mutableStateOf(false) }
    var isListeningRunning by remember { mutableStateOf(false) }
    
    val currentMode = if (isTestMode) "测试模式" else "学习模式"

    // 定期更新状态（降低频率）
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)  // 2秒检查一次
            isServiceEnabled = AppAccessibilityService.isEnabled()
            isServiceRunning = AppAccessibilityService.isRunning()
            isTestMode = AppAccessibilityService.isTestMode()
            learnedCount = AppAccessibilityService.getLearnedCount()
            hasOcrPermission = AppAccessibilityService.instance?.isScreenCaptureReady() ?: false
            isSpellingRunning = AppAccessibilityService.isSpellingRunning()
            isSelectionRunning = AppAccessibilityService.isSelectionRunning()
            isListeningRunning = AppAccessibilityService.isListeningRunning()
        }
    }

    // 优化的动画配置 - 使用Material Design推荐的动画
    val expandTransition = updateTransition(targetState = isExpanded, label = "expand")

    val fabRotation by expandTransition.animateFloat(
        label = "fabRotation",
        transitionSpec = { 
            tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        }
    ) { expanded ->
        if (expanded) 45f else 0f  // 从+号变成×号（45度旋转）
    }
    
    val fabScale by expandTransition.animateFloat(
        label = "fabScale",
        transitionSpec = { 
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        }
    ) { expanded ->
        if (expanded) 0.9f else 1f  // 展开时略微缩小
    }
    
    val fabElevation by expandTransition.animateDp(
        label = "fabElevation",
        transitionSpec = {
            tween(durationMillis = 200)
        }
    ) { expanded ->
        if (expanded) 12.dp else 6.dp  // 展开时增加阴影
    }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    isDragging = true
                },
                onDragEnd = {
                    isDragging = false
                    onMoveEnd()
                },
                onDragCancel = {
                    isDragging = false
                    onMoveEnd()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x, dragAmount.y)
                }
            )
        }
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 扩展菜单 - 使用Material Design推荐的动画
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    )
                ) + expandVertically(
                    animationSpec = tween(
                        durationMillis = 250,
                        easing = FastOutSlowInEasing
                    ),
                    expandFrom = Alignment.Bottom
                ) + scaleIn(
                    animationSpec = tween(
                        durationMillis = 250,
                        easing = FastOutSlowInEasing
                    ),
                    initialScale = 0.8f
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = 150,
                        easing = FastOutSlowInEasing
                    )
                ) + shrinkVertically(
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    ),
                    shrinkTowards = Alignment.Bottom
                ) + scaleOut(
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    ),
                    targetScale = 0.8f
                )
            ) {
                Card(
                    modifier = Modifier
                        .width(200.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 状态显示
                        Text(
                            text = if (isServiceRunning) "✓ 服务运行中" else "✗ 服务未运行",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceRunning) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )

                        HorizontalDivider()

                        // 启用/禁用开关
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isServiceRunning) {
                                    AppAccessibilityService.setEnabled(!isServiceEnabled)
                                    isServiceEnabled = !isServiceEnabled
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "启用自动",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp
                            )
                            Switch(
                                checked = isServiceEnabled,
                                onCheckedChange = {
                                    AppAccessibilityService.setEnabled(it)
                                    isServiceEnabled = it
                                },
                                enabled = isServiceRunning,
                                modifier = Modifier.height(24.dp)
                            )
                        }

                        // 学习计数
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "已学习",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "$learnedCount 个",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 11.sp
                            )
                        }

                        // 模式切换
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isServiceRunning) {
                                    AppAccessibilityService.setTestMode(!isTestMode)
                                    isTestMode = !isTestMode
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "模式",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentMode,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isTestMode) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.secondary,
                                    fontSize = 11.sp
                                )
                                Switch(
                                    checked = isTestMode,
                                    onCheckedChange = {
                                        AppAccessibilityService.setTestMode(it)
                                        isTestMode = it
                                    },
                                    enabled = isServiceRunning,
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }

                        HorizontalDivider()
                        
                        // 题型选择
                        Text(
                            text = "题型选择",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        
                        // TODO: 题型按钮功能待实现
                        
                        // 拼写题按钮
                        Button(
                            onClick = {
                                isExpanded = false
                                if (!isSpellingRunning) {
                                    AppAccessibilityService.startSpelling()
                                } else {
                                    AppAccessibilityService.stopSpelling()
                                }
                                isSpellingRunning = AppAccessibilityService.isSpellingRunning()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isServiceRunning && hasOcrPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = if (!hasOcrPermission) "⚠️ 需授权OCR" else if (!isSpellingRunning) "✍️ 开始拼写" else "⏹️ 停止拼写",
                                fontSize = 13.sp
                            )
                        }
                        
                        // 选择题按钮
                        Button(
                            onClick = {
                                isExpanded = false
                                if (!isSelectionRunning) {
                                    AppAccessibilityService.startSelection()
                                } else {
                                    AppAccessibilityService.stopSelection()
                                }
                                isSelectionRunning = AppAccessibilityService.isSelectionRunning()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isServiceRunning && hasOcrPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                text = if (!hasOcrPermission) "⚠️ 需授权OCR" else if (!isSelectionRunning) "📚 开始学习" else "⏹️ 停止学习",
                                fontSize = 13.sp
                            )
                        }
                        
                        // 听力题按钮
                        Button(
                            onClick = {
                                isExpanded = false
                                if (!isListeningRunning) {
                                    AppAccessibilityService.startListening()
                                } else {
                                    AppAccessibilityService.stopListening()
                                }
                                isListeningRunning = AppAccessibilityService.isListeningRunning()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isServiceRunning && hasOcrPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text(
                                text = if (!hasOcrPermission) "⚠️ 需授权OCR" else if (!isListeningRunning) "🎧 开始听力" else "⏹️ 停止听力",
                                fontSize = 13.sp
                            )
                        }

                        HorizontalDivider()

                        // Debug 工具
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(
                                onClick = {
                                    AppAccessibilityService.debugDetectNodes()
                                },
                                enabled = isServiceRunning
                            ) {
                                Text(
                                    text = "🔍 节点",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            
                            TextButton(
                                onClick = {
                                    val details = AppAccessibilityService.instance?.getOcrStatusDetails() ?: "未知"
                                    com.autoswapeng.app.log.LogManager.i("FloatingWindow", "OCR状态:\n$details")
                                },
                                enabled = isServiceRunning
                            ) {
                                Text(
                                    text = "📊 OCR",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        // 关闭按钮
                        TextButton(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "关闭悬浮窗",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 主按钮（FAB）- 更小尺寸48dp
            FloatingActionButton(
                onClick = {
                    if (!isDragging) {
                        isExpanded = !isExpanded
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
                    .shadow(
                        elevation = if (isDragging) 16.dp else fabElevation,
                        shape = CircleShape
                    ),
                containerColor = if (isServiceEnabled && isServiceRunning)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = fabElevation,
                    pressedElevation = 12.dp
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // 使用旋转动画的图标（+ 旋转45度变成 ×）
                    Text(
                        text = "+",
                        fontSize = 24.sp,
                        color = if (isServiceEnabled && isServiceRunning)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = fabRotation
                        }
                    )
                }
            }
        }
    }
}

