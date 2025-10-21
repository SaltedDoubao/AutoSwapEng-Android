package com.autoswapeng.app.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoswapeng.app.MainActivity
import com.autoswapeng.app.accessibility.AppAccessibilityService
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.ui.theme.AppTheme
import com.autoswapeng.app.ui.theme.ThemeManager
import com.autoswapeng.app.ui.theme.ThemeMode
import com.autoswapeng.app.ui.theme.rememberThemeMode
import com.autoswapeng.app.utils.EdgeToEdgeUtils
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay

class PermissionGuideActivity : ComponentActivity() {
    
    private var currentStep by mutableIntStateOf(0)
    
    // 屏幕录制权限请求
    private val screenCaptureRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                AppAccessibilityService.instance?.initScreenCapture(result.resultCode, data)
                LogManager.i("PermissionGuide", "屏幕录制权限授予成功")
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化主题管理器
        ThemeManager.initialize(applicationContext)
        
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
                EdgeToEdgeUtils.setupEdgeToEdge(this@PermissionGuideActivity, isDarkTheme)
            }
            
            AppTheme(themeMode = themeMode) {
                PermissionGuideScreen(
                    currentStep = currentStep,
                    onStepChange = { step -> currentStep = step },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRequestOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    },
                    onRequestScreenCapture = {
                        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        val intent = mediaProjectionManager.createScreenCaptureIntent()
                        screenCaptureRequest.launch(intent)
                    },
                    onComplete = {
                        // 保存已完成引导的标记
                        getSharedPreferences("app_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("permission_guide_completed", true)
                            .apply()
                        
                        // 跳转到主界面
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    hasAccessibilityPermission = {
                        AppAccessibilityService.isRunning()
                    },
                    hasOverlayPermission = {
                        Settings.canDrawOverlays(this)
                    },
                    hasScreenCapturePermission = {
                        AppAccessibilityService.instance?.isScreenCaptureReady() ?: false
                    }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 检查权限状态，可能从设置返回
    }
}

@Composable
fun PermissionGuideScreen(
    currentStep: Int,
    onStepChange: (Int) -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    onComplete: () -> Unit,
    hasAccessibilityPermission: () -> Boolean,
    hasOverlayPermission: () -> Boolean,
    hasScreenCapturePermission: () -> Boolean
) {
    var accessibilityGranted by remember { mutableStateOf(hasAccessibilityPermission()) }
    var overlayGranted by remember { mutableStateOf(hasOverlayPermission()) }
    var screenCaptureGranted by remember { mutableStateOf(hasScreenCapturePermission()) }
    
    // 定期检查权限状态
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            accessibilityGranted = hasAccessibilityPermission()
            overlayGranted = hasOverlayPermission()
            screenCaptureGranted = hasScreenCapturePermission()
        }
    }
    
    val steps = listOf(
        GuideStep(
            title = "欢迎使用 AutoSwapEng",
            description = "专为翻转外语小程序设计的自动化工具\n让我们完成必要的权限设置",
            icon = Icons.Default.Star,
            isWelcome = true
        ),
        GuideStep(
            title = "无障碍服务",
            description = "允许应用自动识别页面内容并执行操作",
            icon = Icons.Default.Settings,
            buttonText = "授权无障碍服务",
            isGranted = { accessibilityGranted },
            onAction = onOpenAccessibility
        ),
        GuideStep(
            title = "悬浮窗权限",
            description = "允许应用显示控制面板，方便随时控制自动化功能",
            icon = Icons.Default.OpenWith,
            buttonText = "授权悬浮窗",
            isGranted = { overlayGranted },
            onAction = onRequestOverlay
        ),
        GuideStep(
            title = "OCR识别权限",
            description = "允许应用识别屏幕内容，用于题目识别和答案匹配",
            icon = Icons.Default.RemoveRedEye,
            buttonText = "授权OCR识别",
            isGranted = { screenCaptureGranted },
            onAction = onRequestScreenCapture
        ),
        GuideStep(
            title = "设置完成！",
            description = "所有权限已配置完成\n点击下方按钮开始使用",
            icon = Icons.Default.CheckCircle,
            isComplete = true
        )
    )
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景渐变
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 进度指示器
                if (currentStep > 0 && currentStep < steps.size - 1) {
                    ProgressIndicator(
                        currentStep = currentStep - 1,
                        totalSteps = steps.size - 2
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                // 内容区域
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    },
                    label = "step_transition"
                ) { step ->
                    GuideStepContent(
                        step = steps[step],
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 底部按钮
                GuideNavigationButtons(
                    currentStep = currentStep,
                    totalSteps = steps.size,
                    canProceed = when (currentStep) {
                        0 -> true
                        1 -> accessibilityGranted
                        2 -> overlayGranted
                        3 -> screenCaptureGranted
                        else -> true
                    },
                    onNext = {
                        if (currentStep < steps.size - 1) {
                            onStepChange(currentStep + 1)
                        } else {
                            onComplete()
                        }
                    },
                    onStepChange = onStepChange
                )
            }
        }
    }
}

data class GuideStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val buttonText: String = "",
    val isGranted: () -> Boolean = { false },
    val onAction: () -> Unit = {},
    val isWelcome: Boolean = false,
    val isComplete: Boolean = false
)

@Composable
fun GuideStepContent(
    step: GuideStep,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = step.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 权限操作按钮
        if (!step.isWelcome && !step.isComplete) {
            val isGranted = step.isGranted()
            
            AnimatedVisibility(
                visible = !isGranted,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Button(
                    onClick = step.onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step.buttonText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            AnimatedVisibility(
                visible = isGranted,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "权限已授予",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressIndicator(
    currentStep: Int,
    totalSteps: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index <= currentStep)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
fun GuideNavigationButtons(
    currentStep: Int,
    totalSteps: Int,
    canProceed: Boolean,
    onNext: () -> Unit,
    onStepChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 主按钮（下一步/完成）
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = canProceed,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (currentStep == totalSteps - 1) "开始使用" else "下一步",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                if (currentStep == totalSteps - 1) Icons.Default.Done else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null
            )
        }
        
        // 跳过按钮（仅在中间步骤显示）
        if (currentStep > 0 && currentStep < totalSteps - 1) {
            TextButton(
                onClick = {
                    // 直接跳到最后一步（完成页面）
                    onStepChange(totalSteps - 1)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "跳过设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

