package com.autoswapeng.app.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine(context: Context) {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    suspend fun recognize(bitmap: Bitmap): List<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.textBlocks.map { it.text }
    }
}

// Simple Task await extension for ML Kit Task

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}


