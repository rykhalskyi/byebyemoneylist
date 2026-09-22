package com.otakeeesen.byebyemoneylist.ui.components.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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

/**
 * Captures the name and quantity of a free-text placeholder list item. Unlike
 * [PriceInputDialog] there is no price: placeholders are not catalog products and
 * contribute nothing to totals until they are matched to a real purchase.
 */
@Composable
fun PlaceholderInputDialog(
    initialName: String,
    onConfirm: (name: String, quantity: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var nameText by remember { mutableStateOf(initialName) }
    var quantityText by remember { mutableStateOf("1") }
    var nameError by remember { mutableStateOf(false) }
    var quantityError by remember { mutableStateOf(false) }

    fun validateAndConfirm() {
        val name = nameText.trim()
        if (name.isEmpty()) {
            nameError = true
            return
        }
        nameError = false

        val quantity = quantityText.trim().replace(',', '.').toDoubleOrNull()
        if (quantity == null || quantity <= 0) {
            quantityError = true
            return
        }
        quantityError = false
        onConfirm(name, quantity)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.placeholder_item)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.product_name)) },
                    isError = nameError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

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
                    keyboardActions = KeyboardActions(onDone = { validateAndConfirm() }),
                    modifier = Modifier.fillMaxWidth(),
                )
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
