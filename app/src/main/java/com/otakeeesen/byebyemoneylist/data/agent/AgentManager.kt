package com.otakeeesen.byebyemoneylist.data.agent

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.otakeeesen.byebyemoneylist.data.LlmProfile
import com.otakeeesen.byebyemoneylist.data.LlmProvider
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

data class AgentResponse(
    val success: Boolean,
    val textResponse: String,
    val query: AgentQuery? = null,
    val result: AgentResult? = null
)

class AgentManager(
    private val preferencesManager: PreferencesManager,
    private val executor: AgentQueryExecutor
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun processQuery(userPrompt: String): AgentResponse = withContext(Dispatchers.IO) {
        val activeProfileId = preferencesManager.getActiveProfileId()
        val profiles = preferencesManager.getLlmProfiles()
        val profile = profiles.find { it.id == activeProfileId }

        if (profile == null) {
            return@withContext AgentResponse(
                success = false,
                textResponse = "No active LLM profile found. Please configure your API keys in Settings first."
            )
        }

        if (!preferencesManager.isLlmConsentGranted()) {
            return@withContext AgentResponse(
                success = false,
                textResponse = "Consent is required to process purchase data through LLM APIs."
            )
        }

        try {
            // Step 1: Extract parameters (User prompt -> JSON AgentQuery)
            val currentDate = LocalDate.now().toString()
            val extractionSystemInstruction = """
                You are an expert query parameters extractor for the ByeByeMoney personal finance app.
                The current local date is: $currentDate.

                You ONLY handle questions about the user's purchases, products, categories, stores,
                spending, and income — in ANY language the user speaks.

                If the user's message (regardless of language) is NOT about their purchases, products,
                categories, stores, spending, or income:
                  → return {"action": "REJECT_NOT_RELEVANT"}

                Otherwise, return ONLY a raw JSON object conforming to this schema (without markdown wrapping):
                {
                  "action": "GET_TOTAL_SPENT" | "GET_TOTAL_INCOME" | "LIST_PURCHASES" | "GET_TOP_CATEGORIES" | "GET_TOP_STORES" | "GET_TOP_PRODUCTS" | "GET_PRODUCT_PRICE_HISTORY" | "GET_CATEGORIES" | "GET_PRODUCTS" | "GET_STORES" | "GET_SPENT_BY_PRODUCT" | "GET_SPENT_BY_CATEGORY",
                  "productName": string (optional),
                  "categoryName": string (optional),
                  "storeName": string (optional),
                  "startDate": "YYYY-MM-DD" (optional - format is strictly YYYY-MM-DD),
                  "endDate": "YYYY-MM-DD" (optional - format is strictly YYYY-MM-DD),
                  "limit": integer (optional)
                }

                Rules:
                1. Calculate relative periods (e.g. "this month", "yesterday", "last week", "in May") based on the current date: $currentDate.
                2. Preserve productName, categoryName, and storeName in the user's original language (e.g. "Eier", "молоко", "хліб").
                3. Do not output anything else. Just the raw JSON object. Do not wrap in ```json ``` blocks.
           """.trimIndent()

            val rawJsonResult = callLlm(profile, extractionSystemInstruction, userPrompt)
            val cleanJson = cleanJsonString(rawJsonResult)

            val query = json.decodeFromString<AgentQuery>(cleanJson)

            if (query.action == AgentAction.REJECT_NOT_RELEVANT) {
                return@withContext AgentResponse(
                    success = true,
                    textResponse = "I can only help with questions about your purchases, products, categories, stores, and spending.",
                    result = AgentResult.Error("Out of scope")
                )
            }

            // Step 2: Execute safe parameterized local DB query
            val dbResult = executor.execute(query)

            if (dbResult is AgentResult.Error) {
                Log.e("AgentManager", "DB execution error: ${dbResult.message}")
                return@withContext AgentResponse(
                    success = false,
                    textResponse = "I couldn't retrieve that information. Please try again.",
                    query = query,
                    result = dbResult
                )
            }

            // Step 3: Synthesize friendly text response (User question + DB result -> natural language)
            val dbResultString = formatResultForLlm(dbResult)
            val synthesisSystemInstruction = """
                You are the friendly AI Assistant of the ByeByeMoney app.
                Synthesize a helpful, conversational, and concise answer for the user based on their question and the database result.
                Always speak direct human language.
                Keep it under 3-4 sentences.
                Do not talk about SQL, JSON, schemas, parameters, code, or databases.
                Use the local currency format (e.g. ${preferencesManager.getCurrencySymbol() ?: "$"}) when displaying prices and sums.
                If the result includes both a total amount and a total quantity, mention both in your answer.
            """.trimIndent()

            val promptForSynthesis = """
                User Question: "$userPrompt"
                Database Results:
                $dbResultString
            """.trimIndent()

            val finalFriendlyText = callLlm(profile, synthesisSystemInstruction, promptForSynthesis)

            AgentResponse(
                success = true,
                textResponse = finalFriendlyText.trim(),
                query = query,
                result = dbResult
            )

        } catch (e: Exception) {
            Log.e("AgentManager", "Error processing AI query", e)
            AgentResponse(
                success = false,
                textResponse = "Error processing your request: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    private suspend fun callLlm(profile: LlmProfile, systemInstruction: String, userMessage: String): String {
        return when (profile.provider) {
            LlmProvider.GEMINI -> callGemini(profile, systemInstruction, userMessage)
            LlmProvider.SILICONFLOW -> callSiliconFlow(profile, systemInstruction, userMessage)
        }
    }

    private suspend fun callGemini(profile: LlmProfile, systemInstruction: String, userMessage: String): String = withContext(Dispatchers.IO) {
        val modelName = profile.model?.takeIf { it.isNotBlank() } ?: LlmProfile.DEFAULT_GEMINI_MODEL
        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = profile.apiKey,
            requestOptions = RequestOptions(timeout = profile.readTimeoutSeconds.seconds),
            systemInstruction = content { text(systemInstruction) }
        )
        val response = generativeModel.generateContent(userMessage)
        response.text ?: throw Exception("Empty response from Gemini")
    }

    private suspend fun callSiliconFlow(profile: LlmProfile, systemInstruction: String, userMessage: String): String = withContext(Dispatchers.IO) {
        val model = profile.model?.takeIf { it.isNotBlank() } ?: LlmProfile.DEFAULT_SILICONFLOW_MODEL
        
        val requestBody = SiliconFlowRequest(
            model = model,
            messages = listOf(
                Message(role = "system", content = systemInstruction),
                Message(role = "user", content = userMessage)
            ),
            max_tokens = 1024
        )

        val bodyString = json.encodeToString(SiliconFlowRequest.serializer(), requestBody)
        val request = Request.Builder()
            .url("https://api.siliconflow.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${profile.apiKey}")
            .post(bodyString.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBodyString = response.body?.string()
            if (!response.isSuccessful || responseBodyString == null) {
                throw Exception("SiliconFlow API Error: ${response.code} ${responseBodyString ?: ""}")
            }
            val responseObj = json.decodeFromString<SiliconFlowResponse>(responseBodyString)
            responseObj.choices.firstOrNull()?.message?.content ?: throw Exception("Empty content in SiliconFlow response")
        }
    }

    private fun cleanJsonString(content: String): String {
        var clean = content.trim()
        if (clean.startsWith("```")) {
            clean = clean.removePrefix("```json").removePrefix("```")
            if (clean.endsWith("```")) {
                clean = clean.removeSuffix("```")
            }
        }
        return clean.trim()
    }

    private fun formatResultForLlm(result: AgentResult): String {
        return when (result) {
            is AgentResult.TotalAmount -> {
                "Total Spent: ${result.amount} ${result.currency}. Total Quantity: ${result.totalQuantity} items. (${result.type})"
            }
            is AgentResult.PurchaseList -> {
                if (result.items.isEmpty()) "No purchase records found."
                else {
                    val display = result.items.take(20)
                    val extra = if (result.items.size > 20) "\n... and ${result.items.size - 20} more" else ""
                    display.joinToString("\n") { 
                        "- Date: ${it.date}, Store: ${it.storeName ?: "N/A"}, Product: ${it.productName}, Quantity: ${it.quantity}, Price: ${it.price}, Discount: ${it.discount ?: 0.0}, Category: ${it.categoryName ?: "N/A"}"
                    } + extra
                }
            }
            is AgentResult.TopItems -> {
                if (result.items.isEmpty()) "No results."
                else {
                    val display = result.items.take(20)
                    val extra = if (result.items.size > 20) "\n... and ${result.items.size - 20} more" else ""
                    display.joinToString("\n") {
                        "- Name: ${it.name}, Total Spent/Quantity: ${it.totalSpent} (Quantity: ${it.quantity})"
                    } + extra
                }
            }
            is AgentResult.PriceHistory -> {
                if (result.items.isEmpty()) "No price history found for ${result.productName}."
                else "Price history for ${result.productName}:\n" + result.items.joinToString("\n") {
                    "- Date: ${it.date}, Store: ${it.storeName ?: "N/A"}, Price: ${it.price}"
                }
            }
            is AgentResult.NamedList -> {
                if (result.items.isEmpty()) "No ${result.listType}s found."
                else "${result.listType.replaceFirstChar { it.uppercase() }}s:\n" + result.items.joinToString("\n") {
                    "- ${it.name}"
                }
            }
            is AgentResult.Error -> "Error: ${result.message}"
        }
    }

    // Helper classes for SiliconFlow REST communication
    @Serializable
    private data class SiliconFlowRequest(
        val model: String,
        val messages: List<Message>,
        val max_tokens: Int? = null
    )

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class SiliconFlowResponse(val choices: List<Choice>)

    @Serializable
    private data class Choice(val message: MessageResponse)

    @Serializable
    private data class MessageResponse(val content: String)
}
