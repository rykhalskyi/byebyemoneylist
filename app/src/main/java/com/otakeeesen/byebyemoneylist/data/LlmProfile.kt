package com.otakeeesen.byebyemoneylist.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class LlmProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val provider: LlmProvider,
    val apiKey: String,
    val model: String? = null,
    val connectTimeoutSeconds: Int = 30,
    val readTimeoutSeconds: Int = 60,
    val maxTokens: Int = 2048
) {
    companion object {
        const val DEFAULT_SILICON_FLOW_PROFILE_ID = "closed_test_key_silicon_flow"
        const val DEFAULT_SILICONFLOW_MODEL = "Qwen/Qwen3-VL-8B-Instruct"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
}

enum class LlmProvider {
    GEMINI,
    SILICONFLOW
}
