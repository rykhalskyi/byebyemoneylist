package com.otakeeesen.byebyemoneylist.ui.components


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ReviewListViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewListDialog(
    shoppingList: ShoppingList,
    onDismiss: () -> Unit,
    onUpdateItem: (PurchaseItem, String, Double?, String) -> Unit,
    onMapToExisting: (PurchaseItem, ProductEntity) -> Unit,
    onDeleteItem: (PurchaseItem) -> Unit,
    viewModel: ReviewListViewModel = viewModel(factory = ReviewListViewModel.Factory)
) {
    val itemsToReview = shoppingList.items
    val allProducts by viewModel.allProducts.collectAsState()
    
    if (itemsToReview.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var expandedIndex by remember { mutableIntStateOf(0) }
    
    // Ensure expandedIndex is within bounds if items are deleted
    LaunchedEffect(itemsToReview.size) {
        if (expandedIndex >= itemsToReview.size && itemsToReview.isNotEmpty()) {
            expandedIndex = itemsToReview.size - 1
        }
    }

    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.review_list),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                itemsIndexed(itemsToReview) { index, item ->
                    ReviewItemAccordion(
                        item = item,
                        isExpanded = index == expandedIndex,
                        onExpand = { expandedIndex = index },
                        allProducts = allProducts,
                        onUpdate = { name, price, barcode ->
                            onUpdateItem(item, name, price, barcode)
                            if (index < itemsToReview.size - 1) {
                                expandedIndex++
                            } else {
                                onDismiss()
                            }
                        },
                        onMap = { product ->
                            onMapToExisting(item, product)
                            if (index < itemsToReview.size - 1) {
                                expandedIndex++
                            } else {
                                onDismiss()
                            }
                        },
                        onDelete = {
                            onDeleteItem(item)
                        },
                        isLast = index == itemsToReview.size - 1
                    )
                    if (index < itemsToReview.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewItemAccordion(
    item: PurchaseItem,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    allProducts: List<ProductEntity>,
    onUpdate: (String, Double?, String) -> Unit,
    onMap: (ProductEntity) -> Unit,
    onDelete: () -> Unit,
    isLast: Boolean
) {
    var name by remember(item, isExpanded) { mutableStateOf(item.name) }
    
    // Locale-aware price formatting
    val numberFormat = remember { NumberFormat.getInstance(Locale.getDefault()) }
    var priceText by remember(item, isExpanded) { 
        mutableStateOf(item.price?.let { numberFormat.format(it) } ?: "") 
    }
    
    var barcode by remember(item, isExpanded) { mutableStateOf("") }
    
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Normal
                )
                if (!isExpanded && item.price != null) {
                    Text(
                        text = numberFormat.format(item.price),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                SmartSelectField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.product_name),
                    items = allProducts,
                    itemToText = { it.name },
                    onItemSelected = { product ->
                        onMap(product)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.total_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text(stringResource(R.string.barcode)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            GmsBarcodeScanning.getClient(context).startScan()
                                .addOnSuccessListener { result: Barcode ->
                                    barcode = result.rawValue ?: ""
                                }
                        }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan_barcode))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.delete))
                    }

                    Button(
                        onClick = {
                            // Locale-aware price parsing
                            val parsedPrice = try {
                                numberFormat.parse(priceText)?.toDouble()
                            } catch (e: Exception) {
                                null
                            }
                            onUpdate(name, parsedPrice, barcode)
                        }
                    ) {
                        Text(stringResource(if (isLast) R.string.finish else R.string.next))
                    }
                }
            }
        }
    }
}

