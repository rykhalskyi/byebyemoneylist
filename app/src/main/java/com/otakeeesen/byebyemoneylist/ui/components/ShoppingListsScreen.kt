package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListViewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.UiEvent

@Composable
fun ShoppingListsScreen(
    onAddItem: (Long) -> Unit = {},
    viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModel.Factory),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ItemDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Item deleted",
                        actionLabel = "Undo",
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            SpeedDialFab(
                onCreateList = { showCreateDialog = true },
                onInStore = { viewModel.inStore() },
                onDirectPurchase = { viewModel.directPurchase() },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(uiState.shoppingLists, key = { it.id }) { shoppingList ->
                ShoppingListCard(
                    shoppingList = shoppingList,
                    onItemCheckedChange = { item, checked ->
                        viewModel.toggleItemChecked(item, checked)
                    },
                    onAddItem = { onAddItem(shoppingList.id) },
                    onDeleteList = {
                        viewModel.deleteShoppingList(shoppingList)
                    },
                    onDeleteItem = { item ->
                        viewModel.deleteItem(item)
                    },
                    onFinishAndPay = {
                        viewModel.finishAndPay(shoppingList)
                    },
                    onReorderItems = { items ->
                        viewModel.reorderItems(shoppingList.id, items)
                    },
                )
            }
        }

        if (showCreateDialog) {
            CreateShoppingListDialog(
                categories = dialogState.categories,
                stores = dialogState.stores,
                onDismiss = { showCreateDialog = false },
                onConfirm = { name, categoryName, storeName ->
                    viewModel.createList(name, categoryName, storeName)
                    showCreateDialog = false
                },
            )
        }
    }
}
