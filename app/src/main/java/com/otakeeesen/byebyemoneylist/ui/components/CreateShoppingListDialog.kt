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
    onConfirm: (name: String, categoryName: String, storeName: String) -> Unit,
    initialName: String = "",
    initialCategory: String = "",
    initialStore: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    var categoryText by remember { mutableStateOf(initialCategory) }
    var storeText by remember { mutableStateOf(initialStore) }
    var nameError by remember { mutableStateOf(false) }

    val isEditing = initialName.isNotEmpty()
    var pendingCategoryConfirm by remember { mutableStateOf<String?>(null) }
    var pendingStoreConfirm by remember { mutableStateOf<String?>(null) }
    var pendingConfirmData by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    fun validateAndConfirm() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            nameError = true
            return
        }
        nameError = false

        val trimmedCategory = categoryText.trim()
        val trimmedStore = storeText.trim()

        val categoryExists = categories.any { it.name.equals(trimmedCategory, ignoreCase = true) }
        val storeExists = stores.any { it.name.equals(trimmedStore, ignoreCase = true) }

        if (trimmedCategory.isNotEmpty() && !categoryExists) {
            pendingCategoryConfirm = trimmedCategory
            pendingConfirmData = Triple(trimmedName, trimmedCategory, trimmedStore)
            return
        }

        if (trimmedStore.isNotEmpty() && !storeExists) {
            pendingStoreConfirm = trimmedStore
            pendingConfirmData = Triple(trimmedName, trimmedCategory, trimmedStore)
            return
        }

        onConfirm(trimmedName, trimmedCategory, trimmedStore)
    }

    // Category confirmation dialog
    pendingCategoryConfirm?.let { categoryName ->
        AlertDialog(
            onDismissRequest = { pendingCategoryConfirm = null },
            title = { Text(stringResource(R.string.new_category_title)) },
            text = { Text(stringResource(R.string.new_category_confirmation, categoryName)) },
            confirmButton = {
                TextButton(onClick = {
                    val data = pendingConfirmData
                    pendingCategoryConfirm = null
                    pendingConfirmData = null
                    if (data != null) {
                        val (n, c, s) = data
                        val storeExists = stores.any { it.name.equals(s, ignoreCase = true) }
                        if (s.isNotEmpty() && !storeExists) {
                            pendingStoreConfirm = s
                            pendingConfirmData = Triple(n, c, s)
                        } else {
                            onConfirm(n, c, s)
                        }
                    }
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryConfirm = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
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
                        onConfirm(data.first, data.second, data.third)
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

                CategoryDropdown(
                    value = categoryText,
                    onValueChange = { categoryText = it },
                    categories = categories,
                )

                Spacer(Modifier.height(12.dp))

                StoreDropdown(
                    value = storeText,
                    onValueChange = { storeText = it },
                    stores = stores,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    categories: List<CategoryEntity>,
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
            label = { Text(stringResource(R.string.category)) },
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
            categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Display category color
                            Canvas(
                                modifier = Modifier
                                    .size(16.dp)
                            ) {
                                drawCircle(color = Color(android.graphics.Color.parseColor(category.color)))
                            }
                            Text(category.name)
                        }
                    },
                    onClick = {
                        onValueChange(category.name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    stores: List<StoreEntity>,
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
            label = { Text(stringResource(R.string.store)) },
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
            stores.forEach { store ->
                DropdownMenuItem(
                    text = { Text(store.name) },
                    onClick = {
                        onValueChange(store.name)
                        expanded = false
                    },
                )
            }
        }
    }
}
