package com.otakeeesen.byebyemoneylist.ui.components.scanner

import android.graphics.Bitmap
import com.otakeeesen.byebyemoneylist.data.LlmProvider
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.LlmProfile

class CompositeScanner(
    private val preferencesManager: PreferencesManager
) : ReceiptParser {

    private var cachedProfileId: String? = null
    private var cachedScanner: ReceiptParser? = null

    override suspend fun parse(bitmap: Bitmap, categories: List<String>, stores: List<String>): ScannedReceipt {
        val profile = resolveProfile() ?: return MlKitScanner().parse(bitmap, categories, stores)

        val result = scannerFor(profile).parse(bitmap, categories, stores)

        if (result.errorMessage == null) return result

        // If LLM fails, fallback to ML Kit but preserve the error message
        val llmError = result.errorMessage
        val mlKitResult = MlKitScanner().parse(bitmap, categories, stores)
        return mlKitResult.copy(errorMessage = llmError)
    }

    override suspend fun parseMultiPart(bitmaps: List<Bitmap>, categories: List<String>, stores: List<String>): ScannedReceipt {
        val profile = resolveProfile() ?: return MlKitScanner().parseMultiPart(bitmaps, categories, stores)

        val result = scannerFor(profile).parseMultiPart(bitmaps, categories, stores)

        if (result.errorMessage == null) return result

        val llmError = result.errorMessage
        val mlKitResult = MlKitScanner().parseMultiPart(bitmaps, categories, stores)
        return mlKitResult.copy(errorMessage = llmError)
    }

    private fun resolveProfile(): LlmProfile? {
        val activeProfileId = preferencesManager.getActiveProfileId() ?: return null
        return preferencesManager.getLlmProfiles().find { it.id == activeProfileId }
    }

    private fun scannerFor(profile: LlmProfile): ReceiptParser {
        cachedScanner?.let { cached ->
            if (cachedProfileId == profile.id) return cached
        }

        val scanner = when (profile.provider) {
            LlmProvider.GEMINI -> GeminiScanner(
                apiKey = profile.apiKey,
                readTimeoutSeconds = profile.readTimeoutSeconds
            )
            LlmProvider.SILICONFLOW -> SiliconFlowScanner(
                apiKey = profile.apiKey,
                model = profile.model ?: "",
                connectTimeoutSeconds = profile.connectTimeoutSeconds,
                readTimeoutSeconds = profile.readTimeoutSeconds
            )
            LlmProvider.DEEPSEEK -> DeepSeekScanner(
                apiKey = profile.apiKey,
                model = profile.model ?: "",
                connectTimeoutSeconds = profile.connectTimeoutSeconds,
                readTimeoutSeconds = profile.readTimeoutSeconds
            )
        }

        cachedProfileId = profile.id
        cachedScanner = scanner
        return scanner
    }

}
