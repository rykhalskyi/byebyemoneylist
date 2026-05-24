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
}
