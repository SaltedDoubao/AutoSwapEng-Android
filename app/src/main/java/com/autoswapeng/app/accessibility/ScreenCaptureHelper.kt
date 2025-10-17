package com.autoswapeng.app.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager
import com.autoswapeng.app.log.LogManager
import com.autoswapeng.app.ocr.OcrEngine
import com.autoswapeng.app.ocr.MiniProgramRegions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

/**
 * 屏幕截图辅助类
 * 用于在无障碍节点无法获取文本时，通过截图+OCR的方式获取文本
 */
class ScreenCaptureHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenCapture"
        private const val ENABLE_DEBUG_SCREENSHOTS = true  // 调试模式：保存截图
    }
    
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val ocrEngine = OcrEngine(context)
    
    var screenWidth: Int = 0
        private set
    var screenHeight: Int = 0
        private set
    private var screenDensity: Int = 0
    
    private var debugScreenshotCounter = 0
    
    /**
     * 初始化媒体投影
     * 需要在获得用户授权后调用
     */
    fun initMediaProjection(projection: MediaProjection) {
        try {
            mediaProjection = projection
            
            // Android 14+ 要求：必须先注册回调
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    LogManager.i(TAG, "MediaProjection 已停止")
                    release()
                }
                
                override fun onCapturedContentResize(width: Int, height: Int) {
                    LogManager.d(TAG, "屏幕尺寸变化: ${width}x${height}")
                }
                
                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    LogManager.d(TAG, "捕获内容可见性变化: $isVisible")
                }
            }, null)
            
            LogManager.i(TAG, "✓ MediaProjection 回调已注册")
            
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            
            val rawWidth = metrics.widthPixels
            val rawHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
            
            LogManager.i(TAG, "========== 屏幕信息 ==========")
            LogManager.i(TAG, "原始分辨率: ${rawWidth}x${rawHeight}")
            LogManager.i(TAG, "DPI: $screenDensity")
            
            // 检测横屏/竖屏
            val isLandscape = rawWidth > rawHeight
            val orientation = if (isLandscape) "横屏" else "竖屏"
            LogManager.i(TAG, "方向: $orientation")
            
            // 确保 screenWidth 是窄边，screenHeight 是长边（竖屏标准）
            if (isLandscape) {
                screenWidth = rawHeight
                screenHeight = rawWidth
                LogManager.w(TAG, "⚠️ 检测到横屏，归一化坐标将基于竖屏: ${screenWidth}x${screenHeight}")
            } else {
                screenWidth = rawWidth
                screenHeight = rawHeight
                LogManager.i(TAG, "竖屏模式，归一化坐标: ${screenWidth}x${screenHeight}")
            }
            
            LogManager.i(TAG, "屏幕比例: ${String.format("%.2f", screenHeight.toFloat() / screenWidth)}")
            LogManager.i(TAG, "===============================")
            
            // 创建ImageReader（使用原始物理尺寸，不交换）
            imageReader = ImageReader.newInstance(
                rawWidth,
                rawHeight,
                PixelFormat.RGBA_8888,
                2
            )
            LogManager.i(TAG, "✓ ImageReader 创建成功 (${rawWidth}x${rawHeight})")
            
            // 创建虚拟显示（使用原始物理尺寸）
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                rawWidth,
                rawHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
            
            if (virtualDisplay != null) {
                LogManager.i(TAG, "✓ VirtualDisplay 创建成功")
                LogManager.i(TAG, "✓ 媒体投影初始化完成")
            } else {
                LogManager.e(TAG, "✗ VirtualDisplay 创建失败")
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "初始化媒体投影失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean {
        return mediaProjection != null && imageReader != null && virtualDisplay != null
    }
    
    /**
     * 截取屏幕区域并识别文本（归一化坐标版本）
     * @param normalizedRegion 归一化区域
     * @param regionName 区域名称（用于调试）
     * @return OCR识别出的文本
     */
    suspend fun captureAndRecognize(
        normalizedRegion: MiniProgramRegions.NormalizedRect,
        regionName: String = "region"
    ): String = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized()) {
                LogManager.w(TAG, "媒体投影未初始化")
                return@withContext ""
            }
            
            val bitmap = captureScreen() ?: return@withContext ""
            
            // 注意：归一化坐标基于竖屏标准(screenWidth x screenHeight)
            // 但bitmap可能是横屏(rawWidth x rawHeight)
            // 需要根据实际bitmap尺寸进行转换
            val pixelRect = normalizedRegion.toPixelRect(screenWidth, screenHeight)
            
            LogManager.d(TAG, "[$regionName] Bitmap: ${bitmap.width}x${bitmap.height}, 区域: (${pixelRect.left},${pixelRect.top})-(${pixelRect.right},${pixelRect.bottom}), 尺寸: ${pixelRect.width()}x${pixelRect.height()}")
            
            // 调试：保存全屏截图（每10次保存一次）
            if (ENABLE_DEBUG_SCREENSHOTS && debugScreenshotCounter % 7 == 1) {
                saveDebugScreenshot(bitmap, "fullscreen")
            }
            
            val croppedBitmap = cropBitmap(bitmap, pixelRect)
            
            // 调试：保存裁剪后的截图（每个区域都保存）
            if (ENABLE_DEBUG_SCREENSHOTS) {
                saveDebugScreenshot(croppedBitmap, regionName.replace("-", "_").replace("区域", "region"))
            }
            
            val texts = ocrEngine.recognize(croppedBitmap)
            val result = texts.joinToString(" ")
            
            bitmap.recycle()
            croppedBitmap.recycle()
            
            LogManager.d(TAG, "[$regionName] OCR: '$result'")
            result
        } catch (e: Exception) {
            LogManager.e(TAG, "截图识别失败: ${e.message}")
            e.printStackTrace()
            ""
        }
    }
    
    /**
     * 保存调试截图
     */
    private fun saveDebugScreenshot(bitmap: Bitmap, prefix: String) {
        try {
            val screenshotDir = context.getExternalFilesDir("screenshots")
            if (screenshotDir == null || !screenshotDir.exists()) {
                screenshotDir?.mkdirs()
            }
            
            debugScreenshotCounter++
            val timestamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
            val file = File(screenshotDir, "${prefix}_${timestamp}_${debugScreenshotCounter}.png")
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            LogManager.i(TAG, "调试截图已保存: ${file.absolutePath}")
        } catch (e: Exception) {
            LogManager.e(TAG, "保存调试截图失败: ${e.message}")
        }
    }
    
    /**
     * 截取屏幕区域并识别文本（像素坐标版本）
     * @param region 屏幕区域（像素坐标）
     * @return OCR识别出的文本列表
     */
    suspend fun captureAndRecognize(region: Rect): List<String> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized()) {
                LogManager.w(TAG, "媒体投影未初始化")
                return@withContext emptyList()
            }
            
            val bitmap = captureScreen() ?: return@withContext emptyList()
            val croppedBitmap = cropBitmap(bitmap, region)
            val texts = ocrEngine.recognize(croppedBitmap)
            bitmap.recycle()
            croppedBitmap.recycle()
            texts
        } catch (e: Exception) {
            LogManager.e(TAG, "截图识别失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 截取整个屏幕
     */
    private fun captureScreen(): Bitmap? {
        try {
            // 清理旧图像
            try {
                var oldImage = imageReader?.acquireLatestImage()
                while (oldImage != null) {
                    oldImage.close()
                    oldImage = imageReader?.acquireLatestImage()
                }
            } catch (e: Exception) {
                // 忽略清理错误
            }
            
            // 等待新图像
            Thread.sleep(200)
            
            // 获取最新图像
            var image: Image? = null
            for (retry in 1..5) {
                image = imageReader?.acquireLatestImage()
                if (image != null) break
                
                if (retry < 5) {
                    LogManager.d(TAG, "等待截图 $retry/5")
                    Thread.sleep(150)
                }
            }
            
            if (image == null) {
                LogManager.w(TAG, "无法获取截图（已等待5次）")
                return null
            }
            
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            
            // 创建bitmap
            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            
            // 裁剪多余的padding
            return if (rowPadding == 0) {
                bitmap
            } else {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                bitmap.recycle()
                cropped
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "截图失败: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 裁剪位图到指定区域
     */
    private fun cropBitmap(source: Bitmap, region: Rect): Bitmap {
        val width = source.width
        val height = source.height
        
        val left = region.left.coerceIn(0, width)
        val top = region.top.coerceIn(0, height)
        val right = region.right.coerceIn(0, width)
        val bottom = region.bottom.coerceIn(0, height)
        
        val cropWidth = (right - left).coerceAtLeast(1)
        val cropHeight = (bottom - top).coerceAtLeast(1)
        
        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            LogManager.i(TAG, "资源释放完成")
        } catch (e: Exception) {
            LogManager.e(TAG, "释放资源失败: ${e.message}")
        }
    }
}

