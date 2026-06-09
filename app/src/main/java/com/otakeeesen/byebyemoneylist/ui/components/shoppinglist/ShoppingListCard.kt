package com.otakeeesen.byebyemoneylist.ui.components.shoppinglist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Storefront

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun parseColor(colorString: String): Color {
    val hex = colorString.removePrefix("#")
    val color = if (hex.length == 6) {
        "FF$hex".toLong(16)
    } else {
        hex.toLong(16)
    }
    return Color(
        (color shr 16 and 0xFF).toInt(),
        (color shr 8 and 0xFF).toInt(),
        (color and 0xFF).toInt(),
        (color shr 24 and 0xFF).toInt()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShoppingListCard(
    shoppingList: ShoppingList,
    actualPriceRule: String,
    isExpanded: Boolean = false,
    isInStore: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onToggleStoreMode: () -> Unit = {},
    onItemCheckedChange: (PurchaseItem, Boolean) -> Unit = { _, _ -> },
    onAddItem: () -> Unit = {},
    onEditList: () -> Unit = {},
    onDeleteList: () -> Unit = {},
    onDeleteItem: (PurchaseItem) -> Unit = {},
    onEditItem: (PurchaseItem) -> Unit = {},
    onReviewList: () -> Unit = {},
    onUnarchiveList: () -> Unit = {},
    onFinishAndPay: () -> Unit = {},
    onReorderItems: (List<PurchaseItem>) -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    var hideCheckedItems by remember { mutableStateOf(preferencesManager.getHideCheckedItems()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var localItems by remember(shoppingList.items) { mutableStateOf(shoppingList.items) }

    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = "rotation"
    )

    LaunchedEffect(shoppingList.items) {
        localItems = shoppingList.items
    }

    val displayItems = if (hideCheckedItems) {
        localItems.filter { !it.checked }
    } else {
        localItems
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .animateContentSize()
            .then(if (shoppingList.isArchived) Modifier.alpha(0.8f) else Modifier),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (shoppingList.isArchived) 1.dp else 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            if (shoppingList.categories.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                ) {
                    shoppingList.categories.forEach { category ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(parseColor(category.color))
                        )
                    }
                }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusIcon = when {
                                shoppingList.isSubscription -> Icons.Default.CalendarMonth
                                shoppingList.isArchived -> Icons.Default.Archive
                                shoppingList.isFinished -> Icons.Default.CheckCircle
                                isInStore -> Icons.Default.Storefront
                                else -> Icons.Default.FiberNew
                            }
                            val statusTint = when {
                                shoppingList.isSubscription -> Color(0xFF4CAF50)
                                shoppingList.isArchived -> MaterialTheme.colorScheme.outline
                                shoppingList.isFinished -> MaterialTheme.colorScheme.secondary
                                isInStore -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                            val statusDescription = when {
                                shoppingList.isSubscription -> stringResource(R.string.subscription)
                                shoppingList.isArchived -> stringResource(R.string.cd_status_archived)
                                shoppingList.isFinished -> stringResource(R.string.cd_status_finished)
                                isInStore -> stringResource(R.string.cd_status_instore)
                                else -> stringResource(R.string.cd_status_new)
                            }

                            if (shoppingList.isArchived || shoppingList.isSubscription) {
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = statusDescription,
                                    modifier = Modifier.size(20.dp),
                                    tint = statusTint
                                )

                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Text(
                                text = shoppingList.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (shoppingList.isRecurring) {
                                Icon(
                                    imageVector = Icons.Default.Autorenew,
                                    contentDescription = "Recurring",
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

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

                            Text(
                                text = stringResource(R.string.dual_price_display, shoppingList.purchasePrice, shoppingList.itemsTotal),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (shoppingList.isFinished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .then(
                                    if (shoppingList.isFinished) Modifier.clickable { onFinishAndPay() } else Modifier
                                ),
                            content = {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                Text(
                                    text = com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(shoppingList.calculateActualPrice(actualPriceRule), context),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (shoppingList.isFinished) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        )

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
                                        text = { Text(stringResource(R.string.edit_list)) },
                                        onClick = {
                                            onEditList()
                                            menuExpanded = false
                                        },
                                    )
                                    if (shoppingList.items.any { it.productStatus == "added" }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.review_list)) },
                                            onClick = {
                                                onReviewList()
                                                menuExpanded = false
                                            },
                                        )
                                    }
                                    if (!shoppingList.isFinished && !shoppingList.isArchived && !shoppingList.isSubscription) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(if (isInStore) R.string.exit_store_mode else R.string.enter_store_mode)) },
                                            onClick = {
                                                onToggleStoreMode()
                                                menuExpanded = false
                                            },
                                        )
                                    }
                                    if (shoppingList.isArchived) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.unarchive)) },
                                            onClick = {
                                                onUnarchiveList()
                                                menuExpanded = false
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = stringResource(R.string.delete_list),
                                                color = MaterialTheme.colorScheme.error
                                            ) 
                                        },
                                        onClick = {
                                            showDeleteConfirmation = true
                                            menuExpanded = false
                                        },
                                    )
                                }
                                if (showDeleteConfirmation) {
                                    androidx.compose.material3.AlertDialog(
                                        onDismissRequest = { showDeleteConfirmation = false },
                                        title = { Text(stringResource(R.string.delete_list)) },
                                        text = { Text(stringResource(R.string.delete_confirm, shoppingList.title)) },
                                        confirmButton = {
                                            androidx.compose.material3.TextButton(
                                                onClick = {
                                                    onDeleteList()
                                                    showDeleteConfirmation = false
                                                }
                                            ) {
                                                Text(stringResource(R.string.yes))
                                            }
                                        },
                                        dismissButton = {
                                            androidx.compose.material3.TextButton(
                                                onClick = { showDeleteConfirmation = false }
                                            ) {
                                                Text(stringResource(R.string.no))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        ReorderableColumn(
                            list = displayItems,
                            onSettle = { fromIndex, toIndex ->
                                val fromItem = displayItems[fromIndex]
                                val toItem = displayItems[toIndex]
                                val fromActualIndex = localItems.indexOf(fromItem)
                                val toActualIndex = localItems.indexOf(toItem)
                                localItems = localItems.toMutableList().apply { add(toActualIndex, removeAt(fromActualIndex)) }
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
                                                .clickable { onEditItem(item) }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (!shoppingList.isSubscription && !shoppingList.isFinished) {
                                                Checkbox(
                                                    checked = item.checked,
                                                    onCheckedChange = { onItemCheckedChange(item, it) },
                                                    enabled = isInStore,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                            }

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
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = item.name,
                                                        style = if (isInStore) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                val quantityText = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val context = androidx.compose.ui.platform.LocalContext.current
                                                    Text(
                                                        text = if (shoppingList.isSubscription) 
                                                            com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(item.price ?: 0.0, context) 
                                                        else "$quantityText x " + com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(item.price ?: 0.0, context),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    if (item.discount != null && item.discount != 0.0) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "- " + com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(item.discount!!, context),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
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

                        if (!shoppingList.isArchived) {
                            if (isInStore) {
                                Button(
                                    onClick = onAddItem,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.add_product))
                                }
                            } else {
                                Button(
                                    onClick = onAddItem,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.add_product))
                                }
                            }

                            if (!shoppingList.isSubscription && !shoppingList.isFinished && !shoppingList.isArchived) {
                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = onToggleStoreMode,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(if (isInStore) R.string.exit_store_mode else R.string.enter_store_mode))
                                }
                            }

                            if (!shoppingList.isRecurring && !shoppingList.isSubscription) {
                                Spacer(modifier = Modifier.height(8.dp))

                                if (shoppingList.isFinished) {
                                    Button(
                                        onClick = onReviewList,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.review_and_archive))
                                    }
                                } else {
                                    Button(
                                        onClick = onFinishAndPay,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.purchase))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
