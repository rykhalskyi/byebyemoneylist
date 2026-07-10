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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import com.otakeeesen.byebyemoneylist.util.toHexString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.Factory),
    onProductClick: (Long) -> Unit,
    onAddProduct: (Boolean, Boolean) -> Unit,
    onMergeStore: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            tint = if (uiState.selectedCategoryIds.isNotEmpty() || uiState.filterFavorites)
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
                        2 -> onAddProduct(false, false)
                        3 -> onAddProduct(true, false)
                        4 -> onAddProduct(false, true) // Income tab
                    }
                }
            ) {
                val icon = Icons.Default.Add
                val contentDescription = if (uiState.selectedTab == 4) 
                    stringResource(R.string.add_income_source) 
                else 
                    stringResource(R.string.add_product_catalog)
                
                Icon(icon, contentDescription = contentDescription)
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
                    filterFavorites = uiState.filterFavorites,
                    onCategoryClick = { viewModel.toggleCategoryFilter(it) },
                    onToggleFavorites = { viewModel.toggleFavoriteFilter() },
                    allCategories = uiState.categories,
                    onClearFilters = { viewModel.clearFilters() }
                )
            }

            val tabs = mutableListOf(
                stringResource(R.string.categories) to Icons.Default.Category,
                stringResource(R.string.stores) to Icons.Default.Store,
                stringResource(R.string.products) to Icons.Default.Inventory2,
                stringResource(R.string.subscriptions) to Icons.Default.CalendarMonth,
                stringResource(R.string.income) to Icons.Default.ArrowUpward,
            )

            SecondaryScrollableTabRow(
                selectedTabIndex = uiState.selectedTab,
                edgePadding = 16.dp
            ) {
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
                    categories = uiState.categories,
                    onEdit = { onProductClick(it.id) },
                    onDelete = viewModel::requestDeleteProduct,
                )
                3 -> ProductListTab(
                    products = uiState.filteredSubscriptionProducts,
                    categories = uiState.categories,
                    onEdit = { onProductClick(it.id) },
                    onDelete = viewModel::requestDeleteProduct,
                )
                4 -> ProductListTab(
                    products = uiState.filteredIncomeProducts,
                    categories = uiState.categories,
                    onEdit = { onProductClick(it.id) },
                    onDelete = viewModel::requestDeleteProduct,
                )
            }

        }
    }

    if (uiState.categoryDialogVisible) {
        CategoryDialog(
            editingCategory = uiState.editingCategory,
            allCategories = uiState.categories.map { CategoryEntity(id = it.id, name = it.name, color = toHexString(it.color).toString(), parentId = it.parentId, isIncome = it.isIncome) },
            onDismiss = viewModel::dismissCategoryDialog,
            onSave = viewModel::saveCategory,
        )
    }

    if (uiState.editingStore != null || uiState.isCreatingStore) {
        StoreScreen(
            store = uiState.editingStore,
            categories = uiState.categories.map { CategoryEntity(id = it.id, name = it.name, color = toHexString(it.color), parentId = it.parentId, isIncome = it.isIncome) },
            storeCategories = uiState.editingStoreCategories.map { CategoryEntity(id = it.id, name = it.name, color = it.color, parentId = it.parentId, isIncome = it.isIncome) },
            onNavigateBack = viewModel::clearEditingStore,
            onSave = viewModel::saveStore,
            onMerge = { onMergeStore(it) }
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
                    color = categoryWithDepth.category.color,
                    statusContent = if (categoryWithDepth.category.isIncome) {
                        {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = stringResource(R.string.income),
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF4CAF50)
                            )
                        }
                    } else null,
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
                    subtitle = store.address,
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
    categories: List<CategoryUiModel>,
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
                val categoryName = categories.find { it.id == product.categoryId }?.name ?: ""
                EntityListItem(
                    title = product.name,
                    subtitle = categoryName,
                    onClick = { onEdit(product) },
                    onDelete = { onDelete(product) },
                    isFavorite = product.isFavorite,
                    statusContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            if (product.categoryId == null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Red, androidx.compose.foundation.shape.CircleShape)
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
    color: Color? = null,
    statusContent: (@Composable () -> Unit)? = null,
    isFavorite: Boolean = false,
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
            if (color != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(color)
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
                    if (isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = stringResource(R.string.favorite),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
    filterFavorites: Boolean,
    onCategoryClick: (Long) -> Unit,
    onToggleFavorites: () -> Unit,
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
                item {
                    FilterChip(
                        selected = filterFavorites,
                        onClick = onToggleFavorites,
                        label = { Text(stringResource(R.string.favorites)) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (filterFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
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
