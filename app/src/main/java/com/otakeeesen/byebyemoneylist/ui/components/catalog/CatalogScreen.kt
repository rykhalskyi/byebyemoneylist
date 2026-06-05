package com.otakeeesen.byebyemoneylist.ui.components.catalog

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.ui.components.category.CategoryDialog
import com.otakeeesen.byebyemoneylist.ui.components.shared.EmptyState
import com.otakeeesen.byebyemoneylist.ui.components.store.components.StoreScreen
import com.otakeeesen.byebyemoneylist.ui.viewmodel.CatalogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.Factory),
    onProductClick: (Long) -> Unit,
    onAddProduct: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { 
                            Text(
                                stringResource(
                                    when (uiState.selectedTab) {
                                        0 -> R.string.search_categories
                                        1 -> R.string.search_stores
                                        else -> R.string.search_product
                                    }
                                )
                            ) 
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        singleLine = true,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (uiState.selectedTab) {
                        0 -> viewModel.showCreateCategoryDialog()
                        1 -> viewModel.showCreateStore()
                        2 -> onAddProduct()
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val tabs = listOf(
                stringResource(R.string.categories) to Icons.Default.Category,
                stringResource(R.string.stores) to Icons.Default.Store,
                stringResource(R.string.products) to Icons.Default.Inventory2,
            )

            SecondaryTabRow(selectedTabIndex = uiState.selectedTab) {
                tabs.forEachIndexed { index, (label, icon) ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(label) },
                        icon = { Icon(icon, contentDescription = label) },
                    )
                }
            }

            when (uiState.selectedTab) {
                0 -> CategoryListTab(
                    categories = uiState.filteredCategories,
                    onEdit = viewModel::showEditCategoryDialog,
                    onDelete = viewModel::requestDeleteCategory,
                )
                1 -> StoreListTab(
                    stores = uiState.filteredStores,
                    categories = uiState.categories,
                    storeCategories = uiState.storeCategories,
                    onEdit = { 
                        viewModel.showEditStore(it)
                    },
                    onDelete = viewModel::requestDeleteStore,
                )
                2 -> ProductListTab(
                    products = uiState.filteredProducts,
                    onEdit = { onProductClick(it.id) },
                    onDelete = viewModel::requestDeleteProduct,
                )
                }

        }
    }

    if (uiState.categoryDialogVisible) {
        CategoryDialog(
            editingCategory = uiState.editingCategory,
            allCategories = uiState.categories,
            onDismiss = viewModel::dismissCategoryDialog,
            onSave = viewModel::saveCategory,
        )
    }

    if (uiState.editingStore != null || uiState.isCreatingStore) {
        StoreScreen(
            store = uiState.editingStore,
            categories = uiState.categories,
            storeCategories = uiState.editingStoreCategories,
            onNavigateBack = viewModel::clearEditingStore,
            onSave = viewModel::saveStore,
        )
    }

    uiState.deleteConfirmMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CategoryListTab(
    categories: List<CategoryEntity>,
    onEdit: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    if (categories.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Category,
            message = stringResource(R.string.no_categories),
        )
    } else {
        val structuredCategories = remember(categories) {
            buildStructuredCategories(categories)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = structuredCategories,
                key = { it.category.id }
            ) { categoryWithDepth ->
                EntityListItem(
                    title = categoryWithDepth.category.name,
                    onClick = { onEdit(categoryWithDepth.category) },
                    onDelete = { onDelete(categoryWithDepth.category) },
                    color = categoryWithDepth.category.color,
                    modifier = Modifier.padding(start = (categoryWithDepth.depth * 16).dp)
                )
            }
        }
    }
}

private data class CategoryWithDepth(val category: CategoryEntity, val depth: Int)

private fun buildStructuredCategories(categories: List<CategoryEntity>): List<CategoryWithDepth> {
    val result = mutableListOf<CategoryWithDepth>()
    val grouped = categories.groupBy { it.parentId }
    
    fun addChildren(parentId: Long?, depth: Int) {
        grouped[parentId]?.sortedBy { it.name }?.forEach { category ->
            result.add(CategoryWithDepth(category, depth))
            addChildren(category.id, depth + 1)
        }
    }
    
    addChildren(null, 0)
    
    // Add orphans (if any)
    val addedIds = result.map { it.category.id }.toSet()
    categories.filter { it.id !in addedIds }.forEach { category ->
        result.add(CategoryWithDepth(category, 0))
    }
    
    return result
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreListTab(
    stores: List<StoreEntity>,
    categories: List<CategoryEntity>, // Changed: need all categories to find tags
    storeCategories: Map<Long, List<CategoryEntity>>, // New: storeId -> list of categories
    onEdit: (StoreEntity) -> Unit,
    onDelete: (StoreEntity) -> Unit,
) {
    if (stores.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Store,
            message = stringResource(R.string.no_stores),
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(stores, key = { it.id }) { store ->
                val tags = storeCategories[store.id] ?: emptyList()
                EntityListItem(
                    title = store.name,
                    subtitle = null, // Subtitle no longer used for single category
                    onClick = { onEdit(store) },
                    onDelete = { onDelete(store) },
                    statusContent = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            tags.forEach { category ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(category.name, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProductListTab(
    products: List<ProductEntity>,
    onEdit: (ProductEntity) -> Unit,
    onDelete: (ProductEntity) -> Unit,
) {
    if (products.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Inventory2,
            message = stringResource(R.string.no_products),
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(products, key = { it.id }) { product ->
                EntityListItem(
                    title = product.name,
                    subtitle = product.category,
                    onClick = { onEdit(product) },
                    onDelete = { onDelete(product) },
                    statusContent = {
                        when {
                            product.barcode.isNotBlank() -> {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = "Barcode",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            product.status == "reviewed" -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Reviewed",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.tertiary, androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EntityListItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMerge: (() -> Unit)? = null,
    color: String? = null,
    statusContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!color.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(Color(AndroidColor.parseColor(color)))
                )
                Spacer(Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (statusContent != null) {
                        Spacer(Modifier.width(8.dp))
                        statusContent()
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
