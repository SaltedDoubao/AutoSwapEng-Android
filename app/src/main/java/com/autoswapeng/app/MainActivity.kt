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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoswapeng.app.accessibility.AppAccessibilityService
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.overlay.FloatingWindowService
import com.autoswapeng.app.ui.theme.AppTheme

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
        LogManager.i("MainActivity", "应用启动")
        LogManager.i("MainActivity", "filesDir: ${applicationContext.filesDir.absolutePath}")
        
        // 记录屏幕信息
        val displayMetrics = applicationContext.resources.displayMetrics
        LogManager.i("MainActivity", "屏幕分辨率: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
        LogManager.i("MainActivity", "屏幕DPI: ${displayMetrics.densityDpi}")
        
        setContent {
            AppTheme {
                MainScreen(
                    context = this,
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRequestScreenCapture = {
                        requestScreenCapturePermission()
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

@Composable
fun MainScreen(
    context: ComponentActivity,
    onOpenAccessibility: () -> Unit,
    onRequestScreenCapture: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        var selectedTab by remember { mutableIntStateOf(0) }
        
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("主页") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("日志") }
                )
            }
            
            when (selectedTab) {
                0 -> HomeScreen(
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
                    onStartFloatingWindow = {
                        if (Settings.canDrawOverlays(context)) {
                            context.startService(Intent(context, FloatingWindowService::class.java))
                        } else {
                            // 如果没有权限，跳转到授权页面
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    },
                    hasOverlayPermission = {
                        Settings.canDrawOverlays(context)
                    },
                    onRequestScreenCapture = onRequestScreenCapture
                )
                1 -> LogsScreen()
            }
        }
    }
}

@Composable
fun HomeScreen(
    onOpenAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    onStartFloatingWindow: () -> Unit,
    hasOverlayPermission: () -> Boolean,
    onRequestScreenCapture: () -> Unit
) {
    var isServiceRunning by remember { mutableStateOf(AppAccessibilityService.isRunning()) }
    var isAutoEnabled by remember { mutableStateOf(AppAccessibilityService.isEnabled()) }
    var hasOverlay by remember { mutableStateOf(hasOverlayPermission()) }
    var hasScreenCapture by remember { mutableStateOf(AppAccessibilityService.instance?.isScreenCaptureReady() ?: false) }
    
    // 定期检查服务状态和权限（降低频率）
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)  // 2秒检查一次
            isServiceRunning = AppAccessibilityService.isRunning()
            isAutoEnabled = AppAccessibilityService.isEnabled()
            hasOverlay = hasOverlayPermission()
            hasScreenCapture = AppAccessibilityService.instance?.isScreenCaptureReady() ?: false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AutoSwapEng for Android",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 服务状态显示
        Text(
            text = if (isServiceRunning) "✓ 无障碍服务已连接" else "✗ 无障碍服务未运行",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isServiceRunning) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 自动操作开关
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "启用自动操作",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = isAutoEnabled,
                onCheckedChange = { enabled ->
                    AppAccessibilityService.setEnabled(enabled)
                    isAutoEnabled = enabled
                },
                enabled = isServiceRunning
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onOpenAccessibility) {
            Text("打开无障碍设置")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 悬浮窗权限状态
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (hasOverlay) "✓" else "✗",
                style = MaterialTheme.typography.bodyLarge,
                color = if (hasOverlay) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error
            )
            Text(
                text = if (hasOverlay) "悬浮窗权限已授予" else "悬浮窗权限未授予",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        if (!hasOverlay) {
            Button(onClick = onRequestOverlay) {
                Text("授权悬浮窗")
            }
        } else {
            Button(onClick = onStartFloatingWindow) {
                Text("启动悬浮窗")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 屏幕录制权限状态（用于OCR识别）
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (hasScreenCapture) "✓" else "✗",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hasScreenCapture) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (hasScreenCapture) "OCR权限已授予" else "OCR权限未授予",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 显示OCR详细状态（调试用）
            if (!hasScreenCapture) {
                val ocrDetails = AppAccessibilityService.instance?.getOcrStatusDetails() ?: "服务未运行"
                Text(
                    text = ocrDetails,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        if (!hasScreenCapture) {
            Button(onClick = onRequestScreenCapture) {
                Text("授权OCR识别")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 使用说明
        Text(
            text = "使用说明：",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "1. 先开启无障碍服务\n2. 授权悬浮窗权限\n3. 授权OCR识别权限（用于微信小程序）\n4. 启动悬浮窗进行控制\n5. 专门支持翻转外语小程序",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp)
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
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "运行日志 (${logs.size}/${allLogs.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "D:$debugCount I:$infoCount W:$warnCount E:$errorCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 过滤按钮
                Button(
                    onClick = { showFilterMenu = !showFilterMenu },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("过滤: ${minLevel.name}", fontSize = 12.sp)
                }
                
                Button(
                    onClick = { LogManager.clear() },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("清空", fontSize = 12.sp)
                }
            }
        }
        
        // 日志文件路径显示
        if (logFilePath != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                SelectionContainer {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "日志文件路径（可通过文件管理器访问）：",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = logFilePath,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = "提示: 日志同时保存在内部和外部存储",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 8.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        
        // 过滤菜单
        if (showFilterMenu) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "选择最小日志级别：",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LogManager.LogEntry.Level.values().forEach { level ->
                            Button(
                                onClick = {
                                    LogManager.setMinLevel(level)
                                    showFilterMenu = false
                                },
                                modifier = Modifier.height(32.dp),
                                colors = if (level == minLevel) {
                                    ButtonDefaults.buttonColors()
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                }
                            ) {
                                Text(level.name, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        
        // 日志列表
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无日志\n请启动无障碍服务并使用应用",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
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
        LogManager.LogEntry.Level.DEBUG -> Color(0xFF808080)
        LogManager.LogEntry.Level.INFO -> Color(0xFFFFFFFF)
        LogManager.LogEntry.Level.WARN -> Color(0xFFFFAA00)
        LogManager.LogEntry.Level.ERROR -> Color(0xFFFF5555)
    }
    
    Text(
        text = log.format(),
        color = textColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}


