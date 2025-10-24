package com.autoswapeng.app.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 主题模式
 */
enum class ThemeMode {
    LIGHT,      // 浅色
    DARK,       // 深色
    SYSTEM      // 跟随系统
}

/**
 * 主题管理器
 */
object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode
    
    /**
     * 初始化主题设置
     */
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        _themeMode.value = ThemeMode.valueOf(savedMode ?: ThemeMode.SYSTEM.name)
    }
    
    /**
     * 设置主题模式
     */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
    
    /**
     * 获取当前主题模式
     */
    fun getThemeMode(): ThemeMode = _themeMode.value
    
    /**
     * 获取主题显示名称
     */
    fun getThemeModeName(mode: ThemeMode): String {
        return when (mode) {
            ThemeMode.LIGHT -> "浅色"
            ThemeMode.DARK -> "深色"
            ThemeMode.SYSTEM -> "跟随系统"
        }
    }
}

/**
 * 在Compose中使用主题模式
 */
@Composable
fun rememberThemeMode(): ThemeMode {
    val themeMode by ThemeManager.themeMode.collectAsState()
    return themeMode
}

