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
