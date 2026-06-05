package com.otakeeesen.byebyemoneylist.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.otakeeesen.byebyemoneylist.R

sealed class Screen(val route: String, val labelResId: Int, val icon: ImageVector) {
    object Shopping : Screen("shopping", R.string.nav_shopping, Icons.Default.ShoppingCart)
    object Analytics : Screen("analytics", R.string.nav_analytics, Icons.Default.Analytics)
    object Catalog : Screen("catalog", R.string.nav_catalog, Icons.AutoMirrored.Filled.List)
    object ProductDetail : Screen("product_detail/{productId}", R.string.nav_product_detail, Icons.Default.Add)
    object AddProduct : Screen("add_product/{listId}", R.string.nav_add_product, Icons.Default.Add)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
    object ProductMergeSearch : Screen("product_merge_search/{productAId}", R.string.select_product_to_merge, Icons.Default.Add)
    object ProductMergeDetail : Screen("product_merge_detail/{productAId}/{productBId}", R.string.merge_products_title, Icons.Default.Add)
}

val mainScreens = listOf(
    Screen.Shopping,
    Screen.Analytics,
    Screen.Catalog,
    Screen.Settings
)
