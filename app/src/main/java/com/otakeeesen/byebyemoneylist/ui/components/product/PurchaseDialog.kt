package com.otakeeesen.byebyemoneylist.ui.components.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.ui.components.components.SmartSelectField
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedItem
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedReceipt
import com.otakeeesen.byebyemoneylist.ui.viewmodel.PurchaseDialogViewModel

enum class PurchaseMode {
    MANUAL, SCAN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDialog(
    shoppingLists: List<ShoppingList>,
    stores: List<StoreEntity>,
    products: List<ProductEntity> = emptyList(),
    aliases: List<com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity> = emptyList(),
    initialShoppingList: ShoppingList? = null,
    onDismiss: () -> Unit,
    onConfirm: (listId: Long?, listName: String?, storeName: String, price: Double, items: List<ScannedItem>) -> Unit,
    onScanRequest: () -> Unit = {},
    onGalleryRequest: () -> Unit = {},
    onPdfRequest: () -> Unit = {},
    scannedReceipt: ScannedReceipt? = null,
    viewModel: PurchaseDialogViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val unfinishedLists = remember(shoppingLists) { shoppingLists.filter { !it.isFinished } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferencesManager = remember { (context.applicationContext as com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication).preferencesManager }
    val isLlmEnabled = remember { preferencesManager.getActiveProfileId() != null }

    LaunchedEffect(Unit) {
        viewModel.reset()
    }

    LaunchedEffect(initialShoppingList) {
        if (initialShoppingList != null) {
            viewModel.updateListText(initialShoppingList.title)
            viewModel.setSelectedList(initialShoppingList)
            if (initialShoppingList.storeName != null) {
                viewModel.updateStoreText(initialShoppingList.storeName)
            }
        }
    }

    LaunchedEffect(scannedReceipt) {
        if (scannedReceipt != null) {
            viewModel.setScannedReceipt(scannedReceipt)
            viewModel.processScannedReceipt(scannedReceipt, stores)
        }
    }

    LaunchedEffect(isLlmEnabled) {
        if (isLlmEnabled) {
            viewModel.setPurchaseMode(PurchaseMode.SCAN)
        } else {
            viewModel.setPurchaseMode(PurchaseMode.MANUAL)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.purchase)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SmartSelectField(
                    value = uiState.listText,
                    onValueChange = { viewModel.updateListText(it) },
                    label = stringResource(R.string.list_name),
                    items = unfinishedLists,
                    itemToText = { it.title },
                    onItemSelected = { list ->
                        viewModel.updateListText(list.title)
                        viewModel.setSelectedList(list)
                        if (uiState.storeText.isBlank()) {
                            viewModel.updateStoreText(list.storeName ?: "")
                        }
                    },
                    isError = uiState.listError,
                    supportingText = if (uiState.listError) { { Text(stringResource(R.string.name_required)) } } else null
                )

                Spacer(Modifier.height(16.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.purchaseMode == PurchaseMode.MANUAL,
                        onClick = { viewModel.setPurchaseMode(PurchaseMode.MANUAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.mode_manual))
                    }
                    SegmentedButton(
                        selected = uiState.purchaseMode == PurchaseMode.SCAN,
                        onClick = { viewModel.setPurchaseMode(PurchaseMode.SCAN) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = isLlmEnabled
                    ) {
                        Text(stringResource(R.string.mode_scan))
                    }
                }

                Spacer(Modifier.height(16.dp))

                when (uiState.purchaseMode) {
                    PurchaseMode.MANUAL -> {
                        StoreSelectionField(
                            value = uiState.storeText,
                            onValueChange = { viewModel.updateStoreText(it) },
                            stores = stores,
                            onItemSelected = { viewModel.updateStoreText(it.name) },
                            isError = uiState.storeError
                        )

                        Spacer(Modifier.height(12.dp))

                        PriceInputComponent(
                            value = uiState.priceText,
                            onValueChange = { viewModel.updatePriceText(it) },
                            isError = uiState.priceError
                        )
                    }
                    PurchaseMode.SCAN -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onScanRequest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                contentPadding = PaddingValues(8.dp)
                            ) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.scan_receipt))
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = onPdfRequest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                contentPadding = PaddingValues(8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.from_pdf))
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = onGalleryRequest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                contentPadding = PaddingValues(8.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.from_gallery))
                            }

                            if (uiState.scannedReceipt != null) {
                                Spacer(Modifier.height(16.dp))
                                
                                StoreSelectionField(
                                    value = uiState.storeText,
                                    onValueChange = { viewModel.updateStoreText(it) },
                                    stores = stores,
                                    onItemSelected = { viewModel.updateStoreText(it.name) },
                                    isError = uiState.storeError
                                )

                                Spacer(Modifier.height(12.dp))

                                PriceInputComponent(
                                    value = uiState.priceText,
                                    onValueChange = { viewModel.updatePriceText(it) },
                                    isError = uiState.priceError
                                )

                                Spacer(Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleItemsExpanded() }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Added ${uiState.scannedReceipt!!.items.size} products",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (uiState.itemsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }

                                if (uiState.itemsExpanded) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        uiState.scannedReceipt!!.items.forEach { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${item.name} (${if (item.quantity % 1.0 == 0.0) item.quantity.toInt() else item.quantity} x)",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = String.format("%.2f", item.price),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.validateAndConfirm(unfinishedLists, stores, onConfirm) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    // Pending confirmation dialogs (list/store)
    uiState.pendingListConfirm?.let { listName ->
        AlertDialog(
            onDismissRequest = { viewModel.setPendingListConfirm(null) },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.new_list_title)) },
            text = { Text(stringResource(R.string.new_list_confirmation, listName)) },
            confirmButton = {
                TextButton(onClick = {
                    val data = uiState.pendingConfirmData
                    viewModel.setPendingListConfirm(null)
                    if (data != null) {
                        val (l, s) = data
                        val storeExists = stores.any { it.name.equals(s, ignoreCase = true) }
                        if (!storeExists) {
                            viewModel.setPendingStoreConfirm(s)
                        } else {
                            onConfirm(null, l, s, uiState.priceText.trim().replace(',', '.').toDouble(), uiState.scannedReceipt?.items ?: emptyList())
                        }
                    }
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setPendingListConfirm(null) }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    uiState.pendingStoreConfirm?.let { storeName ->
        AlertDialog(
            onDismissRequest = { viewModel.setPendingStoreConfirm(null) },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.new_store_title)) },
            text = { Text(stringResource(R.string.new_store_confirmation, storeName)) },
            confirmButton = {
                TextButton(onClick = {
                    val data = uiState.pendingConfirmData
                    viewModel.setPendingStoreConfirm(null)
                    if (data != null) {
                        onConfirm(uiState.selectedListId, data.first, storeName, uiState.priceText.trim().replace(',', '.').toDouble(), uiState.scannedReceipt?.items ?: emptyList())
                    }
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setPendingStoreConfirm(null) }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
fun StoreSelectionField(
    value: String,
    onValueChange: (String) -> Unit,
    stores: List<StoreEntity>,
    onItemSelected: (StoreEntity) -> Unit,
    isError: Boolean
) {
    SmartSelectField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(R.string.store_name),
        items = stores,
        itemToText = { it.name },
        onItemSelected = onItemSelected,
        isError = isError,
        supportingText = if (isError) { { Text(stringResource(R.string.name_required)) } } else null
    )
}

@Composable
fun PriceInputComponent(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.total_price)) },
        isError = isError,
        supportingText = if (isError) {
            { Text(stringResource(R.string.price_must_be_number)) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
