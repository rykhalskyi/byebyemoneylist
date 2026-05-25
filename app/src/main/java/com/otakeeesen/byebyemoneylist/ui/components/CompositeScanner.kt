package com.otakeeesen.byebyemoneylist.ui.components

import android.graphics.Bitmap
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager

class CompositeScanner(private val preferencesManager: PreferencesManager) : ReceiptParser {

    override suspend fun parse(bitmap: Bitmap): ScannedReceipt {
        val provider = preferencesManager.getLlmProvider()
        
        val llmScanner = when (provider) {
            "GEMINI" -> {
                val key = preferencesManager.getGeminiApiKey()
                if (key.isNotBlank()) GeminiScanner(key) else null
            }
            "SILICONFLOW" -> {
                val key = preferencesManager.getSiliconFlowApiKey()
                val model = preferencesManager.getSiliconFlowModel()
                if (key.isNotBlank()) SiliconFlowScanner(key, model) else null
            }
            else -> null
        }

        if (llmScanner != null) {
            val result = llmScanner.parse(bitmap)
            if (result.totalSum != null || result.items.isNotEmpty()) {
                return result
            }
        }

        // Fallback to ML Kit
        return MlKitScanner().parse(bitmap)
    }
}
