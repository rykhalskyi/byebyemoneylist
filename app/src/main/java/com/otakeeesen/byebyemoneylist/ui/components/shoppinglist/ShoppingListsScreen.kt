package com.otakeeesen.byebyemoneylist.ui.components.shoppinglist

import android.Manifest
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.util.PdfToBitmapConverter
import com.otakeeesen.byebyemoneylist.ui.components.scanner.CompositeScanner
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedReceipt
import com.otakeeesen.byebyemoneylist.ui.components.scanner.EditScannedItemDialog
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ReceiptReviewDialog
import com.otakeeesen.byebyemoneylist.ui.components.shared.ErrorDialog
import com.otakeeesen.byebyemoneylist.ui.components.shared.LoadingDialog
import com.otakeeesen.byebyemoneylist.ui.components.product.PurchaseDialog
import com.otakeeesen.byebyemoneylist.ui.components.review.ReviewListDialog
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.ui.components.components.SpeedDialFab
import com.otakeeesen.byebyemoneylist.ui.components.components.components.MonthHeader
import com.otakeeesen.byebyemoneylist.ui.components.components.components.YearHeader
import com.otakeeesen.byebyemoneylist.ui.components.product.EditPurchaseItemDialog
import com.otakeeesen.byebyemoneylist.ui.components.shared.components.WelcomeDialog
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListItem
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListViewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListsScreen(
    onAddItem: (Long) -> Unit = {},
    onNavigateToProduct: (Long) -> Unit = {},
    viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModel.Factory),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var purchaseShoppingList by remember { mutableStateOf<ShoppingList?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesManager = remember { (context.applicationContext as ByeByeMoneyApplication).preferencesManager }
    val scanner = remember { CompositeScanner(preferencesManager) }

    var isScanning by remember { mutableStateOf(false) }
    var scannedReceiptResult by remember { mutableStateOf<ScannedReceipt?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var scannerError by remember { mutableStateOf<String?>(null) }

    fun processImageUri(uri: Uri) {
        isScanning = true
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }

                    val catNames = dialogState.categories.map { it.name }
                    scanner.parse(bitmap, catNames)
                }
                
                if (result.errorMessage != null) {
                    scannerError = result.errorMessage
                }
                scannedReceiptResult = result
            } catch (e: Exception) {
                scannerError = e.message ?: "Failed to process image"
            } finally {
                isScanning = false
            }
        }
    }

    fun processPdfUri(uri: Uri) {
        isScanning = true
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    PdfToBitmapConverter.convertPdfToBitmap(context, uri)
                }

                when (result) {
                    is PdfToBitmapConverter.ConversionResult.Success -> {
                        val bitmap = result.bitmap
                        val catNames = dialogState.categories.map { it.name }
                        val scannedReceipt = withContext(Dispatchers.IO) {
                            scanner.parse(bitmap, catNames)
                        }
                        
                        if (scannedReceipt.errorMessage != null) {
                            scannerError = scannedReceipt.errorMessage
                        }
                        scannedReceiptResult = scannedReceipt
                    }
                    is PdfToBitmapConverter.ConversionResult.Error -> {
                        scannerError = "PDF conversion failed: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                scannerError = e.message ?: "Failed to process PDF"
            } finally {
                isScanning = false
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            processImageUri(tempPhotoUri!!)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processImageUri(it) }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processPdfUri(it) }
    }

    if (scannerError != null) {
        ErrorDialog(
            title = "Scan Error",
            errorMessage = scannerError!!,
            onDismiss = { scannerError = null }
        )
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

    val itemDeletedMessage = stringResource(R.string.snackbar_item_deleted)
    val undoActionLabel = stringResource(R.string.snackbar_undo)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ItemDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = itemDeletedMessage,
                        actionLabel = undoActionLabel,
                        duration = SnackbarDuration.Short
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
                Row {
                    IconButton(onClick = { viewModel.toggleSearchPanel() }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Toggle Search",
                            tint = if (uiState.filterQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFilterPanel() }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Toggle Filter",
                            tint = if (uiState.selectedCategoryIds.isNotEmpty() || uiState.filterRecurring != null)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.toggleSortOrder() }) {
                        Icon(
                            imageVector = if (uiState.isSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = "Toggle Sorting"
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            SpeedDialFab(
                onCreateList = { showCreateDialog = true },
                onPurchase = { showPurchaseDialog = true },
            )
        },
     ) { innerPadding ->
         Column(modifier = Modifier.padding(innerPadding)) {
             AnimatedVisibility(
                 visible = uiState.showSearchPanel,
                 enter = expandVertically(),
                 exit = shrinkVertically()
             ) {
                 SearchPanel(
                     query = uiState.filterQuery,
                     onQueryChange = { viewModel.updateFilterQuery(it) }
                 )
             }

             AnimatedVisibility(
                 visible = uiState.showFilterPanel,
                 enter = expandVertically(),
                 exit = shrinkVertically()
             ) {
                 FilterPanel(
                     selectedCategoryIds = uiState.selectedCategoryIds,
                     onCategoryClick = { viewModel.toggleCategoryFilter(it) },
                     allCategories = dialogState.categories,
                     filterRecurring = uiState.filterRecurring,
                     onRecurringFilterChange = { viewModel.updateRecurringFilter(it) },
                     filterStatus = uiState.filterStatus,
                     onStatusFilterChange = { viewModel.updateStatusFilter(it) },
                     onClearFilters = { viewModel.clearFilters() }
                 )
             }

             LazyColumn(
                 state = lazyListState,
                 modifier = Modifier.fillMaxSize(),
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
                                     actualPriceRule = viewModel.preferencesManager.getActualPriceRule(),
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
                                     onReviewList = {
                                         viewModel.startReview(item.shoppingList)
                                     },
                                     onUnarchiveList = {
                                         viewModel.unarchiveList(item.shoppingList)
                                     },
                                     onFinishAndPay = {
                                         purchaseShoppingList = item.shoppingList
                                         showPurchaseDialog = true
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
         }

         if (isScanning) {
             LoadingDialog()
         }

         if (uiState.showWelcomeDialog) {
             WelcomeDialog(
                 onSetupCategories = { viewModel.setupDefaultCategories(context) },
                 onDismiss = { viewModel.dismissWelcomeDialog() }
             )
         }

        if (uiState.editingList != null) {
            CreateShoppingListDialog(
                categories = dialogState.categories,
                stores = dialogState.stores,
                onDismiss = { viewModel.stopEditingList() },
                onConfirm = { name, categoryIds, storeName, isRecurring, recurringPeriod, isForwardEmpty, isSubscription ->
                    viewModel.updateList(uiState.editingList!!, name, categoryIds, storeName, isRecurring, recurringPeriod, isForwardEmpty, isSubscription)
                },
                initialName = uiState.editingList!!.title,
                initialCategories = uiState.editingList!!.categories,
                initialStore = uiState.editingList!!.storeName ?: "",
                initialIsRecurring = uiState.editingList!!.isRecurring,
                initialRecurringPeriod = uiState.editingList!!.recurringPeriod,
                initialIsForwardEmpty = uiState.editingList!!.isForwardEmpty,
                initialIsSubscription = uiState.editingList!!.isSubscription,
            )
        }

        if (showCreateDialog) {
            CreateShoppingListDialog(
                categories = dialogState.categories,
                stores = dialogState.stores,
                onDismiss = { showCreateDialog = false },
                onConfirm = { name, categoryIds, storeName, isRecurring, recurringPeriod, isForwardEmpty, isSubscription ->
                    viewModel.createList(name, categoryIds, storeName, isRecurring, recurringPeriod, isForwardEmpty, isSubscription)
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
                initialShoppingList = purchaseShoppingList,
                onDismiss = { 
                    showPurchaseDialog = false
                    purchaseShoppingList = null
                    scannedReceiptResult = null 
                },
                onConfirm = { listId, listName, storeName, price, items ->
                    viewModel.processPurchase(listId, listName, storeName, price, items)
                    showPurchaseDialog = false
                    purchaseShoppingList = null
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
                onGalleryRequest = {
                    galleryLauncher.launch("image/*")
                },
                onPdfRequest = {
                    pdfLauncher.launch("application/pdf")
                },
                scannedReceipt = scannedReceiptResult
            )
        }

        if (uiState.editingItem != null) {
            EditPurchaseItemDialog(
                item = uiState.editingItem!!,
                onDismiss = { viewModel.stopEditingItem() },
                onConfirm = { price, quantity ->
                    viewModel.updatePurchaseItem(uiState.editingItem!!, price, quantity)
                },
                onEditProduct = { productId ->
                    viewModel.stopEditingItem()
                    onNavigateToProduct(productId)
                }
            )
        }

        if (uiState.showReviewDialog && uiState.selectedReviewList != null) {
            ReviewListDialog(
                shoppingList = uiState.selectedReviewList!!,
                onDismiss = { viewModel.stopReview() },
                onUpdateItem = { item, name, price, quantity, barcode, categoryId ->
                    viewModel.updateReviewedItem(item, name, price, quantity, barcode, categoryId)
                },
                onMapToExisting = { item, product, newName, newPrice, newQuantity, newBarcode, categoryId ->
                    viewModel.mapToExistingProduct(item, product, newName, newPrice, newQuantity, newBarcode, categoryId)
                },
                onDeleteItem = { viewModel.deleteItem(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search lists...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            } else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPanel(
    selectedCategoryIds: Set<Long>,
    onCategoryClick: (Long) -> Unit,
    allCategories: List<CategoryEntity>,
    filterRecurring: Boolean?,
    onRecurringFilterChange: (Boolean?) -> Unit,
    filterStatus: ShoppingListViewModel.ListStatusFilter,
    onStatusFilterChange: (ShoppingListViewModel.ListStatusFilter) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (allCategories.isNotEmpty()) {
            Text("Categories", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allCategories, key = { it.id }) { category ->
                    val isSelected = category.id in selectedCategoryIds
                    val categoryColor = try {
                        Color(android.graphics.Color.parseColor(category.color))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategoryClick(category.id) },
                        label = { Text(category.name) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = categoryColor.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }

        Text("Status & Type", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Statuses
            item {
                FilterChip(
                    selected = filterStatus == ShoppingListViewModel.ListStatusFilter.NEW,
                    onClick = { onStatusFilterChange(ShoppingListViewModel.ListStatusFilter.NEW) },
                    label = { Text(stringResource(R.string.cd_status_new)) }
                )
            }
            /*
            item {
                FilterChip(
                    selected = filterStatus == ShoppingListViewModel.ListStatusFilter.IN_STORE,
                    onClick = { onStatusFilterChange(ShoppingListViewModel.ListStatusFilter.IN_STORE) },
                    label = { Text(stringResource(R.string.in_store)) }
                )
            }
            */

            item {
                FilterChip(
                    selected = filterStatus == ShoppingListViewModel.ListStatusFilter.FINISHED,
                    onClick = { onStatusFilterChange(ShoppingListViewModel.ListStatusFilter.FINISHED) },
                    label = { Text(stringResource(R.string.cd_status_finished)) }
                )
            }
            item {
                FilterChip(
                    selected = filterStatus == ShoppingListViewModel.ListStatusFilter.ARCHIVED,
                    onClick = { onStatusFilterChange(ShoppingListViewModel.ListStatusFilter.ARCHIVED) },
                    label = { Text(stringResource(R.string.cd_status_archived)) }
                )
            }

            // Types
            item {
                FilterChip(
                    selected = filterRecurring == true,
                    onClick = { onRecurringFilterChange(true) },
                    label = { Text(stringResource(R.string.recurring)) }
                )
            }
            /*
            item {
                FilterChip(
                    selected = filterRecurring == false,
                    onClick = { onRecurringFilterChange(false) },
                    label = { Text(stringResource(R.string.regular)) }
                )
            }*/
        }
/*
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClearFilters) {
                Text(stringResource(R.string.clear_all))
            }
        }*/
    }
}
