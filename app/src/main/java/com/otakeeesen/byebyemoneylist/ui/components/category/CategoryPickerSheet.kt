package com.otakeeesen.byebyemoneylist.ui.components.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.util.safeParseColor

enum class SelectionMode { Single, Multi }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<CategoryEntity>,
    selectedIds: Set<Long>,
    selectionMode: SelectionMode,
    title: String = "Select Categories",
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var localSelectedIds by remember(selectedIds) { mutableStateOf(selectedIds) }

    val sortedCategories = remember(categories) {
        categories.sortedBy { it.name.lowercase() }
    }

    val filteredCategories = remember(sortedCategories, query) {
        if (query.isBlank()) sortedCategories
        else sortedCategories.filter { it.name.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge
                )
                if (selectionMode == SelectionMode.Multi) {
                    Button(onClick = { onConfirm(localSelectedIds) }) {
                        Text(stringResource(com.otakeeesen.byebyemoneylist.R.string.done))
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search categories...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                } else null
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredCategories, key = { it.id }) { category ->
                    val isSelected = category.id in localSelectedIds
                    ListItem(
                        headlineContent = { Text(category.name) },
                        modifier = Modifier.clickable {
                            when (selectionMode) {
                                SelectionMode.Single -> {
                                    val newSet = if (isSelected) emptySet<Long>() else setOf(category.id)
                                    onConfirm(newSet)
                                }
                                SelectionMode.Multi -> {
                                    localSelectedIds = if (isSelected) localSelectedIds - category.id
                                    else localSelectedIds + category.id
                                }
                            }
                        },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = CircleShape,
                                color = safeParseColor(category.color)
                            ) {}
                        },
                        trailingContent = if (isSelected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null
                    )
                }
            }


        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryChipsField(
    selectedCategories: List<CategoryEntity>,
    allCategories: List<CategoryEntity>,
    selectionMode: SelectionMode = SelectionMode.Multi,
    onCategorySelected: (CategoryEntity) -> Unit,
    onCategoryRemoved: (CategoryEntity) -> Unit,
    labelResId: Int = com.otakeeesen.byebyemoneylist.R.string.category,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
            Text(
                text = stringResource(labelResId),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

        FlowRow(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            selectedCategories.forEach { category ->
                InputChip(
                    selected = true,
                    onClick = { onCategoryRemoved(category) },
                    label = { Text(category.name) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = safeParseColor(category.color).copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            if (selectionMode != SelectionMode.Single || selectedCategories.isEmpty()) {
                AssistChip(
                    onClick = { showSheet = true },
                    label = { Text("Add") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add category",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        if (showSheet) {
            val selectedIds = selectedCategories.map { it.id }.toSet()
            CategoryPickerSheet(
                categories = allCategories,
                selectedIds = selectedIds,
                selectionMode = selectionMode,
                title = "Select Categories",
                onDismiss = { showSheet = false },
                onConfirm = { ids ->
                    if (selectionMode == SelectionMode.Single) {
                        val removed = selectedCategories.firstOrNull()
                        removed?.let { onCategoryRemoved(it) }
                        ids.firstOrNull()?.let { id -> allCategories.find { it.id == id }?.let { onCategorySelected(it) } }
                    } else {
                        val currentIds = selectedCategories.map { it.id }.toSet()
                        val added = ids - currentIds
                        val removed = currentIds - ids
                        added.forEach { id ->
                            allCategories.find { it.id == id }?.let { onCategorySelected(it) }
                        }
                        removed.forEach { id ->
                            selectedCategories.find { it.id == id }?.let { onCategoryRemoved(it) }
                        }
                    }
                    showSheet = false
                }
            )
        }
    }
}
