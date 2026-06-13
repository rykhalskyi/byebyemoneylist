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

class OpenAiScanner(
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

    override suspend fun parse(bitmap: Bitmap, categories: List<String>, stores: List<String>): ScannedReceipt {
        val base64Image = bitmapToBase64(bitmap)

        val categoryListString = if (categories.isNotEmpty()) {
            "\nFor each item, suggest the most appropriate category from this list: ${categories.joinToString(", ")}. Return it in the 'category' field."
        } else ""

        val storeListString = if (stores.isNotEmpty()) {
            "\nTry to match the store name against this list: ${stores.joinToString(", ")}. Return the matched name in 'store_name'."
        } else ""

        val requestBody = OpenAiRequest(
            model = model,
            messages = listOf(
                OpenAiMessage(
                    role = "user",
                    content = listOf(
                        OpenAiContent(
                            type = "image_url",
                            image_url = OpenAiImageUrl(url = "data:image/jpeg;base64,$base64Image")
                        ),
                        OpenAiContent(
                            type = "text",
                            text = LlmScannerConstants.RECEIPT_EXTRACTION_PROMPT + categoryListString + storeListString
                        )
                    )
                )
            ),
            response_format = OpenAiResponseFormat(type = "json_object"),
            max_tokens = 2048
        )

        val bodyString = json.encodeToString(OpenAiRequest.serializer(), requestBody)
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(bodyString.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string()
                    if (response.code != 200) {
                        Log.e("OpenAiScanner", "Error Response: $responseBodyString")
                    }

                    if (!response.isSuccessful) return@withContext ScannedReceipt(errorMessage = "API Error: ${response.code}")

                    val content = responseBodyString?.let { json.decodeFromString(OpenAiResponse.serializer(), it).choices.firstOrNull()?.message?.content }
                        ?: return@withContext ScannedReceipt(errorMessage = "Empty response from API")

                    parseReceiptJson(content)
                }
            } catch (e: Exception) {
                Log.e("OpenAiScanner", "Error parsing receipt", e)
                ScannedReceipt(errorMessage = e.message ?: "OpenAI API Error")
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 1200
        val scale = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()

        if (scaledBitmap != bitmap) scaledBitmap.recycle()

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun parseReceiptJson(content: String): ScannedReceipt {
        return try {
            val data = json.decodeFromString(ReceiptJson.serializer(), content)
            ScannedReceipt(
                storeName = data.store_name,
                items = data.items.map { ScannedItem(it.name, it.quantity, it.price, discount = it.discount, isCoupon = it.isCoupon ?: false, categorySuggestion = it.category) },
                totalSum = data.total_sum,
                storeAddress = data.store_address
            )
        } catch (e: Exception) {
            Log.e("OpenAiScanner", "JSON Parse Error: $content", e)
            ScannedReceipt(errorMessage = "Failed to parse receipt data")
        }
    }
}

@Serializable
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val response_format: OpenAiResponseFormat? = null,
    val max_tokens: Int? = null
)

@Serializable
data class OpenAiMessage(val role: String, val content: List<OpenAiContent>)

@Serializable
data class OpenAiContent(val type: String, val text: String? = null, val image_url: OpenAiImageUrl? = null)

@Serializable
data class OpenAiImageUrl(val url: String, val detail: String = "low")

@Serializable
data class OpenAiResponseFormat(val type: String)

@Serializable
data class OpenAiResponse(val choices: List<OpenAiChoice>)

@Serializable
data class OpenAiChoice(val message: OpenAiMessageResponse)

@Serializable
data class OpenAiMessageResponse(val content: String)
