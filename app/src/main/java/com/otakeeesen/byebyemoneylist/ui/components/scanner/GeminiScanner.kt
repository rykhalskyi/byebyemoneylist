package com.otakeeesen.byebyemoneylist.ui.components.scanner

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class GeminiScanner(
    private val apiKey: String,
    private val readTimeoutSeconds: Int = 60
) : ReceiptParser {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parse(bitmap: Bitmap, categories: List<String>): ScannedReceipt {
        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey,
            requestOptions = RequestOptions(timeout = readTimeoutSeconds.seconds)
        )

        return try {
            val categoryListString = if (categories.isNotEmpty()) {
                "\nFor each item, suggest the most appropriate category from this list: ${categories.joinToString(", ")}. Return it in the 'category' field."
            } else ""

            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text(LlmScannerConstants.RECEIPT_EXTRACTION_PROMPT + categoryListString)
                }
            )

            val content = response.text ?: return ScannedReceipt(errorMessage = "Empty response from Gemini")

            // Gemini might wrap JSON in code blocks
            val cleanJson = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
           // Log.d("GeminiScanner", "Raw JSON response: $cleanJson")

            parseReceiptJson(cleanJson)
        } catch (e: kotlinx.serialization.SerializationException) {
            Log.e("GeminiScanner", "Serialization Error", e)
            ScannedReceipt(errorMessage = "Communication error: Please check for app updates.")
        } catch (e: Exception) {
            Log.e("GeminiScanner", "Generic Error", e)
            ScannedReceipt(errorMessage = e.message ?: "Gemini API Error")
        }
    }

    private fun parseReceiptJson(content: String): ScannedReceipt {
        return try {
            val data = json.decodeFromString(ReceiptJson.serializer(), content)
            ScannedReceipt(
                storeName = data.store_name,
                items = data.items.map { ScannedItem(it.name, it.quantity, it.price, discount = it.discount, isCoupon = it.isCoupon ?: false, categorySuggestion = it.category) },
                totalSum = data.total_sum
            )
        } catch (e: Exception) {
            Log.e("GeminiScanner", "JSON Parse Error: $content", e)
            ScannedReceipt(errorMessage = "Failed to parse receipt data")
        }
    }
}
