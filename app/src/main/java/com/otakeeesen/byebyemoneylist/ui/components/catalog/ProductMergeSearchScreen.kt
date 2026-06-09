package com.otakeeesen.byebyemoneylist.ui.components.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.ui.components.shared.EmptyState
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ProductMergeSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductMergeSearchScreen(
    productAId: Long,
    onProductSelected: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ProductMergeSearchViewModel = viewModel(factory = ProductMergeSearchViewModel.createFactory(productAId))
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.select_product_to_merge)) },
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
    ) { innerPadding ->
        if (uiState.filteredProducts.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Inventory2,
                message = stringResource(R.string.no_products),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(uiState.filteredProducts, key = { it.id }) { product ->
                    val categoryName = uiState.allCategories.find { it.id == product.categoryId }?.name ?: ""
                    ListItem(
                        headlineContent = { Text(product.name) },
                        supportingContent = { 
                            if (categoryName.isNotEmpty()) {
                                Text(categoryName)
                            }
                        },
                        modifier = Modifier.clickable { onProductSelected(product.id) }
                    )
                }
            }
        }
    }
}
