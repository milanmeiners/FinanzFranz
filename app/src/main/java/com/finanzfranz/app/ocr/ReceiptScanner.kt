package com.finanzfranz.app.ocr

import android.content.Context
import android.net.Uri
import com.finanzfranz.app.domain.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs ML Kit text recognition on a photo, fully on the device. */
object ReceiptScanner {
    suspend fun scan(context: Context, uri: Uri): List<OcrLine> = suspendCancellableCoroutine { cont ->
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            cont.resumeWithException(e); return@suspendCancellableCoroutine
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { block ->
                    block.lines.map { l ->
                        val b = l.boundingBox
                        OcrLine(l.text, b?.top ?: 0, b?.bottom ?: 0, b?.left ?: 0, b?.right ?: 0)
                    }
                }
                recognizer.close()
                if (cont.isActive) cont.resume(lines)
            }
            .addOnFailureListener { e ->
                recognizer.close()
                if (cont.isActive) cont.resumeWithException(e)
            }
    }
}
