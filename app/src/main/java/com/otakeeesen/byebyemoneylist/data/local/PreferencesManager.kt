package com.otakeeesen.byebyemoneylist.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import com.otakeeesen.byebyemoneylist.data.LlmProfile
import com.otakeeesen.byebyemoneylist.data.LlmProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bye_bye_money_prefs", Context.MODE_PRIVATE)

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bye_bye_money_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun getLastShownVersion(): String? {
        return prefs.getString("last_shown_version", null)
    }

    fun setLastShownVersion(version: String) {
        prefs.edit().putString("last_shown_version", version).apply()
    }

    fun isLlmConsentGranted(): Boolean {
        return prefs.getBoolean("llm_consent_granted", false)
    }

    fun setLlmConsentGranted(granted: Boolean) {
        prefs.edit().putBoolean("llm_consent_granted", granted).apply()
    }

    fun isLlmConsentDismissed(): Boolean {
        return prefs.getBoolean("llm_consent_dismissed", false)
    }

    fun setLlmConsentDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean("llm_consent_dismissed", dismissed).apply()
    }

    fun getCurrencySymbol(): String? {
        return prefs.getString("currency_symbol", null)
    }

    fun setCurrencySymbol(symbol: String?) {
        prefs.edit().putString("currency_symbol", symbol).apply()
    }

    fun getActualPriceRule(): String {
        return prefs.getString("actual_price_rule", "PURCHASE_PRICE") ?: "PURCHASE_PRICE"
    }

    fun setActualPriceRule(rule: String) {
        prefs.edit().putString("actual_price_rule", rule).apply()
    }

    fun getActiveProfileId(): String? {
        val activeId = encryptedPrefs.getString("active_llm_profile_id", null)
        if (activeId == null && com.otakeeesen.byebyemoneylist.BuildConfig.SILICON_FLOW_KEY.isNotBlank()) {
            return LlmProfile.DEFAULT_SILICON_FLOW_PROFILE_ID
        }
        return activeId
    }

    fun setActiveProfileId(id: String?) {
        encryptedPrefs.edit().putString("active_llm_profile_id", id).apply()
    }

    fun getLlmProfiles(): List<LlmProfile> {
        val jsonString = encryptedPrefs.getString("llm_profiles", null)
        val profiles = if (jsonString != null) {
            try {
                json.decodeFromString<List<LlmProfile>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            val migrated = migrateLegacySettings()
            if (migrated != null) {
                val migratedList = listOf(migrated)
                saveLlmProfiles(migratedList)
                migratedList
            } else {
                emptyList()
            }
        }

        return if (com.otakeeesen.byebyemoneylist.BuildConfig.SILICON_FLOW_KEY.isNotBlank()) {
            if (profiles.any { it.id == LlmProfile.DEFAULT_SILICON_FLOW_PROFILE_ID }) {
                profiles
            } else {
                val defaultProfile = LlmProfile(
                    id = LlmProfile.DEFAULT_SILICON_FLOW_PROFILE_ID,
                    name = "Closed Test Key",
                    provider = LlmProvider.SILICONFLOW,
                    apiKey = com.otakeeesen.byebyemoneylist.BuildConfig.SILICON_FLOW_KEY,
                    model = LlmProfile.DEFAULT_SILICONFLOW_MODEL
                )
                profiles + defaultProfile
            }
        } else {
            profiles
        }
    }

    fun saveLlmProfiles(profiles: List<LlmProfile>) {
        val jsonString = json.encodeToString(profiles)
        encryptedPrefs.edit().putString("llm_profiles", jsonString).apply()
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
            prefs.getString("siliconflow_model", LlmProfile.DEFAULT_SILICONFLOW_MODEL)
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
        return prefs.getString("siliconflow_model", LlmProfile.DEFAULT_SILICONFLOW_MODEL) ?: LlmProfile.DEFAULT_SILICONFLOW_MODEL
    }

    @Deprecated("Use profiles")
    fun setSiliconFlowModel(model: String) {
        prefs.edit().putString("siliconflow_model", model).apply()
    }
}
