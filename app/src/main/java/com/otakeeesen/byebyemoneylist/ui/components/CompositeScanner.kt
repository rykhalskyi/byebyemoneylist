package com.otakeeesen.byebyemoneylist.ui.components

import android.graphics.Bitmap
import com.otakeeesen.byebyemoneylist.data.LlmProvider
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager

class CompositeScanner(private val preferencesManager: PreferencesManager) : ReceiptParser {

    override suspend fun parse(bitmap: Bitmap): ScannedReceipt {
        val activeProfileId = preferencesManager.getActiveProfileId()
        val profiles = preferencesManager.getLlmProfiles()
        val activeProfile = profiles.find { it.id == activeProfileId }
        
        val llmScanner = when (activeProfile?.provider) {
            LlmProvider.GEMINI -> {
                if (activeProfile.apiKey.isNotBlank()) {
                    GeminiScanner(
                        apiKey = activeProfile.apiKey,
                        readTimeoutSeconds = activeProfile.readTimeoutSeconds
                    )
                } else null
            }
            LlmProvider.SILICONFLOW -> {
                val model = activeProfile.model ?: "Qwen/Qwen3-VL-32B-Instruct"
                if (activeProfile.apiKey.isNotBlank()) {
                    SiliconFlowScanner(
                        apiKey = activeProfile.apiKey,
                        model = model,
                        connectTimeoutSeconds = activeProfile.connectTimeoutSeconds,
                        readTimeoutSeconds = activeProfile.readTimeoutSeconds
                    )
                } else null
            }
            null -> null
        }

        var llmError: String? = null
        if (llmScanner != null) {
            val result = llmScanner.parse(bitmap)
            if (result.errorMessage != null) {
                llmError = result.errorMessage
            } else if (result.totalSum != null || result.items.isNotEmpty()) {
                return result
            }
        }

        // Fallback to ML Kit
        val mlKitResult = MlKitScanner().parse(bitmap)
        return if (llmError != null) {
            mlKitResult.copy(errorMessage = llmError)
        } else {
            mlKitResult
        }
    }
}
