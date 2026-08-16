package com.otakeeesen.byebyemoneylist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String = CategoryColors.DEFAULT_COLOR,
    val parentId: Long? = null,
    val isIncome: Boolean = false,
    val emoji: String? = null,
)
