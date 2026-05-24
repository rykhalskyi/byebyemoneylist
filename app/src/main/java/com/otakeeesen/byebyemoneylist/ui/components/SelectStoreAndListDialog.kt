package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity

@Composable
fun SelectStoreAndListDialog(
    shoppingLists: List<ShoppingList>,
    stores: List<StoreEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onCreateStore: (String, (Long) -> Unit) -> Unit,
    onCreateShoppingList: (String, Long, (Long) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var storeText by remember { mutableStateOf("") }
    
    var pendingStoreConfirm by remember { mutableStateOf<String?>(null) }
    var pendingListConfirm by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var nameError by remember { mutableStateOf(false) }

    fun validateAndConfirm() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            nameError = true
            return
        }
        
        val trimmedStore = storeText.trim()
        val store = stores.find { it.name.equals(trimmedStore, ignoreCase = true) }
        
        // Handle New Store
        if (trimmedStore.isNotEmpty() && store == null) {
            pendingStoreConfirm = trimmedStore
            return
        }

        // Handle New List
        val list = shoppingLists.find { it.title.equals(trimmedName, ignoreCase = true) && it.storeName.equals(trimmedStore, ignoreCase = true) }
        if (list == null && store != null) {
            pendingListConfirm = trimmedName to store.id
            return
        }

        if (list != null) {
            onConfirm(list.id)
        }
    }

    val filteredLists = shoppingLists.filter { list ->
        !list.isFinished && (storeText.isEmpty() || list.storeName == storeText)
    }

    // Confirmation dialogs
    pendingStoreConfirm?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingStoreConfirm = null },
            title = { Text(stringResource(R.string.create_store)) },
            text = { Text(stringResource(R.string.create_store_confirmation, name)) },
            confirmButton = {
                TextButton(onClick = {
                    onCreateStore(name) { newStoreId -> 
                        pendingStoreConfirm = null
                        storeText = name
                    }
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = { pendingStoreConfirm = null }) { Text(stringResource(R.string.no)) } }
        )
    }

    pendingListConfirm?.let { (name, storeId) ->
        AlertDialog(
            onDismissRequest = { pendingListConfirm = null },
            title = { Text(stringResource(R.string.create_list)) },
            text = { Text(stringResource(R.string.create_list_in_store_confirmation, name)) },
            confirmButton = {
                TextButton(onClick = {
                    onCreateShoppingList(name, storeId) { newListId ->
                        onConfirm(newListId)
                        pendingListConfirm = null
                    }
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = { pendingListConfirm = null }) { Text(stringResource(R.string.no)) } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.in_store)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                StoreDropdown(
                    value = storeText,
                    onValueChange = { storeText = it },
                    stores = stores
                )

                Spacer(Modifier.height(12.dp))

                ShoppingListCombobox(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    lists = filteredLists,
                    onListSelected = { name = it.title }
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
private fun ShoppingListCombobox(
    value: String,
    onValueChange: (String) -> Unit,
    lists: List<ShoppingList>,
    onListSelected: (ShoppingList) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
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
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.title) },
                    onClick = { onListSelected(list); expanded = false },
                )
            }
        }
    }
}


// ... (rest of imports)

// (Removed local StoreDropdown definition)

