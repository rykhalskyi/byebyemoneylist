package com.otakeeesen.byebyemoneylist.ui.components.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ProductMergeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductMergeScreen(
    productAId: Long,
    productBId: Long,
    onMergeComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: ProductMergeViewModel = viewModel(factory = ProductMergeViewModel.createFactory(productAId, productBId))
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.mergeComplete) {
        if (uiState.mergeComplete) {
            onMergeComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merge_products_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (uiState.isMerging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.performMerge() }) {
                            Icon(Icons.Default.Done, contentDescription = stringResource(R.string.confirm_merge))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val a = uiState.productA
            val b = uiState.productB

            if (a != null && b != null) {
                MergeRow(
                    label = stringResource(R.string.product_name),
                    valueA = a.name,
                    valueB = b.name,
                    selectedValue = uiState.selectedName,
                    onSelect = viewModel::selectName,
                    onValueChange = viewModel::updateName
                )

                MergeRow(
                    label = stringResource(R.string.barcode),
                    valueA = a.barcode,
                    valueB = b.barcode,
                    selectedValue = uiState.selectedBarcode,
                    onSelect = viewModel::selectBarcode,
                    onValueChange = viewModel::updateBarcode
                )

                val categoryMap = uiState.allCategories.associate { it.id to it.name }
                val catAName = categoryMap[a.categoryId] ?: ""
                val catBName = categoryMap[b.categoryId] ?: ""
                val selectedCatName = categoryMap[uiState.selectedCategoryId] ?: ""

                CategoryMergeRow(
                    label = stringResource(R.string.category),
                    valueA = catAName,
                    idA = a.categoryId,
                    valueB = catBName,
                    idB = b.categoryId,
                    selectedValue = selectedCatName,
                    onSelect = viewModel::selectCategory,
                    allCategories = uiState.allCategories
                )

                ImageMergeRow(
                    label = stringResource(R.string.product_preview),
                    pathA = a.picturePath,
                    pathB = b.picturePath,
                    selectedPath = uiState.selectedPicturePath,
                    onSelect = viewModel::selectPicturePath
                )

                Spacer(Modifier.height(24.dp))
                
                Text(
                    text = stringResource(R.string.merged_result),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = uiState.selectedName, style = MaterialTheme.typography.headlineSmall)
                        Text(text = "${stringResource(R.string.category)}: $selectedCatName")
                        if (uiState.selectedBarcode.isNotEmpty()) {
                            Text(text = "${stringResource(R.string.barcode)}: ${uiState.selectedBarcode}")
                        }
                    }
                }
                
                Button(
                    onClick = { viewModel.performMerge() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isMerging
                ) {
                    Text(stringResource(R.string.confirm_merge))
                }
            }
        }
    }
}

@Composable
private fun MergeRow(
    label: String,
    valueA: String,
    valueB: String,
    selectedValue: String,
    onSelect: (String) -> Unit,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.product_a),
                value = valueA,
                isSelected = selectedValue == valueA,
                onClick = { onSelect(valueA) }
            )
            OptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.product_b),
                value = valueB,
                isSelected = selectedValue == valueB,
                onClick = { onSelect(valueB) }
            )
        }
        
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = selectedValue,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.merged_result)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryMergeRow(
    label: String,
    valueA: String,
    idA: Long?,
    valueB: String,
    idB: Long?,
    selectedValue: String,
    onSelect: (Long?) -> Unit,
    allCategories: List<com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.product_a),
                value = valueA,
                isSelected = selectedValue == valueA,
                onClick = { onSelect(idA) }
            )
            OptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.product_b),
                value = valueB,
                isSelected = selectedValue == valueB,
                onClick = { onSelect(idB) }
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                label = { Text(stringResource(R.string.merged_result)) },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                allCategories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onSelect(category.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageMergeRow(
    label: String,
    pathA: String?,
    pathB: String?,
    selectedPath: String?,
    onSelect: (String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageOptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.product_a),
                path = pathA,
                isSelected = selectedPath == pathA,
                onClick = { onSelect(pathA) }
            )
            ImageOptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.product_b),
                path = pathB,
                isSelected = selectedPath == pathB,
                onClick = { onSelect(pathB) }
            )
        }
    }
}

@Composable
private fun OptionCard(
    modifier: Modifier,
    title: String,
    value: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall)
            Text(text = value.ifEmpty { "-" }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ImageOptionCard(
    modifier: Modifier,
    title: String,
    path: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            if (path != null) {
                AsyncImage(
                    model = path,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    Text("-")
                }
            }
        }
    }
}
