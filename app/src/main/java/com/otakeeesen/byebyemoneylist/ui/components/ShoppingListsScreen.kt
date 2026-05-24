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
import com.otakeeesen.byebyemoneylist.ui.components.FinishAndPayDialog
import com.otakeeesen.byebyemoneylist.ui.components.WelcomeDialog
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
                 .padding(bottom = innerPadding.calculateBottomPadding()),
             contentPadding = PaddingValues(top = 0.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
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
                             totalPrice = item.totalPrice,
                             onClick = { viewModel.toggleYearExpansion(item.year) }
                         )
                     }
                     is ShoppingListItem.MonthHeader -> {
                         MonthHeader(
                             monthName = item.monthName,
                             isExpanded = item.isExpanded,
                             totalPrice = item.totalPrice,
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
                                 onEditList = {
                                     viewModel.startEditingList(item.shoppingList)
                                 },
                                 onDeleteItem = { item ->
                                     viewModel.deleteItem(item)
                                 },
                                 onEditItem = { item ->
                                     viewModel.startEditingItem(item)
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

         // Finish & Pay Dialog
         if (uiState.showFinishAndPayDialog && uiState.selectedShoppingList != null) {
             FinishAndPayDialog(
                 shoppingList = uiState.selectedShoppingList!!,
                 onConfirm = { total ->
                     viewModel.onFinishAndPayConfirm(total)
                 },
                 onDismiss = {
                     viewModel.dismissFinishAndPayDialog()
                 }
             )
         }

         if (uiState.showWelcomeDialog) {
             WelcomeDialog(
                 onDismiss = { viewModel.dismissWelcomeDialog() }
             )
         }

        if (uiState.editingList != null) {
            CreateShoppingListDialog(
                categories = dialogState.categories,
                stores = dialogState.stores,
                onDismiss = { viewModel.stopEditingList() },
                onConfirm = { name, categoryName, storeName ->
                    viewModel.updateList(uiState.editingList!!, name, categoryName, storeName)
                },
                initialName = uiState.editingList!!.title,
                initialCategory = uiState.editingList!!.categoryName ?: "",
                initialStore = uiState.editingList!!.storeName ?: "",
            )
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

        if (uiState.editingItem != null) {
            EditPurchaseItemDialog(
                item = uiState.editingItem!!,
                onDismiss = { viewModel.stopEditingItem() },
                onConfirm = { name, price, imageUrl ->
                    viewModel.updatePurchaseItem(uiState.editingItem!!, name, price, imageUrl)
                }
            )
        }
    }
}
