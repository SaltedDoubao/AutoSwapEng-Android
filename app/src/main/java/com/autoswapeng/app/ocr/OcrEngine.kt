package com.autoswapeng.app.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("UNUSED_PARAMETER")
class OcrEngine(context: Context) {
    // 创建两个识别器：拉丁文（英文）和中文
    // 使用默认的拉丁文识别器（支持英文等拉丁字母语言）
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * 识别图片中的文字
     * @param bitmap 待识别的位图
     * @param preferChinese 是否优先使用中文识别器（默认false，优先英文）
     * @return 识别出的文本列表
     */
    suspend fun recognize(bitmap: Bitmap, preferChinese: Boolean = false): List<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        // 根据需求选择识别器
        val recognizer = if (preferChinese) chineseRecognizer else latinRecognizer
        
        try {
            val result = recognizer.process(image).await()
            val texts = result.textBlocks.map { it.text }
            
            // 如果主识别器没有结果，尝试备用识别器
            if (texts.isEmpty() || texts.all { it.isBlank() }) {
                val fallbackRecognizer = if (preferChinese) latinRecognizer else chineseRecognizer
                val fallbackResult = fallbackRecognizer.process(image).await()
                return fallbackResult.textBlocks.map { it.text }
            }
            
            return texts
        } catch (e: Exception) {
            // 发生错误时尝试备用识别器
            val fallbackRecognizer = if (preferChinese) latinRecognizer else chineseRecognizer
            val fallbackResult = fallbackRecognizer.process(image).await()
            return fallbackResult.textBlocks.map { it.text }
        }
    }
    
    /**
     * 同时使用两个识别器并合并结果（用于混合内容）
     */
    suspend fun recognizeMixed(bitmap: Bitmap): List<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        return try {
            // 并行执行两个识别器
            val latinResult = latinRecognizer.process(image).await()
            val chineseResult = chineseRecognizer.process(image).await()
            
            // 合并结果，去重
            val allTexts = mutableSetOf<String>()
            allTexts.addAll(latinResult.textBlocks.map { it.text })
            allTexts.addAll(chineseResult.textBlocks.map { it.text })
            
            allTexts.filter { it.isNotBlank() }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// Simple Task await extension for ML Kit Task

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}


