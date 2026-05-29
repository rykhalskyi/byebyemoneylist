package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.otakeeesen.byebyemoneylist.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import android.Manifest

import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    var showPurchaseDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesManager = remember { (context.applicationContext as ByeByeMoneyApplication).preferencesManager }
    val scanner = remember { CompositeScanner(preferencesManager) }

    var isScanning by remember { mutableStateOf(false) }
    var scannedReceiptResult by remember { mutableStateOf<ScannedReceipt?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            isScanning = true
            coroutineScope.launch {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, tempPhotoUri!!))
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, tempPhotoUri!!)
                }
                
                val result = scanner.parse(bitmap)
                if (result.errorMessage != null) {
                    val displayError = when {
                        result.errorMessage!!.contains("404") -> "Scanner model update required: Please check app updates."
                        result.errorMessage!!.contains("Failed to parse") -> "The receipt format could not be processed. Please try again."
                        else -> "Scan failed: ${result.errorMessage}"
                    }
                    Toast.makeText(context, displayError, Toast.LENGTH_LONG).show()
                }
                scannedReceiptResult = result
                isScanning = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && tempPhotoUri != null) {
            scanLauncher.launch(tempPhotoUri!!)
        }
    }

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

    LaunchedEffect(uiState.inStoreListIds) {
        uiState.inStoreListIds.forEach { listId ->
            if (!uiState.expandedCards.contains(listId)) {
                viewModel.toggleCardExpansion(listId)
            }
            // Find index and scroll
            val index = localDisplayItems.indexOfFirst { item ->
                item is ShoppingListItem.ListContent && item.shoppingList.id == listId
            }
            if (index != -1) {
                lazyListState.animateScrollToItem(index)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { viewModel.resetSorting() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset Sorting")
                }
            }
        },
        floatingActionButton = {
            SpeedDialFab(
                onCreateList = { showCreateDialog = true },
                onInStore = { viewModel.inStore() },
                onPurchase = { showPurchaseDialog = true },
            )
        },
     ) { innerPadding ->
         LazyColumn(
             state = lazyListState,
             modifier = Modifier
                 .fillMaxSize()
                 .padding(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding()),
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
                                 isInStore = uiState.inStoreListIds.contains(item.shoppingList.id),
                                 onToggleExpand = { viewModel.toggleCardExpansion(item.shoppingList.id) },
                                 onToggleStoreMode = { viewModel.toggleInStoreMode(item.shoppingList.id) },
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

         if (isScanning) {
             LoadingDialog()
         }

         // Finish & Pay Dialog
         if (uiState.showFinishAndPayDialog && uiState.selectedShoppingList != null) {             FinishAndPayDialog(
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

        if (showPurchaseDialog) {
            PurchaseDialog(
                shoppingLists = uiState.shoppingLists,
                stores = dialogState.stores,
                products = dialogState.products,
                aliases = dialogState.aliases,
                onDismiss = { 
                    showPurchaseDialog = false
                    scannedReceiptResult = null 
                },
                onConfirm = { listId, listName, storeName, price, items ->
                    viewModel.processPurchase(listId, listName, storeName, price, items)
                    showPurchaseDialog = false
                    scannedReceiptResult = null
                },
                onScanRequest = {
                    val photoFile = File(context.cacheDir, "receipt_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg")
                    tempPhotoUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        photoFile
                    )
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                scannedReceipt = scannedReceiptResult
            )
        }

        if (uiState.showInStoreDialog) {
            SelectStoreAndListDialog(
                shoppingLists = uiState.shoppingLists,
                stores = dialogState.stores,
                onDismiss = { viewModel.dismissInStoreDialog() },
                onConfirm = { listId -> viewModel.enterStoreMode(listId) },
                onCreateStore = { name, onResult -> viewModel.createStore(name, onResult) },
                onCreateShoppingList = { name, storeId, onResult -> viewModel.createShoppingList(name, storeId, onResult) }
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
