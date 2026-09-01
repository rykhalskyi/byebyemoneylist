package com.otakeeesen.byebyemoneylist.ui.components.main

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.ui.res.stringResource
import com.otakeeesen.byebyemoneylist.ui.navigation.mainScreens
import com.otakeeesen.byebyemoneylist.ui.navigation.Screen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.otakeeesen.byebyemoneylist.R

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.BuildConfig

import com.otakeeesen.byebyemoneylist.ui.viewmodel.CatalogViewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.DashboardViewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListViewModel
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.DashboardScreen
import com.otakeeesen.byebyemoneylist.ui.components.shoppinglist.ShoppingListsScreen
import com.otakeeesen.byebyemoneylist.ui.components.analytics.AnalyticsScreen
import com.otakeeesen.byebyemoneylist.ui.components.catalog.CatalogScreen
import com.otakeeesen.byebyemoneylist.ui.components.catalog.ProductMergeSearchScreen
import com.otakeeesen.byebyemoneylist.ui.components.catalog.ProductMergeScreen
import com.otakeeesen.byebyemoneylist.ui.components.catalog.StoreMergeSearchScreen
import com.otakeeesen.byebyemoneylist.ui.components.catalog.StoreMergeScreen
import com.otakeeesen.byebyemoneylist.ui.components.product.ProductScreen
import com.otakeeesen.byebyemoneylist.ui.components.settings.CategorySyncScreen
import com.otakeeesen.byebyemoneylist.ui.components.settings.LlmSettingsScreen
import com.otakeeesen.byebyemoneylist.ui.components.settings.NextcloudSyncSettingsScreen
import com.otakeeesen.byebyemoneylist.ui.components.settings.SettingsScreen

import com.otakeeesen.byebyemoneylist.ui.components.product.AddProductScreen
import androidx.compose.material.icons.Icons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.QuickPurchaseScreen
import com.otakeeesen.byebyemoneylist.ui.components.shared.components.WelcomeDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    shoppingListViewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModel.Factory),
    catalogViewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.Factory),
    dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    val preferencesManager = remember { (context.applicationContext as ByeByeMoneyApplication).preferencesManager }
    val snackbarHostState = remember { SnackbarHostState() }
    val shoppingListUiState by shoppingListViewModel.uiState.collectAsStateWithLifecycle()

    var isDashboardEnabled by remember { mutableStateOf(preferencesManager.isDashboardEnabled()) }

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences("bye_bye_money_prefs", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "show_dashboard") {
                isDashboardEnabled = preferencesManager.isDashboardEnabled()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val screens = if (isDashboardEnabled) mainScreens else mainScreens.filterNot { it == Screen.Dashboard }

    val currentVersion = BuildConfig.VERSION_NAME
    val welcomeMessage = stringResource(R.string.welcome_to_version, currentVersion)

    LaunchedEffect(Unit) {
        val lastShownVersion = preferencesManager.getLastShownVersion()

        if (lastShownVersion != currentVersion) {
            snackbarHostState.showSnackbar(welcomeMessage)
            preferencesManager.setLastShownVersion(currentVersion)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = stringResource(screen.labelResId)) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isDashboardEnabled) Screen.Dashboard.route else Screen.Shopping.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    navController = navController
                )
            }
            composable(Screen.Shopping.route) { backStackEntry ->
                val openPurchase by backStackEntry.savedStateHandle
                    .getStateFlow("open_purchase_dialog", false)
                    .collectAsStateWithLifecycle()
                val autoStartScan by backStackEntry.savedStateHandle
                    .getStateFlow("auto_start_scan", false)
                    .collectAsStateWithLifecycle()
                var previousOpenPurchase by remember { mutableStateOf(false) }
                var previousAutoStartScan by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    val previousHandle = navController.previousBackStackEntry?.savedStateHandle
                    previousOpenPurchase = previousHandle?.get<Boolean>("open_purchase_dialog") == true
                    previousHandle?.remove<Boolean>("open_purchase_dialog")
                    previousAutoStartScan = previousHandle?.get<Boolean>("auto_start_scan") == true
                    previousHandle?.remove<Boolean>("auto_start_scan")
                    val categoryId = backStackEntry.savedStateHandle.get<Long>("filter_category_id")
                        ?: previousHandle?.get<Long>("filter_category_id")
                    if (categoryId != null) {
                        shoppingListViewModel.setCategoryFilters(setOf(categoryId))
                        shoppingListViewModel.showFilterPanel()
                    }
                    backStackEntry.savedStateHandle.remove<Long>("filter_category_id")
                    previousHandle?.remove<Long>("filter_category_id")
                }
                val openPurchaseDialog = openPurchase || previousOpenPurchase
                ShoppingListsScreen(
                    viewModel = shoppingListViewModel,
                    onAddItem = { listId ->
                        navController.navigate("add_product/$listId")
                    },
                    onNavigateToProduct = { productId ->
                        navController.navigate("product_detail/$productId")
                    },
                    openPurchaseDialog = openPurchaseDialog,
                    onOpenPurchaseDialogHandled = {
                        backStackEntry.savedStateHandle["open_purchase_dialog"] = false
                    },
                    autoScanPurchase = (autoStartScan || previousAutoStartScan) && openPurchaseDialog,
                    onAutoScanPurchaseHandled = {
                        backStackEntry.savedStateHandle["auto_start_scan"] = false
                    }
                )
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen(
                    onProductClick = { productId ->
                        navController.navigate("product_detail/$productId")
                    }
                )
            }
            composable(Screen.Catalog.route) {
                CatalogScreen(
                    onProductClick = { productId ->
                        navController.navigate("product_detail/$productId")
                    },
                    onAddProduct = { isSubscription, isIncome ->
                        navController.navigate("product_detail/-1?isSubscription=$isSubscription&isIncome=$isIncome")
                    },
                    onMergeStore = { id ->
                        navController.navigate("store_merge_search/$id")
                    }
                )
            }
            composable(
                route = Screen.ProductDetail.route,
                arguments = listOf(
                    navArgument("productId") { type = NavType.LongType },
                    navArgument("isSubscription") { 
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("isIncome") { 
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getLong("productId")?.takeIf { it != -1L }
                val isSubscriptionParam = backStackEntry.arguments?.getBoolean("isSubscription") ?: false
                val isIncomeParam = backStackEntry.arguments?.getBoolean("isIncome") ?: false
                val viewModel: com.otakeeesen.byebyemoneylist.ui.viewmodel.ProductViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = com.otakeeesen.byebyemoneylist.ui.viewmodel.ProductViewModel.createFactory(productId, isSubscriptionParam, isIncomeParam)
                )
                ProductScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onMerge = { id ->
                        navController.navigate("product_merge_search/$id")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToLlmSettings = { navController.navigate(Screen.LlmSettings.route) },
                    onNavigateToNextcloudSyncSettings = { navController.navigate(Screen.NextcloudSyncSettings.route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.LlmSettings.route) {
                LlmSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.NextcloudSyncSettings.route) {
                NextcloudSyncSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSyncCategories = { navController.navigate(Screen.CategorySync.route) }
                )
            }
            composable(Screen.CategorySync.route) {
                CategorySyncScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.AddProduct.route,
                arguments = listOf(navArgument("listId") { type = NavType.LongType })
            ) { backStackEntry ->
                val listId = backStackEntry.arguments?.getLong("listId") ?: 0L
                AddProductScreen(
                    listId = listId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.ProductMergeSearch.route,
                arguments = listOf(navArgument("productAId") { type = NavType.LongType })
            ) { backStackEntry ->
                val productAId = backStackEntry.arguments?.getLong("productAId") ?: 0L
                ProductMergeSearchScreen(
                    productAId = productAId,
                    onProductSelected = { productBId ->
                        navController.navigate("product_merge_detail/$productAId/$productBId")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.ProductMergeDetail.route,
                arguments = listOf(
                    navArgument("productAId") { type = NavType.LongType },
                    navArgument("productBId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val productAId = backStackEntry.arguments?.getLong("productAId") ?: 0L
                val productBId = backStackEntry.arguments?.getLong("productBId") ?: 0L
                ProductMergeScreen(
                    productAId = productAId,
                    productBId = productBId,
                    onMergeComplete = {
                        navController.popBackStack("catalog", inclusive = false)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.StoreMergeSearch.route,
                arguments = listOf(navArgument("storeAId") { type = NavType.LongType })
            ) { backStackEntry ->
                val storeAId = backStackEntry.arguments?.getLong("storeAId") ?: 0L
                StoreMergeSearchScreen(
                    storeAId = storeAId,
                    onStoreSelected = { storeBId ->
                        navController.navigate("store_merge_detail/$storeAId/$storeBId")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.QuickPurchase.route) {
                QuickPurchaseScreen(navController = navController)
            }
            composable(
                route = Screen.StoreMergeDetail.route,
                arguments = listOf(
                    navArgument("storeAId") { type = NavType.LongType },
                    navArgument("storeBId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val storeAId = backStackEntry.arguments?.getLong("storeAId") ?: 0L
                val storeBId = backStackEntry.arguments?.getLong("storeBId") ?: 0L
                StoreMergeScreen(
                    storeAId = storeAId,
                    storeBId = storeBId,
                    onMergeComplete = {
                        navController.popBackStack("catalog", inclusive = false)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (shoppingListUiState.showWelcomeDialog) {
        WelcomeDialog(
            onSetupCategories = { shoppingListViewModel.setupDefaultCategories(context) },
            onDismiss = { shoppingListViewModel.dismissWelcomeDialog() }
        )
    }
}
