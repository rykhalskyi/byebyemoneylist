package com.otakeeesen.byebyemoneylist.data

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class SharedListDto(
    val title: String,
    val storeName: String? = null,
    val items: List<SharedItemDto>
) {
    fun toShareText(importPrefix: String): String {
        val sb = StringBuilder()
        sb.append("Shopping List: $title\n")
        storeName?.let { sb.append("Store: $it\n") }
        sb.append("\n")
        items.forEach { item ->
            sb.append("- ${item.name}")
            if (item.quantity != 1.0) sb.append(" x${item.quantity}")
            item.price?.let { sb.append(" ($it)") }
            sb.append("\n")
        }
        
        val json = jsonSerializer.encodeToString(this)
        val base64 = Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)
        
        sb.append("\n$importPrefix\n")
        sb.append(base64)
        
        return sb.toString()
    }

    companion object {
        private val jsonSerializer = Json { ignoreUnknownKeys = true }
        private const val INTERNAL_MARKER = "--- Import-Code ---"

        fun fromShareText(text: String, importPrefix: String): SharedListDto? {
            // 1. Try with the dynamic localized prefix first
            var parts = text.split(importPrefix)
            if (parts.size >= 2) {
                val dto = tryParseBase64(parts.last())
                if (dto != null) return dto
            }

            // 2. Try with the hardcoded internal marker (to handle cross-language shares)
            parts = text.split(INTERNAL_MARKER)
            if (parts.size >= 2) {
                val dto = tryParseBase64(parts.last())
                if (dto != null) return dto
            }

            // 3. Fallback: Try to parse the whole string as Base64 (user might have copied just the code)
            return tryParseBase64(text)
        }

        private fun tryParseBase64(input: String): SharedListDto? {
            val base64 = input.trim()
            if (base64.isBlank()) return null
            return try {
                val json = String(Base64.decode(base64, Base64.DEFAULT))
                jsonSerializer.decodeFromString<SharedListDto>(json)
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Serializable
data class SharedItemDto(
    val name: String,
    val quantity: Double,
    val price: Double? = null,
    val discount: Double? = null,
    val categoryName: String? = null
)
