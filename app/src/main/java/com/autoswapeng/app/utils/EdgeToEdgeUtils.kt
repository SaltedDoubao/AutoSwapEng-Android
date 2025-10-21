package com.autoswapeng.app.utils

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Edge-to-Edge工具类
 * 用于处理全面屏、刘海屏、挖孔屏等适配
 */
object EdgeToEdgeUtils {
    
    /**
     * 启用Edge-to-Edge模式
     */
    fun setupEdgeToEdge(activity: Activity, isDarkTheme: Boolean = true) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        
        val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        
        // 设置状态栏和导航栏样式
        windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
        windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
        
        // 设置刘海屏/挖孔屏支持
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    
    /**
     * 获取状态栏高度
     */
    @Composable
    fun getStatusBarHeight(): Dp {
        val insets = WindowInsets.systemBars
        val density = LocalDensity.current
        return with(density) { insets.getTop(this).toDp() }
    }
    
    /**
     * 获取导航栏高度
     */
    @Composable
    fun getNavigationBarHeight(): Dp {
        val insets = WindowInsets.systemBars
        val density = LocalDensity.current
        return with(density) { insets.getBottom(this).toDp() }
    }
    
    /**
     * 判断是否为全面屏手势导航
     */
    fun isGestureNavigation(activity: Activity): Boolean {
        val insets = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .systemBarsBehavior
        return insets == WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }
    
    /**
     * 获取显示切口（刘海/挖孔）信息
     */
    fun getDisplayCutoutSafeInsets(activity: Activity): SafeInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cutout = activity.window.decorView.rootWindowInsets?.displayCutout
            if (cutout != null) {
                return SafeInsets(
                    top = cutout.safeInsetTop,
                    bottom = cutout.safeInsetBottom,
                    left = cutout.safeInsetLeft,
                    right = cutout.safeInsetRight
                )
            }
        }
        return SafeInsets()
    }
    
    data class SafeInsets(
        val top: Int = 0,
        val bottom: Int = 0,
        val left: Int = 0,
        val right: Int = 0
    )
}

