package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
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
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity

@Composable
fun CategoryDialog(
    editingCategory: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit,
) {
    var name by remember { mutableStateOf(editingCategory?.name ?: "") }
    var nameError by remember { mutableStateOf(false) }

    val isEditing = editingCategory != null
    val title = if (isEditing) stringResource(R.string.edit_category) else stringResource(R.string.add_category)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = false
                },
                label = { Text(stringResource(R.string.category_name)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.name_required)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    nameError = true
                } else {
                    onSave(trimmed)
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
