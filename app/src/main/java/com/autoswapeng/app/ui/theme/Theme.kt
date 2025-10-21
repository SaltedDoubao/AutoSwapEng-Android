package com.autoswapeng.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Grok风格深色配色方案
private val GrokDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00D9FF), // 明亮的青色 - Grok的主要强调色
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF004D5C),
    onPrimaryContainer = Color(0xFF9EF0FF),
    
    secondary = Color(0xFF8B5CF6), // 紫色 - 次要强调色
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF4C1D95),
    onSecondaryContainer = Color(0xFFDDD6FE),
    
    tertiary = Color(0xFF10B981), // 绿色 - 成功状态
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF065F46),
    onTertiaryContainer = Color(0xFFA7F3D0),
    
    error = Color(0xFFEF4444), // 红色 - 错误状态
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    
    background = Color(0xFF0A0A0A), // 深黑色背景
    onBackground = Color(0xFFE5E5E5),
    
    surface = Color(0xFF1A1A1A), // 稍浅的表面颜色
    onSurface = Color(0xFFE5E5E5),
    surfaceVariant = Color(0xFF262626), // 卡片背景
    onSurfaceVariant = Color(0xFFB4B4B4),
    
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF2A2A2A),
)

// Grok风格浅色配色方案
private val GrokLightColorScheme = lightColorScheme(
    primary = Color(0xFF0099CC), // 深青色 - 主要强调色
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB3E5FC),
    onPrimaryContainer = Color(0xFF003D4D),
    
    secondary = Color(0xFF7C3AED), // 紫色 - 次要强调色
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9D5FF),
    onSecondaryContainer = Color(0xFF3B0764),
    
    tertiary = Color(0xFF059669), // 绿色 - 成功状态
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    
    error = Color(0xFFDC2626), // 红色 - 错误状态
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    
    background = Color(0xFFF8FAFC), // 浅灰背景
    onBackground = Color(0xFF1E293B),
    
    surface = Color(0xFFFFFFFF), // 白色表面
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F9), // 浅灰卡片背景
    onSurfaceVariant = Color(0xFF64748B),
    
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isSystemInDarkTheme = isSystemInDarkTheme()
    
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> GrokLightColorScheme
        ThemeMode.DARK -> GrokDarkColorScheme
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme) GrokDarkColorScheme else GrokLightColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}


