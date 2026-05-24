package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity

@Composable
fun DirectPurchaseDialog(
    shoppingLists: List<ShoppingList>,
    stores: List<StoreEntity>,
    onDismiss: () -> Unit,
    onConfirm: (listId: Long?, listName: String?, storeName: String, price: Double) -> Unit,
) {
    var listDropdownExpanded by remember { mutableStateOf(false) }
    var listText by remember { mutableStateOf("") }
    var selectedListId by remember { mutableStateOf<Long?>(null) }
    var storeText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var priceError by remember { mutableStateOf(false) }

    var pendingListConfirm by remember { mutableStateOf<String?>(null) }
    var pendingStoreConfirm by remember { mutableStateOf<String?>(null) }
    var pendingConfirmData by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun validateAndConfirm() {
        val trimmedPrice = priceText.trim()
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

        onConfirm(selectedListId, trimmedList.ifBlank { null }, trimmedStore, trimmedPrice.toDouble())
    }

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
                            onConfirm(null, l, s, priceText.trim().toDouble())
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
                        onConfirm(selectedListId, data.first.ifBlank { null }, storeName, priceText.trim().toDouble())
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.direct_purchase)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.select_or_create_list),
                    style = MaterialTheme.typography.labelSmall,
                )

                ListDropdown(
                    value = listText,
                    onValueChange = {
                        listText = it
                        selectedListId = null
                    },
                    shoppingLists = shoppingLists,
                    onListSelected = { list ->
                        listText = list.title
                        selectedListId = list.id
                    },
                )

                Spacer(Modifier.height(8.dp))

                StoreDropdown(
                    value = storeText,
                    onValueChange = { storeText = it },
                    stores = stores,
                )

                Spacer(Modifier.height(8.dp))

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    shoppingLists: List<ShoppingList>,
    onListSelected: (ShoppingList) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.list_name)) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            shoppingLists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.title) },
                    onClick = {
                        onListSelected(list)
                        expanded = false
                    },
                )
            }
        }
    }
}
