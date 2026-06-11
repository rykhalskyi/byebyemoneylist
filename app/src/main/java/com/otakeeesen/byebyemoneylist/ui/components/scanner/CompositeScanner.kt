package com.otakeeesen.byebyemoneylist.ui.components.scanner

import android.graphics.Bitmap
import com.otakeeesen.byebyemoneylist.data.LlmProvider
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.LlmProfile

class CompositeScanner(
    private val preferencesManager: PreferencesManager
) : ReceiptParser {

    override suspend fun parse(bitmap: Bitmap, categories: List<String>, stores: List<String>): ScannedReceipt {
        val activeProfileId = preferencesManager.getActiveProfileId() ?: return MlKitScanner().parse(bitmap, categories, stores)
        val profiles = preferencesManager.getLlmProfiles()
        val profile = profiles.find { it.id == activeProfileId } ?: return MlKitScanner().parse(bitmap, categories, stores)
        
        val scanner = when (profile.provider) {
            LlmProvider.GEMINI -> GeminiScanner(profile.apiKey)
            LlmProvider.SILICONFLOW -> SiliconFlowScanner(profile.apiKey, profile.model ?: "")
        }

        val result = scanner.parse(bitmap, categories, stores)
        
        if (result.errorMessage == null) return result

        // If LLM fails, fallback to ML Kit but preserve the error message
        val llmError = result.errorMessage
        // Fallback to ML Kit
        val mlKitResult = MlKitScanner().parse(bitmap, categories, stores)
        return if (llmError != null) {
            mlKitResult.copy(errorMessage = llmError)
        } else {
            mlKitResult
        }
    }
}
