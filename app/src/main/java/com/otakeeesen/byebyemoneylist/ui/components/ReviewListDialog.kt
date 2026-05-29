package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewListDialog(
    shoppingList: ShoppingList,
    onDismiss: () -> Unit,
    onUpdateItem: (PurchaseItem, String, Double?, String) -> Unit,
    onMapToExisting: (PurchaseItem, ProductEntity) -> Unit,
    onDeleteItem: (PurchaseItem) -> Unit,
) {
    val itemsToReview = remember(shoppingList.items) { 
        shoppingList.items.filter { it.productStatus == "added" } 
    }
    
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentItem = itemsToReview.getOrNull(currentIndex)

    if (itemsToReview.isEmpty() || currentItem == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var name by remember(currentItem) { mutableStateOf(currentItem.name) }
    var priceText by remember(currentItem) { mutableStateOf(currentItem.price?.let { String.format("%.2f", it) } ?: "") }
    var barcode by remember(currentItem) { mutableStateOf("") }
    
    var showSearchDialog by remember { mutableStateOf(false) }
    var pendingMapProduct by remember { mutableStateOf<ProductEntity?>(null) }
    
    val context = LocalContext.current

    if (showSearchDialog) {
        ProductSearchDialog(
            onDismiss = { showSearchDialog = false },
            onProductSelected = { product ->
                pendingMapProduct = product
                showSearchDialog = false
            }
        )
    }

    pendingMapProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { pendingMapProduct = null },
            title = { Text(stringResource(R.string.search_existing)) },
            text = { Text(stringResource(R.string.map_to_product_confirmation, product.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onMapToExisting(currentItem, product)
                    pendingMapProduct = null
                    if (currentIndex < itemsToReview.size - 1) {
                        currentIndex++
                    } else if (itemsToReview.size == 1) {
                        onDismiss()
                    }
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMapProduct = null }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.review_item_title))
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${currentIndex + 1} / ${itemsToReview.size}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.alias) + ": ${currentItem.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.product_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.total_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text(stringResource(R.string.barcode)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            GmsBarcodeScanning.getClient(context).startScan()
                                .addOnSuccessListener { result: Barcode ->
                                    barcode = result.rawValue ?: ""
                                }
                        }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan_barcode))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { showSearchDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.search_existing))
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { 
                        onDeleteItem(currentItem)
                        if (currentIndex < itemsToReview.size - 1) {
                            // index will shift automatically because list is updated? 
                            // actually itemsToReview is remember(shoppingList.items), so it will update.
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_item))
                }
            }
        },
        confirmButton = {
            Row {
                if (currentIndex > 0) {
                    TextButton(onClick = { currentIndex-- }) {
                        Text(stringResource(R.string.previous))
                    }
                }
                
                val isLast = currentIndex == itemsToReview.size - 1
                TextButton(onClick = {
                    onUpdateItem(currentItem, name, priceText.replace(',', '.').toDoubleOrNull(), barcode)
                    if (isLast) {
                        onDismiss()
                    } else {
                        currentIndex++
                    }
                }) {
                    Text(stringResource(if (isLast) R.string.finish else R.string.next))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
