package com.otakeeesen.byebyemoneylist.util

import android.content.Context
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import java.util.Locale

object CurrencyFormatter {

    fun format(price: Double, context: Context): String {
        val prefs = PreferencesManager(context)
        val savedSymbol = prefs.getCurrencySymbol()
        
        // If savedSymbol is null, it means no setting, use locale default.
        // If savedSymbol is "", it means user explicitly chose "None".
        // Otherwise, it's the chosen symbol.
        
        val symbol = when {
            savedSymbol == null -> getDefaultSymbolForLocale()
            savedSymbol == "" -> ""
            else -> savedSymbol
        }

        val isNegative = price < 0
        val absolutePrice = kotlin.math.abs(price)
        
        val formatted = if (symbol.isEmpty()) {
            "%.2f".format(absolutePrice)
        } else {
            "%s%.2f".format(symbol, absolutePrice)
        }
        
        return if (isNegative) "-$formatted" else formatted
    }

    private fun getDefaultSymbolForLocale(): String {
        return when (Locale.getDefault().language) {
            "uk" -> "₴"
            "de" -> "€"
            "en" -> "$" // Defaulting to $ for English, could be refined
            else -> "€" // Default fallback
        }
    }
}
