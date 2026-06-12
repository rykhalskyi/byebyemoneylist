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
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.ui.viewmodel.StoreMergeViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StoreMergeScreen(
    storeAId: Long,
    storeBId: Long,
    onMergeComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: StoreMergeViewModel = viewModel(factory = StoreMergeViewModel.createFactory(storeAId, storeBId))
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
                title = { Text(stringResource(R.string.merge_stores_title)) },
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
            val a = uiState.storeA
            val b = uiState.storeB

            if (a != null && b != null) {
                MergeRow(
                    label = stringResource(R.string.store_name),
                    valueA = a.name,
                    valueB = b.name,
                    selectedValue = uiState.selectedName,
                    onSelect = viewModel::selectName,
                    onValueChange = viewModel::updateName
                )

                MergeRow(
                    label = stringResource(R.string.address),
                    valueA = a.address ?: "",
                    valueB = b.address ?: "",
                    selectedValue = uiState.selectedAddress,
                    onSelect = viewModel::selectAddress,
                    onValueChange = viewModel::updateAddress
                )

                StoreCategoryMergeRow(
                    label = stringResource(R.string.categories),
                    catsA = uiState.storeACategories,
                    catsB = uiState.storeBCategories,
                    selectedCats = uiState.selectedCategories,
                    onSelect = viewModel::selectCategories
                )

                StoreImageMergeRow(
                    label = stringResource(R.string.store_logo),
                    pathA = a.logoPath,
                    pathB = b.logoPath,
                    selectedPath = uiState.selectedLogoPath,
                    onSelect = viewModel::selectLogoPath
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
                        if (uiState.selectedAddress.isNotEmpty()) {
                            Text(text = uiState.selectedAddress)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            uiState.selectedCategories.forEach { category ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(category.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
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
                title = stringResource(R.string.store_a),
                value = valueA,
                isSelected = selectedValue == valueA,
                onClick = { onSelect(valueA) }
            )
            OptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.store_b),
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

@Composable
private fun StoreCategoryMergeRow(
    label: String,
    catsA: List<CategoryEntity>,
    catsB: List<CategoryEntity>,
    selectedCats: List<CategoryEntity>,
    onSelect: (List<CategoryEntity>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StoreCategoryOptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.store_a),
                categories = catsA,
                isSelected = selectedCats == catsA,
                onClick = { onSelect(catsA) }
            )
            StoreCategoryOptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.store_b),
                categories = catsB,
                isSelected = selectedCats == catsB,
                onClick = { onSelect(catsB) }
            )
        }
    }
}

@Composable
private fun StoreImageMergeRow(
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
            StoreImageOptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.store_a),
                path = pathA,
                isSelected = selectedPath == pathA,
                onClick = { onSelect(pathA) }
            )
            StoreImageOptionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.store_b),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreCategoryOptionCard(
    modifier: Modifier,
    title: String,
    categories: List<CategoryEntity>,
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
            if (categories.isEmpty()) {
                Text("-", style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(it.name, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreImageOptionCard(
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
