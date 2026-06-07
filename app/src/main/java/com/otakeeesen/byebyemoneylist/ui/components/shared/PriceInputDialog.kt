package com.otakeeesen.byebyemoneylist.ui.components.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R

@Composable
fun PriceInputDialog(
    initialPrice: Double?,
    initialQuantity: Double = 1.0,
    isSubscription: Boolean = false,
    onConfirm: (Double?, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var priceText by remember { mutableStateOf(initialPrice?.toString() ?: "") }
    var priceError by remember { mutableStateOf(false) }

    var quantityText by remember {
        mutableStateOf(if (initialQuantity % 1.0 == 0.0) initialQuantity.toInt().toString() else initialQuantity.toString())
    }
    var quantityError by remember { mutableStateOf(false) }

    fun validateAndConfirm() {
        val trimmedPrice = priceText.trim().replace(',', '.')
        
        val price = if (trimmedPrice.isEmpty()) null else trimmedPrice.toDoubleOrNull()
        if (trimmedPrice.isNotEmpty() && (price == null || price < 0)) {
            priceError = true
        } else {
            priceError = false
        }

        val quantity = if (isSubscription) 1.0 else {
            val trimmedQuantity = quantityText.trim().replace(',', '.')
            val q = trimmedQuantity.toDoubleOrNull()
            if (q == null || q <= 0) {
                quantityError = true
                null
            } else {
                quantityError = false
                q
            }
        }

        if (!priceError && !quantityError && (isSubscription || quantity != null)) {
            onConfirm(price, quantity!!)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_price)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = if (isSubscription) ImeAction.Done else ImeAction.Next
                    ),
                    keyboardActions = if (isSubscription) {
                        KeyboardActions(onDone = { validateAndConfirm() })
                    } else KeyboardActions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.keep_default_price),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                
                if (!isSubscription) {
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = {
                            quantityText = it
                            quantityError = false
                        },
                        label = { Text(stringResource(R.string.quantity)) },
                        isError = quantityError,
                        supportingText = if (quantityError) {
                            { Text(stringResource(R.string.quantity_must_be_number)) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { validateAndConfirm() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
}
