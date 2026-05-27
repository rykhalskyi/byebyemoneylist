package com.otakeeesen.byebyemoneylist.ui.components

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.serialization.json.Json

class GeminiScanner(private val apiKey: String) : ReceiptParser {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parse(bitmap: Bitmap): ScannedReceipt {
        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey
        )

        return try {
            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text("Extract items from this receipt. Return ONLY a JSON object with: 'store_name' (string), 'items' (list of {name: string, quantity: number, price: number}), and 'total_sum' (number).")
                }
            )

            val content = response.text ?: return ScannedReceipt(errorMessage = "Empty response from Gemini")

            // Gemini might wrap JSON in code blocks
            val cleanJson = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            parseReceiptJson(cleanJson)
        } catch (e: kotlinx.serialization.SerializationException) {
            Log.e("GeminiScanner", "Serialization Error", e)
            ScannedReceipt(errorMessage = "Communication error: Please check for app updates.")
        } catch (e: Exception) {
            ScannedReceipt(errorMessage = e.message ?: "Gemini API Error")
        }
    }

    private fun parseReceiptJson(content: String): ScannedReceipt {
        return try {
            val data = json.decodeFromString(ReceiptJson.serializer(), content)
            ScannedReceipt(
                storeName = data.store_name,
                items = data.items.map { ScannedItem(it.name, it.quantity, it.price) },
                totalSum = data.total_sum
            )
        } catch (e: Exception) {
            ScannedReceipt(errorMessage = "Failed to parse receipt data")
        }
    }
}
