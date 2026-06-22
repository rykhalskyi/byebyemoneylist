package com.otakeeesen.byebyemoneylist.data.agent

import kotlinx.serialization.Serializable

@Serializable
enum class AgentAction {
    GET_TOTAL_SPENT,
    GET_TOTAL_INCOME,
    LIST_PURCHASES,
    GET_TOP_CATEGORIES,
    GET_TOP_STORES,
    GET_TOP_PRODUCTS,
    GET_PRODUCT_PRICE_HISTORY,
    GET_CATEGORIES,
    GET_PRODUCTS,
    GET_STORES,
    GET_SPENT_BY_PRODUCT,
    GET_SPENT_BY_CATEGORY,
    REJECT_NOT_RELEVANT
}

@Serializable
data class AgentQuery(
    val action: AgentAction,
    val productName: String? = null,
    val categoryName: String? = null,
    val storeName: String? = null,
    val startDate: String? = null, // Format: YYYY-MM-DD
    val endDate: String? = null,   // Format: YYYY-MM-DD
    val limit: Int? = null
)

@Serializable
data class AgentPurchaseItem(
    val productName: String,
    val quantity: Double,
    val price: Double,
    val discount: Double?,
    val storeName: String?,
    val date: String, // YYYY-MM-DD
    val categoryName: String?
)

@Serializable
data class AgentTopItem(
    val name: String,
    val totalSpent: Double,
    val quantity: Double,
    val items: List<AgentPurchaseItem> = emptyList()
)

@Serializable
data class AgentPricePoint(
    val storeName: String?,
    val price: Double,
    val date: String // YYYY-MM-DD
)

@Serializable
data class NamedItem(
    val id: Long,
    val name: String
)

@Serializable
sealed class AgentResult {
    @Serializable
    data class TotalAmount(val amount: Double, val currency: String, val type: String = "spending", val totalQuantity: Double = 0.0) : AgentResult()
    @Serializable
    data class PurchaseList(val items: List<AgentPurchaseItem>) : AgentResult()
    @Serializable
    data class TopItems(val items: List<AgentTopItem>, val groupType: String) : AgentResult()
    @Serializable
    data class PriceHistory(val productName: String, val items: List<AgentPricePoint>) : AgentResult()
    @Serializable
    data class NamedList(val items: List<NamedItem>, val listType: String) : AgentResult()
    @Serializable
    data class Error(val message: String) : AgentResult()
}

@Serializable
enum class MessageSender {
    USER, ASSISTANT
}

@Serializable
data class AgentChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val query: AgentQuery? = null,
    val result: AgentResult? = null
)
