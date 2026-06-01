package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity

@Composable
fun CreateShoppingListDialog(
    categories: List<CategoryEntity>,
    stores: List<StoreEntity>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, categoryIds: List<Long>, storeName: String) -> Unit,
    initialName: String = "",
    initialCategories: List<CategoryEntity> = emptyList(),
    initialStore: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedCategories by remember { mutableStateOf(initialCategories) }
    var storeText by remember { mutableStateOf(initialStore) }
    var nameError by remember { mutableStateOf(false) }

    val isEditing = initialName.isNotEmpty()
    var pendingStoreConfirm by remember { mutableStateOf<String?>(null) }
    var pendingConfirmData by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun validateAndConfirm() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            nameError = true
            return
        }
        nameError = false

        val trimmedStore = storeText.trim()
        val storeExists = stores.any { it.name.equals(trimmedStore, ignoreCase = true) }

        if (trimmedStore.isNotEmpty() && !storeExists) {
            pendingStoreConfirm = trimmedStore
            pendingConfirmData = Pair(trimmedName, trimmedStore)
            return
        }

        onConfirm(trimmedName, selectedCategories.map { it.id }, trimmedStore)
    }

    // Store confirmation dialog
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
                        onConfirm(data.first, selectedCategories.map { it.id }, data.second)
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
        title = { Text(stringResource(if (isEditing) R.string.edit_shopping_list else R.string.create_shopping_list)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.list_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.name_required)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                MultiSelectCategoryField(
                    selectedCategories = selectedCategories,
                    allCategories = categories,
                    onCategorySelected = { category ->
                        if (category !in selectedCategories) {
                            selectedCategories = selectedCategories + category
                        }
                    },
                    onCategoryRemoved = { category ->
                        selectedCategories = selectedCategories - category
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                SmartSelectField(
                    value = storeText,
                    onValueChange = { storeText = it },
                    label = stringResource(R.string.store),
                    items = stores,
                    itemToText = { it.name },
                    onItemSelected = { storeText = it.name }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { validateAndConfirm() }) {
                Text(stringResource(if (isEditing) R.string.save else R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
