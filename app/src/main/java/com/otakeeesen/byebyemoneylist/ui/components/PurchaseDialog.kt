package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    
    var listError by remember { mutableStateOf(false) }
    var storeError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }
    
    var purchaseMode by remember { mutableStateOf(PurchaseMode.MANUAL) }

    var pendingListConfirm by remember { mutableStateOf<String?>(null) }
    var pendingStoreConfirm by remember { mutableStateOf<String?>(null) }
    var pendingConfirmData by remember { mutableStateOf<Pair<String, String>?>(null) }

    var itemsExpanded by remember { mutableStateOf(false) }

    // Update fields when a receipt is scanned
    LaunchedEffect(scannedReceipt) {
        scannedReceipt?.let { receipt ->
            receipt.totalSum?.let { 
                priceText = String.format("%.2f", it)
                priceError = false
            }
            
            // 1. Store Matching
            if (storeText.isBlank()) {
                val matchedStore = stores.find { 
                    it.name.equals(receipt.storeName, ignoreCase = true) && 
                    (receipt.storeAddress == null || it.address.equals(receipt.storeAddress, ignoreCase = true))
                }
                
                if (matchedStore != null) {
                    storeText = matchedStore.name
                } else {
                    storeText = receipt.storeName ?: ""
                }
                
                if (storeText.isNotBlank()) {
                    storeError = false
                }
            }

            // 2. Auto-generate list name if blank
            if (listText.isBlank()) {
                val dateStr = SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date())
                val suggestedStore = storeText.ifBlank { "Store" }
                listText = "$suggestedStore $dateStr"
                selectedListId = null
                listError = false
            }

            if (receipt.items.isNotEmpty()) {
                itemsExpanded = true
            }
        }
    }

    fun validateAndConfirm() {
        val trimmedList = listText.trim()
        val trimmedStore = storeText.trim()
        val trimmedPrice = priceText.trim().replace(',', '.')

        listError = trimmedList.isEmpty()
        storeError = trimmedStore.isEmpty()
        priceError = trimmedPrice.isEmpty() || trimmedPrice.toDoubleOrNull() == null

        if (listError || storeError || priceError) return

        val listExists = shoppingLists.any { it.title.equals(trimmedList, ignoreCase = true) }
        val storeExists = stores.any { it.name.equals(trimmedStore, ignoreCase = true) }

        if (!listExists && selectedListId == null) {
            pendingListConfirm = trimmedList
            pendingConfirmData = Pair(trimmedList, trimmedStore)
            return
        }

        if (!storeExists) {
            pendingStoreConfirm = trimmedStore
            pendingConfirmData = Pair(trimmedList, trimmedStore)
            return
        }

        onConfirm(selectedListId, trimmedList, trimmedStore, trimmedPrice.toDouble(), scannedReceipt?.items ?: emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.purchase)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Mandatory List Name Field always at the top
                SmartSelectField(
                    value = listText,
                    onValueChange = {
                        listText = it
                        selectedListId = null
                        listError = false
                    },
                    label = stringResource(R.string.list_name),
                    items = shoppingLists,
                    itemToText = { it.title },
                    onItemSelected = { list ->
                        listText = list.title
                        selectedListId = list.id
                        listError = false
                        if (storeText.isBlank()) {
                            storeText = list.storeName ?: ""
                            storeError = false
                        }
                    },
                    isError = listError,
                    supportingText = if (listError) { { Text(stringResource(R.string.name_required)) } } else null
                )

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
                            onValueChange = { 
                                storeText = it
                                storeError = false
                            },
                            label = stringResource(R.string.store_name),
                            items = stores,
                            itemToText = { it.name },
                            onItemSelected = { 
                                storeText = it.name
                                storeError = false
                            },
                            isError = storeError,
                            supportingText = if (storeError) { { Text(stringResource(R.string.name_required)) } } else null
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

                            if (scannedReceipt != null) {
                                Spacer(Modifier.height(16.dp))
                                
                                SmartSelectField(
                                    value = storeText,
                                    onValueChange = { 
                                        storeText = it
                                        storeError = false
                                    },
                                    label = stringResource(R.string.store_name),
                                    items = stores,
                                    itemToText = { it.name },
                                    onItemSelected = { 
                                        storeText = it.name
                                        storeError = false
                                    },
                                    isError = storeError,
                                    supportingText = if (storeError) { { Text(stringResource(R.string.name_required)) } } else null
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

                                Spacer(Modifier.height(16.dp))
                                
                                // Expander for scanned items (only in Scan mode)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { itemsExpanded = !itemsExpanded }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Added ${scannedReceipt.items.size} products",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (itemsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }

                                if (itemsExpanded) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        scannedReceipt.items.forEach { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = item.name,
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
                        val storeExists = stores.any { it.name.equals(s, ignoreCase = true) }
                        if (!storeExists) {
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
                        onConfirm(selectedListId, data.first, storeName, priceText.trim().replace(',', '.').toDouble(), scannedReceipt?.items ?: emptyList())
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
