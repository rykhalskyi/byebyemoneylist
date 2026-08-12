package com.otakeeesen.byebyemoneylist.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.ui.components.components.SmartSelectField
import com.otakeeesen.byebyemoneylist.ui.viewmodel.QuickPurchaseViewModel
import com.otakeeesen.byebyemoneylist.util.safeParseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPurchaseScreen(
    viewModel: QuickPurchaseViewModel = viewModel(factory = QuickPurchaseViewModel.Factory),
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentStep by remember { mutableIntStateOf(1) }

    LaunchedEffect(uiState.purchaseComplete) {
        if (uiState.purchaseComplete) {
            navController.popBackStack()
        }
    }

    val priceValid = uiState.price.trim().replace(',', '.').toDoubleOrNull()
        ?.let { it > 0.0 } ?: false
    val storeValid = uiState.storeText.isNotBlank()
    val nextEnabled = priceValid && storeValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_purchase_title)) },
                navigationIcon = {
                    if (currentStep == 2) {
                        IconButton(onClick = { currentStep = 1 }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_label))
                        }
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_label))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentStep) {
                1 -> Step1Content(
                    price = uiState.price,
                    onPriceChange = viewModel::updatePrice,
                    storeText = uiState.storeText,
                    onStoreChange = viewModel::updateStore,
                    stores = uiState.stores,
                    onNext = { currentStep = 2 },
                    nextEnabled = nextEnabled,
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> Step2Content(
                    categories = uiState.expenseCategories,
                    onCategorySelected = { category ->
                        viewModel.selectCategory(category)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (uiState.isCreating) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun Step1Content(
    price: String,
    onPriceChange: (String) -> Unit,
    storeText: String,
    onStoreChange: (String) -> Unit,
    stores: List<com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity>,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.quick_purchase_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = price,
            onValueChange = onPriceChange,
            label = { Text(stringResource(R.string.enter_price_2)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = price.isBlank(),
            supportingText = if (price.isBlank()) {
                { Text(stringResource(R.string.field_required)) }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )

        SmartSelectField(
            value = storeText,
            onValueChange = onStoreChange,
            label = stringResource(R.string.store_name),
            items = stores,
            itemToText = { it.name },
            onItemSelected = { onStoreChange(it.name) },
            isError = storeText.isBlank(),
            supportingText = if (storeText.isBlank()) {
                { Text(stringResource(R.string.field_required)) }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.next_button),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun Step2Content(
    categories: List<CategoryEntity>,
    onCategorySelected: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridItems = remember(categories) { buildCategoryGridItems(categories) }

    if (gridItems.isEmpty()) {
        Box(
            modifier = modifier.padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.no_categories_available),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.select_category_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = gridItems,
                key = { it.id },
            ) { category ->
                CategoryGridCard(
                    category = category,
                    onClick = { onCategorySelected(category) },
                )
            }
        }
    }
}

@Composable
private fun CategoryGridCard(
    category: CategoryEntity,
    onClick: () -> Unit,
) {
    val color = safeParseColor(category.color)
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(color.copy(alpha = 0.3f))
                .clickable(onClick = onClick)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = category.emoji ?: "",
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun buildCategoryGridItems(categories: List<CategoryEntity>): List<CategoryEntity> {
    val childrenByParent = categories.groupBy { it.parentId }
    val rootCategories = categories.filter { it.parentId == null }
    val usedChildIds = mutableSetOf<Long>()
    val result = mutableListOf<CategoryEntity>()

    rootCategories.forEach { parent ->
        val children = childrenByParent[parent.id] ?: emptyList()
        result.add(parent)
        usedChildIds.addAll(children.map { it.id })
        result.addAll(children)
    }

    val orphanChildren = categories.filter {
        it.parentId != null && it.id !in usedChildIds
    }
    result.addAll(orphanChildren)

    return result
}
