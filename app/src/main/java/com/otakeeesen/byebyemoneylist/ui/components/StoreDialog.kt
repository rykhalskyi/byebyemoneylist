package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity

@Composable
fun StoreDialog(
    editingStore: StoreEntity?,
    editingStoreCategories: List<CategoryEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, logoPath: String, categoryIds: List<Long>) -> Unit,
) {
    var name by remember { mutableStateOf(editingStore?.name ?: "") }
    var logoPath by remember { mutableStateOf(editingStore?.logoPath ?: "") }
    var selectedCategories by remember { mutableStateOf(editingStoreCategories) }
    var nameError by remember { mutableStateOf(false) }

    val isEditing = editingStore != null
    val title = if (isEditing) stringResource(R.string.edit_store) else stringResource(R.string.add_store)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.store_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.name_required)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = logoPath,
                    onValueChange = { logoPath = it },
                    label = { Text(stringResource(R.string.picture_url)) },
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    nameError = true
                } else {
                    onSave(trimmed, logoPath.trim(), selectedCategories.map { it.id })
                }
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
