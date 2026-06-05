package com.otakeeesen.byebyemoneylist.ui.components.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.ui.components.product.ProductSearchDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EditScannedItemDialog(
    item: ScannedItem,
    onDismiss: () -> Unit,
    onConfirm: (ScannedItem) -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var priceText by remember { mutableStateOf(item.price.toString()) }
    var barcode by remember { mutableStateOf(item.barcode ?: "") }
    var showSearchDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val productRepository = remember { (context.applicationContext as ByeByeMoneyApplication).productRepository }

    if (showSearchDialog) {
        ProductSearchDialog(
            onDismiss = { showSearchDialog = false },
            onProductSelected = { product ->
                name = product.name
                barcode = product.barcode
                showSearchDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_item)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.product_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.total_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text(stringResource(R.string.barcode)) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_product))
                    }
                    IconButton(onClick = {
                        GmsBarcodeScanning.getClient(context).startScan()
                            .addOnSuccessListener { result: Barcode ->
                                val scannedBarcode = result.rawValue ?: ""
                                barcode = scannedBarcode
                                // Attempt to find product in DB
                                val product = productRepository.getProductByBarcode(scannedBarcode)
                                if (product != null) {
                                    name = product.name
                                }
                            }
                    }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan_barcode))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    item.copy(
                        name = name,
                        price = priceText.replace(',', '.').toDoubleOrNull() ?: item.price,
                        barcode = barcode.ifBlank { null }
                    )
                )
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
