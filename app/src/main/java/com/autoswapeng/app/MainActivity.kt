package com.autoswapeng.app

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoswapeng.app.accessibility.AppAccessibilityService
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.overlay.FloatingWindowService
import com.autoswapeng.app.ui.PermissionGuideActivity
import com.autoswapeng.app.ui.SettingsActivity
import com.autoswapeng.app.ui.theme.AppTheme
import com.autoswapeng.app.ui.theme.ThemeManager
import com.autoswapeng.app.ui.theme.ThemeMode
import com.autoswapeng.app.ui.theme.rememberThemeMode
import com.autoswapeng.app.utils.EdgeToEdgeUtils
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    
    // 屏幕录制权限请求
    private val screenCaptureRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                // 将MediaProjection传递给无障碍服务
                AppAccessibilityService.instance?.initScreenCapture(result.resultCode, data)
                LogManager.i("MainActivity", "屏幕录制权限授予成功")
            }
        } else {
            LogManager.w("MainActivity", "屏幕录制权限被拒绝")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化日志管理器
        LogManager.initialize(applicationContext)
        // 初始化主题管理器
        ThemeManager.initialize(applicationContext)
        LogManager.i("MainActivity", "应用启动")
        LogManager.i("MainActivity", "filesDir: ${applicationContext.filesDir.absolutePath}")
        
        // 记录屏幕信息
        val displayMetrics = applicationContext.resources.displayMetrics
        LogManager.i("MainActivity", "屏幕分辨率: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
        LogManager.i("MainActivity", "屏幕DPI: ${displayMetrics.densityDpi}")
        
        // 检查是否需要显示权限引导（仅在首次启动且引导未完成时）
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val guideCompleted = prefs.getBoolean("permission_guide_completed", false)
        
        // 只有在引导未完成时才自动跳转到引导页面
        // 如果用户已经完成过引导（即使跳过了权限设置），就不再自动跳转
        if (!guideCompleted) {
            // 显示权限引导页面
            startActivity(Intent(this, PermissionGuideActivity::class.java))
            finish()
            return
        }
        
        setContent {
            val themeMode = rememberThemeMode()
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemDark
            }
            
            // 设置Edge-to-Edge
            LaunchedEffect(isDarkTheme) {
                EdgeToEdgeUtils.setupEdgeToEdge(this@MainActivity, isDarkTheme)
            }
            
            AppTheme(themeMode = themeMode) {
                MainScreen(
                    context = this,
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRequestScreenCapture = {
                        requestScreenCapturePermission()
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
    
    /**
     * 请求屏幕录制权限
     */
    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureRequest.launch(intent)
        LogManager.i("MainActivity", "请求屏幕录制权限")
    }
    
    override fun onDestroy() {
        LogManager.i("MainActivity", "应用关闭")
        LogManager.close()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    context: ComponentActivity,
    onOpenAccessibility: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AutoSwapEng",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, "主页") },
                    label = { Text("主页") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, "日志") },
                    label = { Text("日志") }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(
                    context = context,
                    onOpenAccessibility = onOpenAccessibility,
                    onRequestScreenCapture = onRequestScreenCapture
                )
                1 -> LogsScreen()
            }
        }
    }
}

@Composable
fun HomeScreen(
    context: ComponentActivity,
    onOpenAccessibility: () -> Unit,
    onRequestScreenCapture: () -> Unit
) {
    var isServiceRunning by remember { mutableStateOf(AppAccessibilityService.isRunning()) }
    var isAutoEnabled by remember { mutableStateOf(AppAccessibilityService.isEnabled()) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasScreenCapture by remember { mutableStateOf(AppAccessibilityService.instance?.isScreenCaptureReady() ?: false) }
    
    // 定期检查服务状态和权限
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            isServiceRunning = AppAccessibilityService.isRunning()
            isAutoEnabled = AppAccessibilityService.isEnabled()
            hasOverlay = Settings.canDrawOverlays(context)
            hasScreenCapture = AppAccessibilityService.instance?.isScreenCaptureReady() ?: false
        }
    }
    
    // 检查是否有缺失的权限
    val missingPermissions = mutableListOf<String>()
    if (!isServiceRunning) missingPermissions.add("无障碍服务")
    if (!hasOverlay) missingPermissions.add("悬浮窗权限")
    if (!hasScreenCapture) missingPermissions.add("OCR权限")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 权限缺失提示卡片
        if (missingPermissions.isNotEmpty()) {
            PermissionWarningCard(
                missingPermissions = missingPermissions,
                onSetupPermissions = {
                    context.startActivity(Intent(context, PermissionGuideActivity::class.java))
                }
            )
        }
        
        // 顶部欢迎卡片
        WelcomeCard(isServiceRunning = isServiceRunning)
        
        // 快速操作部分
        QuickActionsCard(
            isAutoEnabled = isAutoEnabled,
            onToggleAuto = { enabled ->
                AppAccessibilityService.setEnabled(enabled)
                isAutoEnabled = enabled
            },
            isServiceRunning = isServiceRunning
        )
        
        // 权限状态卡片
        PermissionsCard(
            isServiceRunning = isServiceRunning,
            hasOverlay = hasOverlay,
            hasScreenCapture = hasScreenCapture,
            onOpenAccessibility = onOpenAccessibility,
            onRequestOverlay = {
                if (!Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            },
            onRequestScreenCapture = onRequestScreenCapture,
            onStartFloatingWindow = {
                if (Settings.canDrawOverlays(context)) {
                    context.startService(Intent(context, FloatingWindowService::class.java))
                } else {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            }
        )
    }
}

@Composable
fun PermissionWarningCard(
    missingPermissions: List<String>,
    onSetupPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "权限缺失",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "以下权限未配置：${missingPermissions.joinToString("、")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onSetupPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "立即配置",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun WelcomeCard(isServiceRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "👋 欢迎使用",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "专为翻转外语小程序设计的自动化工具",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                StatusChip(
                    text = if (isServiceRunning) "运行中" else "未运行",
                    icon = if (isServiceRunning) Icons.Default.CheckCircle else Icons.Default.Close,
                    isPositive = isServiceRunning
                )
            }
        }
    }
}

@Composable
fun QuickActionsCard(
    isAutoEnabled: Boolean,
    onToggleAuto: (Boolean) -> Unit,
    isServiceRunning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "快速操作",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 自动操作开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自动操作",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isServiceRunning) "开启后自动识别并答题" else "请先启用无障碍服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = isAutoEnabled,
                    onCheckedChange = onToggleAuto,
                    enabled = isServiceRunning
                )
            }
        }
    }
}

@Composable
fun PermissionsCard(
    isServiceRunning: Boolean,
    hasOverlay: Boolean,
    hasScreenCapture: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    onStartFloatingWindow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "权限与功能",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 无障碍服务
            PermissionItem(
                icon = Icons.Default.Settings,
                title = "无障碍服务",
                description = "自动化操作所需",
                isGranted = isServiceRunning,
                buttonText = if (isServiceRunning) "已启用" else "去启用",
                onButtonClick = onOpenAccessibility,
                showButton = !isServiceRunning
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 悬浮窗权限
            PermissionItem(
                icon = Icons.Default.OpenWith,
                title = "悬浮窗权限",
                description = "显示控制面板",
                isGranted = hasOverlay,
                buttonText = if (hasOverlay) "启动悬浮窗" else "去授权",
                onButtonClick = if (hasOverlay) onStartFloatingWindow else onRequestOverlay,
                showButton = true
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // OCR识别权限
            PermissionItem(
                icon = Icons.Default.RemoveRedEye,
                title = "OCR识别权限",
                description = "用于屏幕内容识别",
                isGranted = hasScreenCapture,
                buttonText = if (hasScreenCapture) "已授权" else "去授权",
                onButtonClick = onRequestScreenCapture,
                showButton = !hasScreenCapture
            )
        }
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onButtonClick: () -> Unit,
    showButton: Boolean = true
) {
    val actualIcon = when(title) {
        "无障碍服务" -> Icons.Default.Settings
        "悬浮窗权限" -> Icons.Default.OpenWith
        "OCR识别权限" -> Icons.Default.RemoveRedEye
        else -> icon
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) 
                        MaterialTheme.colorScheme.tertiaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = actualIcon,
                contentDescription = null,
                tint = if (isGranted) 
                    MaterialTheme.colorScheme.onTertiaryContainer 
                else 
                    MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (showButton) {
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isGranted) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    icon: ImageVector,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isPositive) 
                    MaterialTheme.colorScheme.tertiaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPositive) 
                MaterialTheme.colorScheme.onTertiaryContainer 
            else 
                MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isPositive) 
                MaterialTheme.colorScheme.onTertiaryContainer 
            else 
                MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun LogsScreen() {
    val logs by LogManager.logsFlow.collectAsState()
    val minLevel by LogManager.minLevel.collectAsState()
    val listState = rememberLazyListState()
    var showFilterMenu by remember { mutableStateOf(false) }
    val logFilePath = LogManager.getLogFilePath()
    
    // 统计各级别日志数量
    val allLogs = LogManager.getAllLogs()
    val debugCount = allLogs.count { it.level == LogManager.LogEntry.Level.DEBUG }
    val infoCount = allLogs.count { it.level == LogManager.LogEntry.Level.INFO }
    val warnCount = allLogs.count { it.level == LogManager.LogEntry.Level.WARN }
    val errorCount = allLogs.count { it.level == LogManager.LogEntry.Level.ERROR }
    
    // 自动滚动到最新日志
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题和操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "运行日志",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${logs.size}/${allLogs.size} 条 · D:$debugCount I:$infoCount W:$warnCount E:$errorCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 过滤按钮
                FilledTonalButton(
                    onClick = { showFilterMenu = !showFilterMenu },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Filter,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(minLevel.name, fontSize = 12.sp)
                }
                
                FilledTonalButton(
                    onClick = { LogManager.clear() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        // 过滤菜单
        AnimatedVisibility(
            visible = showFilterMenu,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "过滤级别",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LogManager.LogEntry.Level.values().forEach { level ->
                            FilterChip(
                                selected = level == minLevel,
                                onClick = {
                                    LogManager.setMinLevel(level)
                                    showFilterMenu = false
                                },
                                label = { Text(level.name) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        
        // 日志列表
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无日志",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "请启动无障碍服务并使用应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        SelectionContainer {
                            LogItem(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItem(log: LogManager.LogEntry) {
    val textColor = when (log.level) {
        LogManager.LogEntry.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        LogManager.LogEntry.Level.INFO -> MaterialTheme.colorScheme.onSurface
        LogManager.LogEntry.Level.WARN -> Color(0xFFFFAA00)
        LogManager.LogEntry.Level.ERROR -> MaterialTheme.colorScheme.error
    }
    
    Text(
        text = log.format(),
        color = textColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
