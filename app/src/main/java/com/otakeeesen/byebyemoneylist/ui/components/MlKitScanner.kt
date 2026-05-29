package com.otakeeesen.byebyemoneylist.ui.components

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MlKitScanner : ReceiptParser {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun parse(bitmap: Bitmap): ScannedReceipt {
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
