package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
                    Text(
                        text = stringResource(R.string.label_detected_items),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(items) { item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                trailingContent = {
                                    Text(String.format("%.2f", item.price))
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalTotal = totalSumText.replace(',', '.').toDoubleOrNull()
                onConfirm(
                    ScannedReceipt(
                        storeName = storeName.ifBlank { null },
                        items = items,
                        totalSum = finalTotal
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
