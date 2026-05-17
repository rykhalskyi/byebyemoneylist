package com.otakeeesen.byebyemoneylist.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Shopping : Screen("shopping", "Shopping", Icons.Default.ShoppingCart)
    object Analytics : Screen("analytics", "Analytics", Icons.Default.Analytics)
    object Catalog : Screen("catalog", "Catalog", Icons.AutoMirrored.Filled.List)
    object AddProduct : Screen("add_product/{listId}", "Add Product", Icons.Default.Add)
}

val mainScreens = listOf(
    Screen.Shopping,
    Screen.Analytics,
    Screen.Catalog
)
