package com.otakeeesen.byebyemoneylist.ui.components.category

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.CategoryEmoji
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors
import com.otakeeesen.byebyemoneylist.util.safeParseColor
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity

@Composable
fun CategoryDialog(
    editingCategory: CategoryEntity?,
    allCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String, parentId: Long?, isIncome: Boolean, emoji: String?) -> Unit,
) {
    var name by remember { mutableStateOf(editingCategory?.name ?: "") }
    var selectedColor by remember { mutableStateOf(editingCategory?.color ?: CategoryColors.DEFAULT_COLOR) }
    var parentId by remember { mutableStateOf(editingCategory?.parentId) }
    var isIncome by remember { mutableStateOf(editingCategory?.isIncome ?: false) }
    var selectedEmoji by remember { mutableStateOf(editingCategory?.emoji) }
    var nameError by remember { mutableStateOf(false) }

    val isEditing = editingCategory != null
    val title = if (isEditing) stringResource(R.string.edit_category) else stringResource(R.string.add_category)

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

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = isIncome,
                        onCheckedChange = { newIsIncome ->
                            isIncome = newIsIncome
                            // Check if current parent is compatible with new isIncome
                            val currentParent = allCategories.find { it.id == parentId }
                            if (currentParent != null && currentParent.isIncome != newIsIncome) {
                                parentId = null
                            }
                        }
                    )
                    Text(stringResource(R.string.is_income), modifier = Modifier.padding(start = 8.dp))
                }
                
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

                Text(
                    text = stringResource(R.string.select_category_emoji),
                    modifier = Modifier.padding(top = 16.dp)
                )

                EmojiPicker(
                    selectedEmoji = selectedEmoji,
                    onEmojiSelected = { selectedEmoji = it },
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                val availableParents = allCategories.filter {
                    it.id != editingCategory?.id && it.isIncome == isIncome
                }

                CategoryChipsField(
                    selectedCategories = availableParents.filter { it.id == parentId },
                    allCategories = availableParents,
                    selectionMode = SelectionMode.Single,
                    onCategorySelected = { parentId = it.id },
                    onCategoryRemoved = { parentId = null },
                    labelResId = R.string.parent_category,
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
                    onSave(trimmed, selectedColor, parentId, isIncome, selectedEmoji)
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

@Composable
fun EmojiPicker(
    selectedEmoji: String?,
    onEmojiSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val groups = CategoryEmoji.GROUPS

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = selectedEmoji ?: "🙂",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            Text(
                text = selectedEmoji ?: stringResource(R.string.emoji_group_other),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onEmojiSelected(null) }) {
                Text(stringResource(R.string.clear_emoji))
            }
        }

        ScrollableTabRow(selectedTabIndex = selectedTab.coerceIn(0, groups.lastIndex)) {
            groups.forEachIndexed { index, group ->
                Tab(
                    selected = index == selectedTab,
                    onClick = { selectedTab = index },
                    text = { Text(stringResource(group.nameResId)) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val currentGroup = groups[selectedTab.coerceIn(0, groups.lastIndex)]
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
        ) {
            items(currentGroup.emojis) { emoji ->
                Card(
                    onClick = { onEmojiSelected(emoji) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (emoji == selectedEmoji) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    border = if (emoji == selectedEmoji) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else null,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
