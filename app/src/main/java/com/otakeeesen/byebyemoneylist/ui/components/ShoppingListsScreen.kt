package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListItem
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListViewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.UiEvent
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    var showDirectPurchaseDialog by remember { mutableStateOf(false) }

    var localDisplayItems by remember(uiState.displayItems) { mutableStateOf(uiState.displayItems) }
    var isAnyDragging by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.displayItems, isAnyDragging) {
        if (!isAnyDragging) {
            localDisplayItems = uiState.displayItems
        }
    }

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

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localDisplayItems = localDisplayItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            SpeedDialFab(
                onCreateList = { showCreateDialog = true },
                onInStore = { viewModel.inStore() },
                onDirectPurchase = { showDirectPurchaseDialog = true },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(localDisplayItems, key = { item ->
                when (item) {
                    is ShoppingListItem.YearHeader -> "year-${item.year}"
                    is ShoppingListItem.MonthHeader -> "month-${item.yearMonth}"
                    is ShoppingListItem.ListContent -> "list-${item.shoppingList.id}"
                }
            }) { item ->
                when (item) {
                    is ShoppingListItem.YearHeader -> {
                        YearHeader(
                            year = item.year,
                            isExpanded = item.isExpanded,
                            onClick = { viewModel.toggleYearExpansion(item.year) }
                        )
                    }
                    is ShoppingListItem.MonthHeader -> {
                        MonthHeader(
                            monthName = item.monthName,
                            isExpanded = item.isExpanded,
                            onClick = {
                                viewModel.toggleMonthExpansion(item.yearMonth)
                            }
                        )
                    }
                    is ShoppingListItem.ListContent -> {
                        ReorderableItem(
                            state = reorderableLazyListState,
                            key = "list-${item.shoppingList.id}",
                        ) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)

                            ShoppingListCard(
                                shoppingList = item.shoppingList,
                                isExpanded = uiState.expandedCards.contains(item.shoppingList.id),
                                onToggleExpand = { viewModel.toggleCardExpansion(item.shoppingList.id) },
                                onItemCheckedChange = { item, checked ->
                                    viewModel.toggleItemChecked(item, checked)
                                },
                                onAddItem = { onAddItem(item.shoppingList.id) },
                                onDeleteList = {
                                    viewModel.deleteShoppingList(item.shoppingList)
                                },
                                onDeleteItem = { item ->
                                    viewModel.deleteItem(item)
                                },
                                onFinishAndPay = {
                                    viewModel.finishAndPay(item.shoppingList)
                                },
                                onReorderItems = { items ->
                                    viewModel.reorderItems(item.shoppingList.id, items)
                                },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = { isAnyDragging = true },
                                    onDragStopped = {
                                        isAnyDragging = false
                                        val lists = localDisplayItems
                                            .filterIsInstance<ShoppingListItem.ListContent>()
                                            .map { it.shoppingList }
                                        viewModel.reorderLists(lists)
                                    }
                                ),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
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

        if (showDirectPurchaseDialog) {
            DirectPurchaseDialog(
                shoppingLists = uiState.shoppingLists,
                stores = dialogState.stores,
                onDismiss = { showDirectPurchaseDialog = false },
                onConfirm = { listId, listName, storeName, price ->
                    viewModel.processDirectPurchase(listId, listName, storeName, price)
                    showDirectPurchaseDialog = false
                }
            )
        }
    }
}
