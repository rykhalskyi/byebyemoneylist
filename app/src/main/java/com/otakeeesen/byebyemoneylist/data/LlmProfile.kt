package com.otakeeesen.byebyemoneylist.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class LlmProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val provider: LlmProvider,
    val apiKey: String,
    val model: String? = null
)

enum class LlmProvider {
    GEMINI,
    SILICONFLOW
}
