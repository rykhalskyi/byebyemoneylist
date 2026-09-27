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
        const val DEFAULT_SILICONFLOW_MODEL = "deepseek-ai/DeepSeek-V4-Flash-Vision-Exp"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        const val DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-flash-vision-exp"
    }
}

enum class LlmProvider {
    GEMINI,
    SILICONFLOW,
    DEEPSEEK
}
