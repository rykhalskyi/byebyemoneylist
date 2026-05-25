package com.otakeeesen.byebyemoneylist.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bye_bye_money_prefs", Context.MODE_PRIVATE)

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

    fun getLlmProvider(): String {
        return prefs.getString("llm_provider", "NONE") ?: "NONE"
    }

    fun setLlmProvider(provider: String) {
        prefs.edit().putString("llm_provider", provider).apply()
    }

    fun getGeminiApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    fun getSiliconFlowApiKey(): String {
        return prefs.getString("siliconflow_api_key", "") ?: ""
    }

    fun setSiliconFlowApiKey(key: String) {
        prefs.edit().putString("siliconflow_api_key", key).apply()
    }

    fun getSiliconFlowModel(): String {
        return prefs.getString("siliconflow_model", "Qwen/Qwen3-VL-32B-Instruct") ?: "Qwen/Qwen3-VL-32B-Instruct"
    }

    fun setSiliconFlowModel(model: String) {
        prefs.edit().putString("siliconflow_model", model).apply()
    }
}
