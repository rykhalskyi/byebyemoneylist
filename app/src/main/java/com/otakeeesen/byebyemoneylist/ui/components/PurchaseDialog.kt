package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity

data class MappableItem(
    val scannedItem: ScannedItem,
    var matchedProduct: ProductEntity? = null,
    var displayName: String,
    var isNew: Boolean = false
)

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
    onDismiss: () -> Unit,
    onConfirm: (listId: Long?, listName: String?, storeName: String, price: Double, items: List<ScannedItem>) -> Unit,
    onScanRequest: () -> Unit = {},
    scannedReceipt: ScannedReceipt? = null,
) {
    var listText by remember { mutableStateOf("") }
    var selectedListId by remember { mutableStateOf<Long?>(null) }
    var storeText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var priceError by remember { mutableStateOf(false) }
    
    var purchaseMode by remember { mutableStateOf(PurchaseMode.MANUAL) }

    var pendingListConfirm by remember { mutableStateOf<String?>(null) }
    var pendingStoreConfirm by remember { mutableStateOf<String?>(null) }
    var pendingConfirmData by remember { mutableStateOf<Pair<String, String>?>(null) }

    var mappableItems by remember { mutableStateOf<List<MappableItem>>(emptyList()) }
    var isMappingMode by remember { mutableStateOf(false) }
    val editedItemIndices = remember { mutableStateOf(setOf<Int>()) }

    // Update fields when a receipt is scanned or selected list changes
    LaunchedEffect(scannedReceipt, selectedListId) {
        scannedReceipt?.let { receipt ->
            receipt.totalSum?.let { priceText = String.format("%.2f", it) }
            
            // 1. Store Matching (only if not already set or if it's the first time)
            if (storeText.isBlank()) {
                val matchedStore = stores.find { 
                    it.name.equals(receipt.storeName, ignoreCase = true) && 
                    (receipt.storeAddress == null || it.address.equals(receipt.storeAddress, ignoreCase = true))
                }
                
                if (matchedStore != null) {
                    storeText = matchedStore.name
                } else {
                    val suggestedName = buildString {
                        append(receipt.storeName ?: "Unknown Store")
                        receipt.storeAddress?.let { 
                            val briefAddress = it.take(20).trim() + if (it.length > 20) "..." else ""
                            append(" ($briefAddress)") 
                        }
                    }
                    storeText = suggestedName
                }
            }

            // 2. Product Mapping
            val currentList = shoppingLists.find { it.id == selectedListId }
            
            mappableItems = receipt.items.mapIndexed { index, item ->
                // If user already edited this item, keep their edit
                if (editedItemIndices.value.contains(index) && index < mappableItems.size) {
                    return@mapIndexed mappableItems[index]
                }

                // 2a. Priority 1: Match products in the selected list
                val listItemMatch = currentList?.items?.find { it.name.equals(item.name, ignoreCase = true) }
                
                val matchedProduct = if (listItemMatch != null) {
                    products.find { it.id == listItemMatch.productId }
                } else {
                    // 2b. Priority 2: Use Aliases
                    val aliasMatch = aliases.find { 
                        it.aliasName.equals(item.name, ignoreCase = true) &&
                        (selectedListId == null || it.storeId == currentList?.storeId || it.storeId == null)
                    }
                    aliasMatch?.let { am -> products.find { it.id == am.productId } }
                        ?: products.find { it.name.equals(item.name, ignoreCase = true) }
                }

                MappableItem(
                    scannedItem = item,
                    matchedProduct = matchedProduct,
                    displayName = matchedProduct?.name ?: item.name,
                    isNew = matchedProduct == null
                )
            }
            
            if (receipt.items.isNotEmpty()) {
                isMappingMode = true
            }
        }
    }

    fun validateAndConfirm() {
        val trimmedPrice = priceText.trim().replace(',', '.')
        if (trimmedPrice.isBlank() || trimmedPrice.toDoubleOrNull() == null) {
            priceError = true
            return
        }
        priceError = false

        val trimmedList = listText.trim()
        val trimmedStore = storeText.trim()

        val listExists = trimmedList.isNotEmpty()
            && shoppingLists.any { it.title.equals(trimmedList, ignoreCase = true) }
        val storeExists = trimmedStore.isNotEmpty()
            && stores.any { it.name.equals(trimmedStore, ignoreCase = true) }

        if (trimmedList.isNotEmpty() && !listExists && selectedListId == null) {
            pendingListConfirm = trimmedList
            pendingConfirmData = Pair(trimmedList, trimmedStore)
            return
        }

        if (trimmedStore.isNotEmpty() && !storeExists) {
            pendingStoreConfirm = trimmedStore
            pendingConfirmData = Pair(trimmedList, trimmedStore)
            return
        }

        val itemsToSave = if (isMappingMode) {
            mappableItems.map { it.scannedItem.copy(name = it.displayName, productId = it.matchedProduct?.id) }
        } else {
            scannedReceipt?.items ?: emptyList()
        }

        onConfirm(selectedListId, trimmedList.ifBlank { null }, trimmedStore, trimmedPrice.toDouble(), itemsToSave)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isMappingMode) stringResource(R.string.receipt_review) else stringResource(R.string.purchase))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Shopping List Selection always at the top
                SmartSelectField(
                    value = listText,
                    onValueChange = {
                        listText = it
                        selectedListId = null
                    },
                    label = stringResource(R.string.list_name),
                    items = shoppingLists,
                    itemToText = { it.title },
                    onItemSelected = { list ->
                        listText = list.title
                        selectedListId = list.id
                        // Pre-fill store if available
                        if (storeText.isBlank()) {
                            storeText = list.storeName ?: ""
                        }
                    }
                )

                if (!isMappingMode) {
                    Spacer(Modifier.height(16.dp))
                    
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = purchaseMode == PurchaseMode.MANUAL,
                            onClick = { purchaseMode = PurchaseMode.MANUAL },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text(stringResource(R.string.mode_manual))
                        }
                        SegmentedButton(
                            selected = purchaseMode == PurchaseMode.SCAN,
                            onClick = { purchaseMode = PurchaseMode.SCAN },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text(stringResource(R.string.mode_scan))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    when (purchaseMode) {
                        PurchaseMode.MANUAL -> {
                            SmartSelectField(
                                value = storeText,
                                onValueChange = { storeText = it },
                                label = stringResource(R.string.store_name),
                                items = stores,
                                itemToText = { it.name },
                                onItemSelected = { storeText = it.name }
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = priceText,
                                onValueChange = {
                                    priceText = it
                                    priceError = false
                                },
                                label = { Text(stringResource(R.string.total_price)) },
                                isError = priceError,
                                supportingText = if (priceError) {
                                    { Text(stringResource(R.string.price_must_be_number)) }
                                } else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        PurchaseMode.SCAN -> {
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
                        }
                    }
                } else {
                    // Mapping Mode Content
                    Spacer(Modifier.height(12.dp))

                    SmartSelectField(
                        value = storeText,
                        onValueChange = { storeText = it },
                        label = stringResource(R.string.store_name),
                        items = stores,
                        itemToText = { it.name },
                        onItemSelected = { storeText = it.name }
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = {
                            priceText = it
                            priceError = false
                        },
                        label = { Text(stringResource(R.string.total_price)) },
                        isError = priceError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.label_detected_items),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    
                    Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        mappableItems.forEachIndexed { index, item ->
                            MappingItemRow(
                                item = item,
                                onNameChange = {
                                    editedItemIndices.value = editedItemIndices.value + index
                                    mappableItems = mappableItems.toMutableList().apply {
                                        this[index] = this[index].copy(displayName = it)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { validateAndConfirm() }) {
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
    pendingListConfirm?.let { listName ->
        AlertDialog(
            onDismissRequest = { pendingListConfirm = null },
            title = { Text(stringResource(R.string.new_list_title)) },
            text = { Text(stringResource(R.string.new_list_confirmation, listName)) },
            confirmButton = {
                TextButton(onClick = {
                    val data = pendingConfirmData
                    pendingListConfirm = null
                    pendingConfirmData = null
                    if (data != null) {
                        val (l, s) = data
                        val storeExists = s.isNotEmpty()
                            && stores.any { it.name.equals(s, ignoreCase = true) }
                        if (s.isNotEmpty() && !storeExists) {
                            pendingStoreConfirm = s
                            pendingConfirmData = Pair(l, s)
                        } else {
                            onConfirm(null, l, s, priceText.trim().replace(',', '.').toDouble(), scannedReceipt?.items ?: emptyList())
                        }
                    }
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingListConfirm = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    pendingStoreConfirm?.let { storeName ->
        AlertDialog(
            onDismissRequest = { pendingStoreConfirm = null },
            title = { Text(stringResource(R.string.new_store_title)) },
            text = { Text(stringResource(R.string.new_store_confirmation, storeName)) },
            confirmButton = {
                TextButton(onClick = {
                    val data = pendingConfirmData
                    pendingStoreConfirm = null
                    pendingConfirmData = null
                    if (data != null) {
                        onConfirm(selectedListId, data.first.ifBlank { null }, storeName, priceText.trim().replace(',', '.').toDouble(), scannedReceipt?.items ?: emptyList())
                    }
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStoreConfirm = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun MappingItemRow(
    item: MappableItem,
    onNameChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (item.isNew) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = item.displayName,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                label = { Text(item.scannedItem.name, style = MaterialTheme.typography.labelSmall) }
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = String.format("%.2f", item.scannedItem.price),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
