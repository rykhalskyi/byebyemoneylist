package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectCategoryField(
    selectedCategories: List<CategoryEntity>,
    allCategories: List<CategoryEntity>,
    onCategorySelected: (CategoryEntity) -> Unit,
    onCategoryRemoved: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Categories",
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
                        selectedContainerColor = Color(android.graphics.Color.parseColor(category.color)).copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            
            AssistChip(
                onClick = { showBottomSheet = true },
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

        if (showBottomSheet) {
            val availableCategories = allCategories.filter { it !in selectedCategories }
            if (availableCategories.isNotEmpty()) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            "Select Category",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        LazyColumn {
                            items(availableCategories) { category ->
                                ListItem(
                                    headlineContent = { Text(category.name) },
                                    modifier = Modifier.clickable {
                                        onCategorySelected(category)
                                        showBottomSheet = false
                                    },
                                    leadingContent = {
                                        Surface(
                                            modifier = Modifier.size(24.dp),
                                            shape = CircleShape,
                                            color = Color(android.graphics.Color.parseColor(category.color))
                                        ) {}
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                showBottomSheet = false
            }
        }
    }
}
