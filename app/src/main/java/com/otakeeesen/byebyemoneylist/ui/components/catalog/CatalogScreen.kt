package com.otakeeesen.byebyemoneylist.ui.components.catalog

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.otakeeesen.byebyemoneylist.ui.model.CategoryUiModel
import com.otakeeesen.byebyemoneylist.ui.components.category.CategoryDialog
import com.otakeeesen.byebyemoneylist.ui.components.shared.EmptyState
import com.otakeeesen.byebyemoneylist.ui.components.store.components.StoreScreen
import com.otakeeesen.byebyemoneylist.ui.viewmodel.CatalogViewModel
import com.otakeeesen.byebyemoneylist.util.safeParseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.Factory),
    onProductClick: (Long) -> Unit,
    onAddProduct: (Boolean) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row {
                    IconButton(onClick = { viewModel.toggleSearchPanel() }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_toggle_search),
                            tint = if (uiState.searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFilterPanel() }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.cd_toggle_filter),
                            tint = if (uiState.selectedCategoryIds.isNotEmpty())
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.toggleSortOrder() }) {
                        Icon(
                            imageVector = if (uiState.isSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = stringResource(R.string.cd_toggle_sorting)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (uiState.selectedTab) {
                        0 -> viewModel.showCreateCategoryDialog()
                        1 -> viewModel.showCreateStore()
                        2 -> onAddProduct(false)
                        3 -> onAddProduct(true)
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_product_catalog))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnimatedVisibility(
                visible = uiState.showSearchPanel,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                CatalogSearchPanel(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) }
                )
            }

            AnimatedVisibility(
                visible = uiState.showFilterPanel && uiState.selectedTab != 0,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                CatalogFilterPanel(
                    selectedCategoryIds = uiState.selectedCategoryIds,
                    onCategoryClick = { viewModel.toggleCategoryFilter(it) },
                    allCategories = uiState.categories,
                    onClearFilters = { viewModel.clearFilters() }
                )
            }

            val tabs = listOf(
                stringResource(R.string.categories) to Icons.Default.Category,
                stringResource(R.string.stores) to Icons.Default.Store,
                stringResource(R.string.products) to Icons.Default.Inventory2,
                stringResource(R.string.subscriptions) to Icons.Default.CalendarMonth,
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
                3 -> ProductListTab(
                    products = uiState.filteredSubscriptionProducts,
                    onEdit = { onProductClick(it.id) },
                    onDelete = viewModel::requestDeleteProduct,
                )
                }

        }
    }

    if (uiState.categoryDialogVisible) {
        CategoryDialog(
            editingCategory = uiState.editingCategory,
            allCategories = uiState.categories.map { CategoryEntity(id = it.id, name = it.name, color = it.color.toString(), parentId = it.parentId) },
            onDismiss = viewModel::dismissCategoryDialog,
            onSave = viewModel::saveCategory,
        )
    }

    if (uiState.editingStore != null || uiState.isCreatingStore) {
        StoreScreen(
            store = uiState.editingStore,
            categories = uiState.categories.map { CategoryEntity(id = it.id, name = it.name, color = it.color.toString(), parentId = it.parentId) },
            storeCategories = uiState.editingStoreCategories.map { CategoryEntity(id = it.id, name = it.name, color = it.color.toString(), parentId = it.parentId) },
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
    categories: List<CategoryUiModel>,
    onEdit: (CategoryUiModel) -> Unit,
    onDelete: (CategoryUiModel) -> Unit,
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
                    color = categoryWithDepth.category.color.toString(),
                    modifier = Modifier.padding(start = (categoryWithDepth.depth * 16).dp)
                )
            }
        }
    }
}

private data class CategoryWithDepth(val category: CategoryUiModel, val depth: Int)

private fun buildStructuredCategories(categories: List<CategoryUiModel>): List<CategoryWithDepth> {
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
    categories: List<CategoryUiModel>, // Changed: need all categories to find tags
    storeCategories: Map<Long, List<CategoryUiModel>>, // New: storeId -> list of categories
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
                val parsedColor = safeParseColor(color)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(parsedColor)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                    }
                }
            } else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogFilterPanel(
    selectedCategoryIds: Set<Long>,
    onCategoryClick: (Long) -> Unit,
    allCategories: List<CategoryUiModel>,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (allCategories.isNotEmpty()) {
            Text(stringResource(R.string.categories), style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allCategories, key = { it.id }) { category ->
                    val isSelected = category.id in selectedCategoryIds

                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategoryClick(category.id) },
                        label = { Text(category.name) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = category.color.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

