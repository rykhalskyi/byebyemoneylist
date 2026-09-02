package com.otakeeesen.byebyemoneylist.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

fun safeParseColor(colorString: String?, defaultColor: Color = Color.Gray): Color {
    if (colorString.isNullOrBlank()) return defaultColor
    return try {
        Color(AndroidColor.parseColor(colorString))
    } catch (e: Exception) {
        defaultColor
    }
}

fun toHexString(color: Color): String {
    return String.format("#%08X", color.toArgb())
}

fun toServerColorHex(color: String?): String? {
    if (color.isNullOrBlank()) return color
    val cleaned = color.trimStart('#')
    return when (cleaned.length) {
        6 -> "#${cleaned.uppercase()}"
        8 -> "#${cleaned.substring(2).uppercase()}"
        else -> color
    }
}

fun toLocalColorHex(color: String?): String? {
    if (color.isNullOrBlank()) return color
    val cleaned = color.trimStart('#')
    return when (cleaned.length) {
        6 -> "#FF${cleaned.uppercase()}"
        else -> color
    }
}
