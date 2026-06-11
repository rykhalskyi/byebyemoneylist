package com.otakeeesen.byebyemoneylist.ui.components.scanner

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class SiliconFlowScanner(
    private val apiKey: String,
    private val model: String = "deepseek-ai/deepseek-vl2-tiny"
) : ReceiptParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parse(bitmap: Bitmap, categories: List<String>, stores: List<String>): ScannedReceipt = withContext(Dispatchers.IO) {
        try {
            val base64Image = encodeImageToBase64(bitmap)
            val categoryListString = if (categories.isNotEmpty()) {
                "\nFor each item, suggest the most appropriate category from this list: ${categories.joinToString(", ")}. Return it in the 'category' field."
            } else ""

            val storeListString = if (stores.isNotEmpty()) {
                "\nTry to match the store name against this list: ${stores.joinToString(", ")}. Return the matched name in 'store_name'."
            } else ""

            val prompt = LlmScannerConstants.RECEIPT_EXTRACTION_PROMPT + categoryListString + storeListString

            val requestBody = """
                {
                    "model": "$model",
                    "messages": [
                        {
                            "role": "user",
                            "content": [
                                { "type": "text", "text": "$prompt" },
                                { "type": "image_url", "image_url": { "url": "data:image/jpeg;base64,$base64Image" } }
                            ]
                        }
                    ],
                    "response_format": { "type": "json_object" }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://api.siliconflow.cn/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ScannedReceipt(errorMessage = "SiliconFlow Error: ${response.code}")
                }
                
                val body = response.body?.string() ?: return@withContext ScannedReceipt(errorMessage = "Empty response from SiliconFlow")
                val siliconFlowResponse = json.decodeFromString(SiliconFlowResponse.serializer(), body)
                val content = siliconFlowResponse.choices.firstOrNull()?.message?.content ?: return@withContext ScannedReceipt(errorMessage = "No content in SiliconFlow response")
                
                val cleanJson = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                parseReceiptJson(cleanJson)
            }
        } catch (e: Exception) {
            Log.e("SiliconFlowScanner", "Error", e)
            ScannedReceipt(errorMessage = e.message ?: "Unknown Error")
        }
    }

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun parseReceiptJson(content: String): ScannedReceipt {
        return try {
            val data = json.decodeFromString(ReceiptJson.serializer(), content)
            ScannedReceipt(
                storeName = data.store_name,
                storeAddress = data.store_address,
                items = data.items.map { ScannedItem(it.name, it.quantity, it.price, discount = it.discount, isCoupon = it.isCoupon ?: false, categorySuggestion = it.category) },
                totalSum = data.total_sum
            )
        } catch (e: Exception) {
            Log.e("SiliconFlowScanner", "JSON Parse Error: $content", e)
            ScannedReceipt(errorMessage = "Failed to parse receipt data")
        }
    }
}

@Serializable
data class ResponseFormat(val type: String)

@Serializable
data class SiliconFlowResponse(val choices: List<Choice>)

@Serializable
data class Choice(val message: MessageResponse)

@Serializable
data class MessageResponse(val content: String)
