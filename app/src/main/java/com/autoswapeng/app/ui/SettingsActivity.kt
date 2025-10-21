package com.autoswapeng.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.autoswapeng.app.accessibility.AppAccessibilityService
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.ui.theme.AppTheme
import com.autoswapeng.app.ui.theme.ThemeManager
import com.autoswapeng.app.ui.theme.ThemeMode
import com.autoswapeng.app.ui.theme.rememberThemeMode
import com.autoswapeng.app.utils.EdgeToEdgeUtils
import androidx.compose.foundation.isSystemInDarkTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                EdgeToEdgeUtils.setupEdgeToEdge(this@SettingsActivity, isDarkTheme)
            }
            
            AppTheme(themeMode = themeMode) {
                SettingsScreen(
                    context = this,
                    onBack = { finish() },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenOverlaySettings = {
                        if (!Settings.canDrawOverlays(this)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        }
                    },
                    onOpenPermissionGuide = {
                        // 清除引导完成标记
                        getSharedPreferences("app_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("permission_guide_completed", false)
                            .apply()
                        
                        // 跳转到权限引导
                        startActivity(Intent(this, PermissionGuideActivity::class.java))
                        finish()
                    },
                    hasOverlayPermission = {
                        Settings.canDrawOverlays(this)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    context: ComponentActivity,
    onBack: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenPermissionGuide: () -> Unit,
    hasOverlayPermission: () -> Boolean
) {
    var isServiceRunning by remember { mutableStateOf(AppAccessibilityService.isRunning()) }
    var isAutoEnabled by remember { mutableStateOf(AppAccessibilityService.isEnabled()) }
    var hasOverlay by remember { mutableStateOf(hasOverlayPermission()) }
    var hasScreenCapture by remember { mutableStateOf(AppAccessibilityService.instance?.isScreenCaptureReady() ?: false) }
    
    // 定期检查状态
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            isServiceRunning = AppAccessibilityService.isRunning()
            isAutoEnabled = AppAccessibilityService.isEnabled()
            hasOverlay = hasOverlayPermission()
            hasScreenCapture = AppAccessibilityService.instance?.isScreenCaptureReady() ?: false
        }
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 权限设置部分
            SettingsSectionCard(title = "权限设置") {
                // 无障碍服务
                PermissionSettingItem(
                    icon = Icons.Default.Settings,
                    title = "无障碍服务",
                    description = "自动化操作所需",
                    isGranted = isServiceRunning,
                    onClick = onOpenAccessibilitySettings
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // 悬浮窗权限
                PermissionSettingItem(
                    icon = Icons.Default.OpenWith,
                    title = "悬浮窗权限",
                    description = "显示控制面板",
                    isGranted = hasOverlay,
                    onClick = onOpenOverlaySettings
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // OCR识别权限
                PermissionSettingItem(
                    icon = Icons.Default.RemoveRedEye,
                    title = "OCR识别权限",
                    description = "用于屏幕内容识别",
                    isGranted = hasScreenCapture,
                    onClick = { /* 将在主界面申请 */ }
                )
            }
            
            // 功能设置部分
            SettingsSectionCard(title = "功能设置") {
                // 自动操作开关
                SwitchSettingItem(
                    icon = Icons.Default.PlayArrow,
                    title = "启用自动操作",
                    description = "自动识别并答题",
                    checked = isAutoEnabled,
                    onCheckedChange = { enabled ->
                        AppAccessibilityService.setEnabled(enabled)
                        isAutoEnabled = enabled
                    },
                    enabled = isServiceRunning
                )
            }
            
            // 外观设置部分
            SettingsSectionCard(title = "外观") {
                ThemeSettingItem(context = context)
            }
            
            // 其他设置
            SettingsSectionCard(title = "其他") {
                ActionSettingItem(
                    icon = Icons.Default.Refresh,
                    title = "重新设置权限",
                    description = "重新进入权限引导流程",
                    onClick = onOpenPermissionGuide
                )
            }
            
            // 关于部分
            SettingsSectionCard(title = "关于") {
                InfoSettingItem(
                    icon = Icons.Default.Info,
                    title = "版本",
                    value = "0.2.1"
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                InfoSettingItem(
                    icon = Icons.Default.Description,
                    title = "应用说明",
                    value = "专为翻转外语小程序设计"
                )
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun PermissionSettingItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isGranted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        StatusBadge(isGranted = isGranted)
    }
}

@Composable
fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun InfoSettingItem(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ActionSettingItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .then(Modifier) // 旋转180度使箭头向右
        )
    }
}

@Composable
fun ThemeSettingItem(context: ComponentActivity) {
    val currentTheme = rememberThemeMode()
    var showDialog by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showDialog = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "主题",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = ThemeManager.getThemeModeName(currentTheme),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择主题") },
            text = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    ThemeManager.setThemeMode(context, mode)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = {
                                    ThemeManager.setThemeMode(context, mode)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = ThemeManager.getThemeModeName(mode),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun StatusBadge(isGranted: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isGranted) 
                    MaterialTheme.colorScheme.tertiaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = if (isGranted) "已授权" else "未授权",
            style = MaterialTheme.typography.labelSmall,
            color = if (isGranted) 
                MaterialTheme.colorScheme.onTertiaryContainer 
            else 
                MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

