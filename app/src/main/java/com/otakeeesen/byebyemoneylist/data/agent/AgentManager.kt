package com.otakeeesen.byebyemoneylist.data.agent

import android.util.Log
import androidx.annotation.VisibleForTesting
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

open class AgentManager(
    private val preferencesManager: PreferencesManager,
    private val executor: AgentQueryExecutor,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        private const val MAX_HISTORY_TOKENS = 500

        private fun extractionSystemInstruction(currentDate: String) = """
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
            4. Preserve the user's raw category mention exactly as stated (e.g. "fruits and vegetables", "dairy products"). The system resolves it to actual DB categories later.
            5. If user asks about specific category expenses, use GET_SPENT_BY_CATEGORY.
        """.trimIndent()

        private fun synthesisSystemInstruction(currencySymbol: String) = """
            You are the friendly AI Assistant of the ByeByeMoney app.
            Synthesize a helpful, conversational, and concise answer for the user based on their question and the database result.
            Always speak direct human language.
            Keep it under 3-4 sentences.
            Do not talk about SQL, JSON, schemas, parameters, code, or databases.
            Use the local currency format (e.g. $currencySymbol) when displaying prices and sums.
            If the result includes both a total amount and a total quantity, mention both in your answer.
        """.trimIndent()

        private val CATEGORY_RESOLVER_SYSTEM_INSTRUCTION = """
            You are a category matcher. Given a list of available categories from the user's database and a search term, determine which categories match.

            Rules:
            1. Match the user's description to the most relevant existing category name(s) from the list.
            2. If a single category encompasses the user's description (e.g. "Fruits & Vegetables" for "fruits and vegetables"), return just that one exact category name.
            3. Only return multiple comma-separated names when the user clearly intends multiple DISTINCT categories from the list.
            4. Return the EXACT category name(s) as they appear in the list — case-sensitive, punctuation included.
            5. If you cannot confidently match any category to the user's search term, return ONLY: UNCERTAIN
            6. Do NOT return any other text, explanation, or markdown.

            Examples:
            Available: Fruits & Vegetables, Dairy, Bakery, Beverages
            User's term: fruits and vegetables
            Response: Fruits & Vegetables

            Available: Rent & Mortgage, Utilities, Subscriptions
            User's term: rent
            Response: Rent & Mortgage

            Available: Fruits & Vegetables, Dairy, Bakery, Beverages, Cleaning Supplies
            User's term: dairy products
            Response: Dairy
        """.trimIndent()
    }

    private var cachedCurrencySymbol: String? = null

    suspend fun processQuery(userPrompt: String, history: List<AgentChatMessage> = emptyList()): AgentResponse = withContext(Dispatchers.IO) {
        val profile = getActiveProfile() ?: return@withContext AgentResponse(
            success = false,
            textResponse = "No active LLM profile found. Please configure your API keys in Settings first."
        )

        if (!preferencesManager.isLlmConsentGranted()) {
            return@withContext AgentResponse(
                success = false,
                textResponse = "Consent is required to process purchase data through LLM APIs."
            )
        }

        try {
            val query = extractQuery(profile, userPrompt, history)

            if (query.action == AgentAction.REJECT_NOT_RELEVANT) {
                return@withContext AgentResponse(
                    success = true,
                    textResponse = "I can only help with questions about your purchases, products, categories, stores, and spending.",
                    result = AgentResult.Error("Out of scope")
                )
            }

            val resolvedQuery = tryResolveCategory(userPrompt, query, profile)
            if (resolvedQuery == null) {
                val categoriesResult = executor.execute(AgentQuery(action = AgentAction.GET_CATEGORIES))
                val categoryNames = (categoriesResult as? AgentResult.NamedList)?.items?.map { it.name } ?: emptyList()
                return@withContext AgentResponse(
                    success = true,
                    textResponse = "I found these categories in your app: ${categoryNames.joinToString(", ")}.\n\nWhich one(s) are you interested in?",
                    query = query,
                    result = categoriesResult
                )
            }

            val dbResult = executeQuery(resolvedQuery)

            if (dbResult is AgentResult.Error) {
                return@withContext AgentResponse(
                    success = false,
                    textResponse = "I couldn't retrieve that information. Please try again.",
                    query = query,
                    result = dbResult
                )
            }

            val friendlyText = synthesizeResponse(profile, userPrompt, dbResult, history)

            AgentResponse(
                success = true,
                textResponse = friendlyText.trim(),
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

    private fun getActiveProfile(): LlmProfile? {
        val activeProfileId = preferencesManager.getActiveProfileId()
        val profiles = preferencesManager.getLlmProfiles()
        return profiles.find { it.id == activeProfileId }
    }

    private fun buildHistoryContext(history: List<AgentChatMessage>): String {
        val parts = mutableListOf<String>()
        var totalEstimatedTokens = 0
        for (msg in history.reversed()) {
            val prefix = if (msg.sender == MessageSender.USER) "User: " else "Assistant: "
            val line = "$prefix${msg.content}"
            val estimatedTokens = line.length / 4
            if (totalEstimatedTokens + estimatedTokens > MAX_HISTORY_TOKENS) break
            totalEstimatedTokens += estimatedTokens
            parts.add(line)
        }
        val context = parts.asReversed().joinToString("\n")
        return "Previous conversation (for context only, do NOT respond to these):\n$context"
    }

    private suspend fun extractQuery(profile: LlmProfile, userPrompt: String, history: List<AgentChatMessage>): AgentQuery {
        val currentDate = LocalDate.now().toString()
        val systemInstruction = extractionSystemInstruction(currentDate)
        val promptWithContext = if (history.isEmpty()) userPrompt else buildHistoryContext(history) + "\n\nCurrent question: " + userPrompt
        val rawJsonResult = callLlm(profile, systemInstruction, promptWithContext)
        val cleanJson = cleanJsonString(rawJsonResult)
        val query = json.decodeFromString<AgentQuery>(cleanJson)
        Log.d("AgentManager", "Extracted query: action=${query.action}, categoryName='${query.categoryName}', productName='${query.productName}', storeName='${query.storeName}'")
        return query
    }

    private suspend fun tryResolveCategory(
        userPrompt: String,
        query: AgentQuery,
        profile: LlmProfile
    ): AgentQuery? {
        if (query.categoryName == null || !actionSupportsCategory(query.action)) {
            Log.d("AgentManager", "Skipping category resolution: categoryName=${query.categoryName}, action=${query.action}")
            return query
        }

        val categoriesResult = executor.execute(AgentQuery(action = AgentAction.GET_CATEGORIES))
        if (categoriesResult !is AgentResult.NamedList || categoriesResult.items.isEmpty()) {
            Log.d("AgentManager", "No categories found in DB, skipping resolution")
            return query
        }

        val categoryNames = categoriesResult.items.map { it.name }
        Log.d("AgentManager", "Resolving category. Available=[${categoryNames.joinToString(", ")}], userMention='${query.categoryName}'")

        val resolved = resolveCategoryNames(userPrompt, query.categoryName, categoryNames, profile)
        if (resolved == null) {
            Log.d("AgentManager", "Category resolution: UNCERTAIN - asking user to clarify")
            return null
        }

        Log.d("AgentManager", "Category resolved: '$resolved'")
        return query.copy(categoryName = resolved)
    }

    private suspend fun executeQuery(query: AgentQuery): AgentResult {
        Log.d("AgentManager", "Executing DB query: action=${query.action}, categoryName='${query.categoryName}'")
        val result = executor.execute(query)
        Log.d("AgentManager", "DB result type: ${result::class.simpleName}, result=${result}")
        return result
    }

    private suspend fun synthesizeResponse(
        profile: LlmProfile,
        userPrompt: String,
        dbResult: AgentResult,
        history: List<AgentChatMessage>
    ): String {
        val currencySymbol = cachedCurrencySymbol ?: preferencesManager.getCurrencySymbol()?.also { cachedCurrencySymbol = it } ?: "$"
        val dbResultString = formatResultForLlm(dbResult)
        val systemInstruction = synthesisSystemInstruction(currencySymbol)

        val historyFragment = if (history.isEmpty()) "" else buildHistoryContext(history) + "\n\n"
        val promptForSynthesis = """
            ${historyFragment}User Question: "$userPrompt"
            Database Results:
            $dbResultString
        """.trimIndent()

        return callLlm(profile, systemInstruction, promptForSynthesis)
    }

    @VisibleForTesting
    protected open suspend fun callLlm(profile: LlmProfile, systemInstruction: String, userMessage: String): String {
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
            max_tokens = profile.maxTokens
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
        return content.trim()
            .replace(Regex("```json\\n?|```"), "")
            .trim()
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
                    buildString {
                        val totalQtyFormatted = "%.2f".format(result.totalQuantity)
                        appendLine("Total: ${"%.2f".format(result.totalSpent)} (${result.itemCount} items, $totalQtyFormatted units)")
                        appendLine()
                        append(display.joinToString("\n\n") { top ->
                            buildString {
                                appendLine("- Name: ${top.name}, Total: ${top.totalSpent} (Quantity: ${top.quantity})")
                                if (top.items.isNotEmpty()) {
                                    appendLine("  Items:")
                                    top.items.forEach { item ->
                                        appendLine("    - Date: ${item.date}, Product: ${item.productName}, Qty: ${item.quantity}, Price: ${item.price}")
                                    }
                                }
                            }
                        })
                        append(extra)
                    }
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

    private fun actionSupportsCategory(action: AgentAction): Boolean {
        return action in setOf(
            AgentAction.LIST_PURCHASES,
            AgentAction.GET_TOTAL_SPENT,
            AgentAction.GET_SPENT_BY_PRODUCT,
            AgentAction.GET_SPENT_BY_CATEGORY
        )
    }

    private suspend fun resolveCategoryNames(
        userPrompt: String,
        extractedCategoryName: String,
        allCategoryNames: List<String>,
        profile: LlmProfile
    ): String? {
        val prompt = """
            Available categories: ${allCategoryNames.joinToString(", ")}
            User's search term: "$extractedCategoryName"
            Original request: "$userPrompt"
        """.trimIndent()

        val response = callLlm(profile, CATEGORY_RESOLVER_SYSTEM_INSTRUCTION, prompt)
        val cleaned = response.trim()
        Log.d("AgentManager", "resolveCategoryNames LLM raw response: '$cleaned'")

        if (cleaned.equals("UNCERTAIN", ignoreCase = true)) return null

        val resolvedNames = cleaned.split(",").map { it.trim() }.filter { it.isNotBlank() }
        Log.d("AgentManager", "resolveCategoryNames LLM names: $resolvedNames")
        val validNames = resolvedNames.filter { name ->
            allCategoryNames.any { it.equals(name, ignoreCase = true) }
        }
        Log.d("AgentManager", "resolveCategoryNames valid names: $validNames")

        return if (validNames.isEmpty()) null else validNames.joinToString(", ")
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
