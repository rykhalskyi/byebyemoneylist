package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.ui.viewmodel.CatalogEvent
import com.otakeeesen.byebyemoneylist.ui.viewmodel.CatalogViewModel
import androidx.compose.foundation.layout.IntrinsicSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(modifier: Modifier = Modifier) {
    val viewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    val categoryDeletedMessage = stringResource(R.string.category_deleted)
    val storeDeletedMessage = stringResource(R.string.store_deleted)
    val productDeletedMessage = stringResource(R.string.product_deleted)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event != null) {
                val message = when (event) {
                    is CatalogEvent.CategoryDeleted -> categoryDeletedMessage
                    is CatalogEvent.StoreDeleted -> storeDeletedMessage
                    is CatalogEvent.ProductDeleted -> productDeletedMessage
                }
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoDelete()
                }
                viewModel.clearEvent()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (searchActive) {
                CenterAlignedTopAppBar(
                    title = {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text(stringResource(R.string.search)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            searchActive = false
                            viewModel.updateSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("Catalog") },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (uiState.selectedTab) {
                        0 -> viewModel.showCreateCategoryDialog()
                        1 -> viewModel.showCreateStoreDialog()
                        2 -> viewModel.showCreateProductDialog()
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
                    onEdit = viewModel::showEditStoreDialog,
                    onDelete = viewModel::requestDeleteStore,
                )
                2 -> ProductListTab(
                    products = uiState.filteredProducts,
                    onEdit = viewModel::showEditProductDialog,
                    onDelete = viewModel::requestDeleteProduct,
                )
            }
        }
    }

    if (uiState.categoryDialogVisible) {
        CategoryDialog(
            editingCategory = uiState.editingCategory,
            onDismiss = viewModel::dismissCategoryDialog,
            onSave = viewModel::saveCategory,
        )
    }

    if (uiState.storeDialogVisible) {
        StoreDialog(
            editingStore = uiState.editingStore,
            categories = uiState.categories,
            onDismiss = viewModel::dismissStoreDialog,
            onSave = viewModel::saveStore,
        )
    }

    if (uiState.productDialogVisible) {
        ProductDialog(
            editingProduct = uiState.editingProduct,
            categories = uiState.categories,
            onDismiss = viewModel::dismissProductDialog,
            onSave = viewModel::saveProduct,
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(categories, key = { it.id }) { category ->
                EntityListItem(
                    title = category.name,
                    onClick = { onEdit(category) },
                    onDelete = { onDelete(category) },
                    color = category.color,
                )
            }
        }
    }
}

@Composable
private fun StoreListTab(
    stores: List<StoreEntity>,
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
                EntityListItem(
                    title = store.name,
                    subtitle = store.category,
                    onClick = { onEdit(store) },
                    onDelete = { onDelete(store) },
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
    color: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Colored bar on the left

        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!color.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(Color(AndroidColor.parseColor(color)))
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (!color.isNullOrBlank()) 8.dp else 0.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
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

@Composable
private fun EmptyState(icon: ImageVector, message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
