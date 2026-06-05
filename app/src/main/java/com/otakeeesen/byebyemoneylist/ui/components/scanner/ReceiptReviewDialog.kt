package com.otakeeesen.byebyemoneylist.ui.components.scanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R

@Composable
fun ReceiptReviewDialog(
    initialReceipt: ScannedReceipt,
    onConfirm: (ScannedReceipt) -> Unit,
    onDismiss: () -> Unit
) {
    var storeName by remember { mutableStateOf(initialReceipt.storeName ?: "") }
    var totalSumText by remember { mutableStateOf(initialReceipt.totalSum?.let { String.format("%.2f", it) } ?: "") }
    val items = remember { mutableStateListOf(*initialReceipt.items.toTypedArray()) }
    val selectedIndices = remember { mutableStateListOf(*initialReceipt.items.indices.toList().toTypedArray()) }
    var itemToEdit by remember { mutableStateOf<ScannedItem?>(null) }
    var itemToEditIndex by remember { mutableStateOf(-1) }

    if (itemToEdit != null && itemToEditIndex != -1) {
        EditScannedItemDialog(
            item = itemToEdit!!,
            onDismiss = { itemToEdit = null },
            onConfirm = { updatedItem ->
                items[itemToEditIndex] = updatedItem
                itemToEdit = null
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_receipt_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text(stringResource(R.string.store_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = totalSumText,
                    onValueChange = { totalSumText = it },
                    label = { Text(stringResource(R.string.total_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_detected_items),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = {
                            if (selectedIndices.size == items.size) {
                                selectedIndices.clear()
                            } else {
                                selectedIndices.clear()
                                selectedIndices.addAll(items.indices)
                            }
                        }) {
                            Text(if (selectedIndices.size == items.size) "Deselect All" else "Select All")
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        itemsIndexed(items) { index, item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                supportingContent = {
                                    Text("€%.2f".format(item.price))
                                },
                                leadingContent = {
                                    Checkbox(
                                        checked = selectedIndices.contains(index),
                                        onCheckedChange = { checked ->
                                            if (checked) selectedIndices.add(index)
                                            else selectedIndices.remove(index)
                                        }
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { 
                                        items.removeAt(index)
                                        selectedIndices.remove(index)
                                        // Adjust indices in selectedIndices
                                        val newIndices = selectedIndices.map { if (it > index) it - 1 else it }.filter { it >= 0 }
                                        selectedIndices.clear()
                                        selectedIndices.addAll(newIndices)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_item))
                                    }
                                },
                                modifier = Modifier.clickable {
                                    itemToEdit = item
                                    itemToEditIndex = index
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIndices.isNotEmpty() || totalSumText.isNotBlank(),
                onClick = {
                    val finalTotal = totalSumText.replace(',', '.').toDoubleOrNull()
                    val selectedItems = selectedIndices.sorted().map { items[it] }
                    onConfirm(
                        ScannedReceipt(
                            storeName = storeName.ifBlank { null },
                            items = selectedItems,
                            totalSum = finalTotal
                        )
                    )
                }
            ) {
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
