package com.otakeeesen.byebyemoneylist.ui.components.scanner

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MlKitScanner : ReceiptParser {
    // Created lazily so a failed ML Kit initialization is caught by parse()'s
    // try/catch instead of crashing when the scanner is constructed.
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun parse(bitmap: Bitmap, categories: List<String>, stores: List<String>): ScannedReceipt {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()
            val total = ReceiptScanner.extractTotal(visionText)
            ScannedReceipt(totalSum = total)
        } catch (e: Exception) {
            ScannedReceipt(errorMessage = e.message ?: "ML Kit Error")
        }
    }
}
