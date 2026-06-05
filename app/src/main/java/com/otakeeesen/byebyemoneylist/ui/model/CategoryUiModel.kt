package com.otakeeesen.byebyemoneylist.ui.model

import androidx.compose.ui.graphics.Color

data class CategoryUiModel(
    val id: Long,
    val name: String,
    val color: Color,
    val parentId: Long?
)
