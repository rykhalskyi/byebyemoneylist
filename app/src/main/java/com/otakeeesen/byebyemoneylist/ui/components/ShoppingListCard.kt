package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

@Composable
fun ShoppingListCard(
    shoppingList: ShoppingList,
    onItemCheckedChange: (PurchaseItem, Boolean) -> Unit = { _, _ -> },
    onAddItem: () -> Unit = {},
    onDeleteList: () -> Unit = {},
    onFinishAndPay: () -> Unit = {},
    onReorderItems: (List<PurchaseItem>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var isCardExpanded by remember { mutableStateOf(false) }
    var localItems by remember(shoppingList.items) { mutableStateOf(shoppingList.items) }

    LaunchedEffect(shoppingList.items) {
        localItems = shoppingList.items
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .animateContentSize(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isCardExpanded = !isCardExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shoppingList.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val storeText = shoppingList.storeName ?: stringResource(R.string.no_store)
                        Text(
                            text = storeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (shoppingList.createDate > 0L) {
                            val dateText = SimpleDateFormat("dd MMM", Locale.getDefault())
                                .format(Date(shoppingList.createDate))
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.progress_items, shoppingList.checkedCount, shoppingList.totalCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        if (shoppingList.finalTotal != null) {
                            Text(
                                text = stringResource(R.string.final_total, shoppingList.finalTotal),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.estimated_total, shoppingList.estimatedTotal),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isCardExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_list)) },
                                onClick = {
                                    onDeleteList()
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // Expanded content
            AnimatedVisibility(visible = isCardExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    // Purchases list
                    ReorderableColumn(
                        list = localItems,
                        onSettle = { fromIndex, toIndex ->
                            localItems = localItems.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                            onReorderItems(localItems)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) { index, item, isDragging ->
                        key(item.id) {
                            ReorderableItem {
                                val scope = this
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = item.checked,
                                        onCheckedChange = { onItemCheckedChange(item, it) },
                                    )

                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.name,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .padding(end = 8.dp),
                                        contentScale = ContentScale.Crop,
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = "€%.2f".format(item.price),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = stringResource(R.string.reorder_item),
                                        modifier = with(scope) {
                                            Modifier.draggableHandle()
                                        }.padding(start = 8.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Add Product button
                    FilledTonalButton(
                        onClick = onAddItem,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.add_product))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Finish & Pay button
                    Button(
                        onClick = onFinishAndPay,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.finish_and_pay))
                    }
                }
            }
        }
    }
}
