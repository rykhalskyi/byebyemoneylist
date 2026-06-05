package com.otakeeesen.byebyemoneylist.ui.components.category

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors
import com.otakeeesen.byebyemoneylist.util.safeParseColor
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDialog(
    editingCategory: CategoryEntity?,
    allCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String, parentId: Long?) -> Unit,
) {
    var name by remember { mutableStateOf(editingCategory?.name ?: "") }
    var selectedColor by remember { mutableStateOf(editingCategory?.color ?: CategoryColors.DEFAULT_COLOR) }
    var parentId by remember { mutableStateOf(editingCategory?.parentId) }
    var nameError by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val isEditing = editingCategory != null
    val title = if (isEditing) stringResource(R.string.edit_category) else stringResource(R.string.add_category)

    val parentName = if (parentId == null) "None" else allCategories.find { it.id == parentId }?.name ?: "None"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
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
                
                Text(
                    text = stringResource(R.string.select_category_color),
                    modifier = Modifier.padding(top = 16.dp)
                )
                
                ColorPicker(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it },
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = parentName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Parent Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                parentId = null
                                expanded = false
                            }
                        )
                        
                        val availableParents = allCategories.filter { it.id != editingCategory?.id }
                        availableParents.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.size(16.dp),
                                            shape = CircleShape,
                                            color = safeParseColor(category.color)
                                        ) {}
                                        Spacer(Modifier.width(8.dp))
                                        Text(category.name)
                                    }
                                },
                                onClick = {
                                    parentId = category.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    nameError = true
                } else {
                    onSave(trimmed, selectedColor, parentId)
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

@Composable
fun ColorPicker(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        CategoryColors.RED,
        CategoryColors.BLUE,
        CategoryColors.GREEN,
        CategoryColors.YELLOW,
        CategoryColors.PURPLE,
        CategoryColors.ORANGE,
        CategoryColors.TEAL
    )
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(colors) { color ->
            Card(
                onClick = { onColorSelected(color) },
                colors = CardDefaults.cardColors(
                    containerColor = safeParseColor(color)
                ),
                shape = CircleShape,
                border = if (selectedColor == color) BorderStroke(2.dp, Color.Black) else null,
                modifier = Modifier
                    .size(32.dp)
            ) {
                // Empty content - just the colored circle
            }
        }
    }
}
