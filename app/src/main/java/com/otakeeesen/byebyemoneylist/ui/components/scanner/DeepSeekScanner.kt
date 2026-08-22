package com.otakeeesen.byebyemoneylist.ui.components.scanner

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.otakeeesen.byebyemoneylist.data.LlmProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class DeepSeekScanner(
    private val apiKey: String,
    private val model: String,
    private val connectTimeoutSeconds: Int = 30,
    private val readTimeoutSeconds: Int = 60
) : ReceiptParser {

    private val resolvedModel = model.ifBlank { LlmProfile.DEFAULT_DEEPSEEK_MODEL }

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
            "\nTry to match the store name against this list: ${stores.joinToString(", ")}. Return the matched name in 'store_name'. If there is no good match, return the name exactly as printed on the receipt and do NOT pick a name from the list."
        } else ""

        val requestBody = DeepSeekChatRequest(
            model = resolvedModel,
            messages = listOf(
                DeepSeekMessage(
                    role = "user",
                    content = listOf(
                        DeepSeekContent(
                            type = "image_url",
                            image_url = DeepSeekImageUrl(url = "data:image/jpeg;base64,$base64Image")
                        ),
                        DeepSeekContent(
                            type = "text",
                            text = LlmScannerConstants.RECEIPT_EXTRACTION_PROMPT + categoryListString + storeListString
                        )
                    )
                )
            ),
            response_format = DeepSeekResponseFormat(type = "json_object"),
            max_tokens = 2048,
            thinking = DeepSeekThinking(type = "disabled")
        )

        val bodyString = json.encodeToString(DeepSeekChatRequest.serializer(), requestBody)
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(bodyString.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string()
                    if (response.code != 200) {
                        Log.e("DeepSeekScanner", "Error Response: $responseBodyString")
                    }

                    if (!response.isSuccessful) return@withContext ScannedReceipt(errorMessage = "API Error: ${response.code}")

                    val decoded = responseBodyString?.let { json.decodeFromString(DeepSeekChatResponse.serializer(), it) }
                        ?: return@withContext ScannedReceipt(errorMessage = "Empty response from API")
                    val message = decoded.choices.firstOrNull()?.message
                    val content = message?.content
                    if (content.isNullOrBlank()) {
                        val reasoning = message?.reasoning_content?.takeIf { it.isNotBlank() }
                        Log.e("DeepSeekScanner", "Empty content from DeepSeek. reasoning_content: $reasoning")
                        val detail = if (reasoning != null) ": ${reasoning.take(200)}" else ""
                        return@withContext ScannedReceipt(
                            errorMessage = "DeepSeek returned empty content$detail"
                        )
                    }

                    parseReceiptJson(content)
                }
            } catch (e: Exception) {
                Log.e("DeepSeekScanner", "Error parsing receipt", e)
                ScannedReceipt(errorMessage = e.message ?: "DeepSeek API Error")
            }
        }
    }

    override suspend fun parseMultiPart(bitmaps: List<Bitmap>, categories: List<String>, stores: List<String>): ScannedReceipt {
        if (bitmaps.isEmpty()) return ScannedReceipt(errorMessage = "No image provided")
        if (bitmaps.size == 1) return parse(bitmaps.first(), categories, stores)

        val categoryListString = if (categories.isNotEmpty()) {
            "\nFor each item, suggest the most appropriate category from this list: ${categories.joinToString(", ")}. Return it in the 'category' field."
        } else ""

        val storeListString = if (stores.isNotEmpty()) {
            "\nTry to match the store name against this list: ${stores.joinToString(", ")}. Return the matched name in 'store_name'. If there is no good match, return the name exactly as printed on the receipt and do NOT pick a name from the list."
        } else ""

        val contentList = mutableListOf<DeepSeekContent>()
        bitmaps.forEach { bitmap ->
            val base64Image = bitmapToBase64(bitmap)
            contentList.add(
                DeepSeekContent(
                    type = "image_url",
                    image_url = DeepSeekImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }
        contentList.add(
            DeepSeekContent(
                type = "text",
                text = LlmScannerConstants.MULTI_PART_RECEIPT_PROMPT + categoryListString + storeListString
            )
        )

        val requestBody = DeepSeekChatRequest(
            model = resolvedModel,
            messages = listOf(
                DeepSeekMessage(
                    role = "user",
                    content = contentList
                )
            ),
            response_format = DeepSeekResponseFormat(type = "json_object"),
            max_tokens = 4096,
            thinking = DeepSeekThinking(type = "disabled")
        )

        val bodyString = json.encodeToString(DeepSeekChatRequest.serializer(), requestBody)
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(bodyString.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string()
                    if (response.code != 200) {
                        Log.e("DeepSeekScanner", "Error Response: $responseBodyString")
                    }

                    if (!response.isSuccessful) return@withContext ScannedReceipt(errorMessage = "API Error: ${response.code}")

                    val decoded = responseBodyString?.let { json.decodeFromString(DeepSeekChatResponse.serializer(), it) }
                        ?: return@withContext ScannedReceipt(errorMessage = "Empty response from API")
                    val message = decoded.choices.firstOrNull()?.message
                    val content = message?.content
                    if (content.isNullOrBlank()) {
                        val reasoning = message?.reasoning_content?.takeIf { it.isNotBlank() }
                        Log.e("DeepSeekScanner", "Empty content from DeepSeek. reasoning_content: $reasoning")
                        val detail = if (reasoning != null) ": ${reasoning.take(200)}" else ""
                        return@withContext ScannedReceipt(
                            errorMessage = "DeepSeek returned empty content$detail"
                        )
                    }

                    parseReceiptJson(content)
                }
            } catch (e: Exception) {
                Log.e("DeepSeekScanner", "Error parsing receipt", e)
                ScannedReceipt(errorMessage = e.message ?: "DeepSeek API Error")
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 1024
        val scale = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
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
            Log.e("DeepSeekScanner", "JSON Parse Error: $content", e)
            ScannedReceipt(errorMessage = "Failed to parse receipt data")
        }
    }
}

@Serializable
data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val response_format: DeepSeekResponseFormat? = null,
    val max_tokens: Int? = null,
    val thinking: DeepSeekThinking? = null
)

@Serializable
data class DeepSeekThinking(val type: String)

@Serializable
data class DeepSeekMessage(val role: String, val content: List<DeepSeekContent>)

@Serializable
data class DeepSeekContent(val type: String, val text: String? = null, val image_url: DeepSeekImageUrl? = null)

@Serializable
data class DeepSeekImageUrl(val url: String, val detail: String = "low")

@Serializable
data class DeepSeekResponseFormat(val type: String)

@Serializable
data class DeepSeekChatResponse(val choices: List<DeepSeekChoice>)

@Serializable
data class DeepSeekChoice(val message: DeepSeekMessageResponse)

@Serializable
data class DeepSeekMessageResponse(val content: String = "", val reasoning_content: String? = null)
