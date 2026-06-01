package com.otakeeesen.byebyemoneylist.ui.components

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

class SiliconFlowScanner(
    private val apiKey: String,
    private val model: String,
    private val connectTimeoutSeconds: Int = 30,
    private val readTimeoutSeconds: Int = 60
) : ReceiptParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(readTimeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun parse(bitmap: Bitmap): ScannedReceipt {
        val base64Image = bitmapToBase64(bitmap)
        
        val requestBody = SiliconFlowRequest(
            model = model,
            messages = listOf(
                Message(
                    role = "user",
                    content = listOf(
                        Content(
                            type = "image_url",
                            image_url = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                        ),
                        Content(
                            type = "text",
                            text = LlmScannerConstants.RECEIPT_EXTRACTION_PROMPT
                        )
                    )
                )
            ),
            response_format = ResponseFormat(type = "json_object"),
            max_tokens = 2048
        )

        val bodyString = json.encodeToString(SiliconFlowRequest.serializer(), requestBody)
        val request = Request.Builder()
            .url("https://api.siliconflow.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")

            .post(bodyString.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                Log.d("SiliconFlowScanner", "Sending request to ${request.url}")
                Log.d("SiliconFlowScanner", "Payload size: ${bodyString.length / 1024} KB")
                
                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string()
                    Log.d("SiliconFlowScanner", "Response Code: ${response.code}")
                    if (response.code != 200) {
                        Log.e("SiliconFlowScanner", "Error Response: $responseBodyString")
                    }

                    if (!response.isSuccessful) return@withContext ScannedReceipt(errorMessage = "API Error: ${response.code}")
                    
                    val content = responseBodyString?.let { json.decodeFromString(SiliconFlowResponse.serializer(), it).choices.firstOrNull()?.message?.content } 
                        ?: return@withContext ScannedReceipt(errorMessage = "Empty response from API")
                    
                    parseReceiptJson(content)
                }
            } catch (e: Exception) {
                Log.e("SiliconFlowScanner", "Error parsing receipt", e)
                ScannedReceipt(errorMessage = e.message ?: "SiliconFlow API Error")
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 1536
        val scale = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        
        Log.d("SiliconFlowScanner", "Original: ${bitmap.width}x${bitmap.height}, Scaled: ${scaledBitmap.width}x${scaledBitmap.height}, Size: ${bytes.size / 1024} KB")
        
        if (scaledBitmap != bitmap) scaledBitmap.recycle()
        
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
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
            Log.e("SiliconFlowScanner", "JSON Parse Error: $content", e)
            ScannedReceipt(errorMessage = "Failed to parse receipt data")
        }
    }
}

@Serializable
data class SiliconFlowRequest(
    val model: String,
    val messages: List<Message>,
    val response_format: ResponseFormat? = null,
    val max_tokens: Int? = null
)

@Serializable
data class Message(val role: String, val content: List<Content>)

@Serializable
data class Content(val type: String, val text: String? = null, val image_url: ImageUrl? = null)

@Serializable
data class ImageUrl(val url: String, val detail: String = "low")

@Serializable
data class ResponseFormat(val type: String)

@Serializable
data class SiliconFlowResponse(val choices: List<Choice>)

@Serializable
data class Choice(val message: MessageResponse)

@Serializable
data class MessageResponse(val content: String)

@Serializable
data class ReceiptJson(
    val store_name: String? = null,
    val items: List<ItemJson> = emptyList(),
    val total_sum: Double? = null
)

@Serializable
data class ItemJson(val name: String, val quantity: Double, val price: Double)
