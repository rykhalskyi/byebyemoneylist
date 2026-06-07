package com.otakeeesen.byebyemoneylist.data.local

import android.content.Context
import android.content.SharedPreferences

import com.otakeeesen.byebyemoneylist.data.LlmProfile
import com.otakeeesen.byebyemoneylist.data.LlmProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bye_bye_money_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getLastShownVersion(): String? {
        return prefs.getString("last_shown_version", null)
    }

    fun setLastShownVersion(version: String) {
        prefs.edit().putString("last_shown_version", version).apply()
    }

    fun getHideCheckedItems(): Boolean {
        return prefs.getBoolean("hide_checked_items", false)
    }

    fun setHideCheckedItems(hide: Boolean) {
        prefs.edit().putBoolean("hide_checked_items", hide).apply()
    }

    fun getActualPriceRule(): String {
        return prefs.getString("actual_price_rule", "PURCHASE_PRICE") ?: "PURCHASE_PRICE"
    }

    fun setActualPriceRule(rule: String) {
        prefs.edit().putString("actual_price_rule", rule).apply()
    }

    fun getActiveProfileId(): String? {
        return prefs.getString("active_llm_profile_id", null)
    }

    fun setActiveProfileId(id: String?) {
        prefs.edit().putString("active_llm_profile_id", id).apply()
    }

    fun getLlmProfiles(): List<LlmProfile> {
        val jsonString = prefs.getString("llm_profiles", null)
        return if (jsonString != null) {
            try {
                json.decodeFromString<List<LlmProfile>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            val migrated = migrateLegacySettings()
            if (migrated != null) {
                saveLlmProfiles(listOf(migrated))
                listOf(migrated)
            } else {
                emptyList()
            }
        }
    }

    fun saveLlmProfiles(profiles: List<LlmProfile>) {
        val jsonString = json.encodeToString(profiles)
        prefs.edit().putString("llm_profiles", jsonString).apply()
    }

    private fun migrateLegacySettings(): LlmProfile? {
        val providerStr = prefs.getString("llm_provider", "NONE") ?: "NONE"
        if (providerStr == "NONE") return null

        val provider = try { LlmProvider.valueOf(providerStr) } catch (e: Exception) { return null }
        val apiKey = when (provider) {
            LlmProvider.GEMINI -> prefs.getString("gemini_api_key", "") ?: ""
            LlmProvider.SILICONFLOW -> prefs.getString("siliconflow_api_key", "") ?: ""
        }
        
        if (apiKey.isBlank()) return null

        val model = if (provider == LlmProvider.SILICONFLOW) {
            prefs.getString("siliconflow_model", "Qwen/Qwen3-VL-32B-Instruct")
        } else null

        val legacyProfile = LlmProfile(
            name = "Legacy Settings",
            provider = provider,
            apiKey = apiKey,
            model = model
        )
        
        // Mark as active since it was active before
        setActiveProfileId(legacyProfile.id)
        
        // Clean up legacy keys
        prefs.edit()
            .remove("llm_provider")
            .remove("gemini_api_key")
            .remove("siliconflow_api_key")
            .remove("siliconflow_model")
            .apply()

        return legacyProfile
    }

    @Deprecated("Use getLlmProfiles and getActiveProfileId")
    fun getLlmProvider(): String {
        return prefs.getString("llm_provider", "NONE") ?: "NONE"
    }

    @Deprecated("Use saveLlmProfiles")
    fun setLlmProvider(provider: String) {
        prefs.edit().putString("llm_provider", provider).apply()
    }

    @Deprecated("Use profiles")
    fun getGeminiApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    @Deprecated("Use profiles")
    fun setGeminiApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    @Deprecated("Use profiles")
    fun getSiliconFlowApiKey(): String {
        return prefs.getString("siliconflow_api_key", "") ?: ""
    }

    @Deprecated("Use profiles")
    fun setSiliconFlowApiKey(key: String) {
        prefs.edit().putString("siliconflow_api_key", key).apply()
    }

    @Deprecated("Use profiles")
    fun getSiliconFlowModel(): String {
        return prefs.getString("siliconflow_model", "Qwen/Qwen3-VL-32B-Instruct") ?: "Qwen/Qwen3-VL-32B-Instruct"
    }

    @Deprecated("Use profiles")
    fun setSiliconFlowModel(model: String) {
        prefs.edit().putString("siliconflow_model", model).apply()
    }
}
