package com.otakeeesen.byebyemoneylist.ui.components.product

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.PurchaseItem

@Composable
fun EditPurchaseItemDialog(
    item: PurchaseItem,
    onDismiss: () -> Unit,
    onConfirm: (newPrice: Double?, newQuantity: Double) -> Unit,
    onEditProduct: (Long) -> Unit,
) {
    var priceText by remember { mutableStateOf(item.price?.toString() ?: "") }
    var quantityText by remember { mutableStateOf(if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()) }
    var priceError by remember { mutableStateOf(false) }
    var quantityError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_item)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = stringResource(R.string.product_preview),
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        OutlinedButton(
                            onClick = { onEditProduct(item.productId) },
                            modifier = Modifier.padding(top = 4.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.edit_product),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Price field - always ready
                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it
                        priceError = false
                    },
                    label = { Text(stringResource(R.string.price_hint)) },
                    isError = priceError,
                    supportingText = if (priceError) {
                        { Text(stringResource(R.string.price_must_be_number)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quantity field
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = it
                        quantityError = false
                    },
                    label = { Text(stringResource(R.string.quantity)) },
                    isError = quantityError,
                    supportingText = if (quantityError) {
                        { Text(stringResource(R.string.price_must_be_number)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = if (priceText.isBlank()) null else priceText.toDoubleOrNull()
                if (priceText.isNotBlank() && (price == null || price < 0)) {
                    priceError = true
                    return@TextButton
                }

                val quantity = quantityText.replace(',', '.').toDoubleOrNull() ?: 1.0
                if (quantity <= 0) {
                    quantityError = true
                    return@TextButton
                }

                onConfirm(price, quantity)
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
