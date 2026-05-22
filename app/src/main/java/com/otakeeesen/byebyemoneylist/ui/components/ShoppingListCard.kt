package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
 import androidx.compose.foundation.layout.Arrangement
 import androidx.compose.foundation.layout.Box
 import androidx.compose.foundation.layout.Column
 import androidx.compose.foundation.layout.IntrinsicSize
 import androidx.compose.foundation.layout.Row
 import androidx.compose.foundation.layout.Spacer
 import androidx.compose.foundation.layout.fillMaxSize
 import androidx.compose.foundation.layout.fillMaxWidth
 import androidx.compose.foundation.layout.height
 import androidx.compose.foundation.layout.offset
 import androidx.compose.foundation.layout.padding
 import androidx.compose.foundation.layout.size
 import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

fun parseColor(colorString: String): Color {
    val hex = colorString.removePrefix("#")
    val color = if (hex.length == 6) {
        // #RRGGBB format - add full opacity
        "FF$hex".toLong(16)
    } else {
        // Assume #AARRGGBB or other formats
        hex.toLong(16)
    }
    return Color(
        (color shr 16 and 0xFF).toInt(),
        (color shr 8 and 0xFF).toInt(),
        (color and 0xFF).toInt(),
        (color shr 24 and 0xFF).toInt()
    )
}

@Composable
fun ShoppingListCard(
    shoppingList: ShoppingList,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onItemCheckedChange: (PurchaseItem, Boolean) -> Unit = { _, _ -> },
    onAddItem: () -> Unit = {},
    onDeleteList: () -> Unit = {},
    onDeleteItem: (PurchaseItem) -> Unit = {},
    onFinishAndPay: () -> Unit = {},
    onReorderItems: (List<PurchaseItem>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var localItems by remember(shoppingList.items) { mutableStateOf(shoppingList.items) }

    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = "rotation"
    )

    LaunchedEffect(shoppingList.items) {
        localItems = shoppingList.items
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .animateContentSize(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // Vertical color bar on the left
            if (shoppingList.categoryColor != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(
                            color = parseColor(shoppingList.categoryColor),
                            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                        )
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
            ) {
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
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
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotationState),
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
            AnimatedVisibility(visible = isExpanded) {
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
                                val coroutineScope = rememberCoroutineScope()
                                val offsetX = remember { Animatable(0f) }
                                var itemWidth by remember { mutableIntStateOf(0) }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clipToBounds()
                                        .onSizeChanged { itemWidth = it.width },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                Color(0xFFE53935),
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .padding(end = 20.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete_item),
                                            tint = Color.White,
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .offset { IntOffset(offsetX.value.toInt(), 0) }
                                            .pointerInput(Unit) {
                                                detectHorizontalDragGestures(
                                                    onDragEnd = {
                                                        coroutineScope.launch {
                                                            if (offsetX.value < -itemWidth * 0.3f) {
                                                                onDeleteItem(item)
                                                            } else {
                                                                offsetX.animateTo(0f, tween(300))
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        coroutineScope.launch {
                                                            offsetX.animateTo(0f, tween(300))
                                                        }
                                                    },
                                                    onHorizontalDrag = { _, dragAmount ->
                                                        coroutineScope.launch {
                                                            offsetX.snapTo(
                                                                (offsetX.value + dragAmount).coerceIn(-itemWidth.toFloat(), 0f)
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                            .background(
                                                MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(12.dp),
                                            )
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
}
