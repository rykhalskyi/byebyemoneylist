package com.otakeeesen.byebyemoneylist.ui.components

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.serialization.json.Json

class GeminiScanner(private val apiKey: String) : ReceiptParser {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parse(bitmap: Bitmap): ScannedReceipt {
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )

        return try {
            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text("Extract items from this receipt. Return ONLY a JSON object with: 'store_name' (string), 'items' (list of {name: string, quantity: number, price: number}), and 'total_sum' (number).")
                }
            )
            
            val content = response.text ?: return ScannedReceipt()
            // Gemini might wrap JSON in code blocks
            val cleanJson = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            
            parseReceiptJson(cleanJson)
        } catch (e: Exception) {
            ScannedReceipt()
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
            ScannedReceipt()
        }
    }
}
