package com.otakeeesen.byebyemoneylist.ui.components.analytics

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.filled.Refresh
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.ui.viewmodel.AnalyticsViewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ProductStat
import com.otakeeesen.byebyemoneylist.util.safeParseColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showTrendDialog by remember { mutableStateOf<ProductStat?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_analytics)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                MonthPicker(
                    selectedMonth = uiState.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    onPrevious = { viewModel.previousMonth() },
                    onNext = { viewModel.nextMonth() }
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                MonthlyComparisonCard(
                    currentTotal = uiState.totalSpent,
                    previousTotal = uiState.previousMonthTotal
                )

                Spacer(modifier = Modifier.height(16.dp))

                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text(stringResource(R.string.overview)) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text(stringResource(R.string.product_stats)) }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTabIndex == 0) {
                        AnalyticsOverviewTab(uiState = uiState, viewModel = viewModel)
                    } else {
                        ProductStatsTab(uiState = uiState, viewModel = viewModel) { showTrendDialog = it }
                    }
                }
            }
        }
    }

    if (showTrendDialog != null) {
        PriceTrendDialog(
            product = showTrendDialog!!,
            viewModel = viewModel,
            onDismiss = { showTrendDialog = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsOverviewTab(
    uiState: com.otakeeesen.byebyemoneylist.ui.viewmodel.AnalyticsUiState,
    viewModel: com.otakeeesen.byebyemoneylist.ui.viewmodel.AnalyticsViewModel
) {
    val categoryColors = remember(uiState.allCategories) {
        uiState.allCategories.associate { it.id to it.color }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            SegmentedButton(
                selected = uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING,
                onClick = { viewModel.setOverviewMode(com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(stringResource(R.string.spending))
            }
            SegmentedButton(
                selected = uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.QUANTITY,
                onClick = { viewModel.setOverviewMode(com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.QUANTITY) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(stringResource(R.string.quantity))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.spending_by_category), style = MaterialTheme.typography.titleMedium)
        
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            val isSplit = uiState.currentRootCategoryId != null && 
                (if (uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING) uiState.subCategorySpending.isNotEmpty() else uiState.subCategoryQuantity.isNotEmpty())
            
            val rootData = if (uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING) uiState.rootCategorySpending else uiState.rootCategoryQuantity
            val subData = if (uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING) uiState.subCategorySpending else uiState.subCategoryQuantity

            Row(modifier = Modifier.fillMaxSize()) {
                SpendingPieChart(
                    pieData = createPieData(rootData, uiState.categoryNames, stringResource(R.string.categories), categoryColors),
                    onSliceClick = { id -> 
                        if (id == -1L) viewModel.setRootCategory(null)
                        else viewModel.setRootCategory(id)
                    },
                    modifier = Modifier.weight(if (isSplit) 0.5f else 1f),
                    showLegend = !isSplit,
                    centerLabel = if (isSplit) "Root" else ""
                )
                
                if (isSplit) {
                    SpendingPieChart(
                        pieData = createPieData(subData, uiState.categoryNames, stringResource(R.string.subcategories), categoryColors),
                        onSliceClick = { },
                        modifier = Modifier.weight(0.5f),
                        showLegend = false,
                        centerLabel = "Sub"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.store_breakdown), style = MaterialTheme.typography.titleMedium)
        SpendingPieChart(
            pieData = createPieData(
                if (uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING) uiState.storeSpending else uiState.storeQuantity,
                uiState.storeNames, 
                stringResource(R.string.stores)
            ),
            onSliceClick = { },
            modifier = Modifier.fillMaxWidth().height(250.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.shopping_list_breakdown), style = MaterialTheme.typography.titleMedium)
        SpendingPieChart(
            pieData = createPieData(
                if (uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING) uiState.listSpending else uiState.listQuantity,
                uiState.listNames, 
                stringResource(R.string.nav_shopping)
            ),
            onSliceClick = { },
            modifier = Modifier.fillMaxWidth().height(250.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductStatsTab(
    uiState: com.otakeeesen.byebyemoneylist.ui.viewmodel.AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onProductClick: (ProductStat) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.productSearchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.search_products)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            IconButton(onClick = { viewModel.toggleStatsFilterPanel() }) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.cd_toggle_filter),
                    tint = if (uiState.statsSelectedCategoryId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.showStatsFilterPanel,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            StatsFilterPanel(
                selectedCategoryId = uiState.statsSelectedCategoryId,
                onCategoryClick = { viewModel.setStatsCategory(it) },
                allCategories = uiState.allCategories
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val filteredStats = remember(uiState.productStats, uiState.productSearchQuery, uiState.statsSelectedCategoryId, uiState.allCategories) {
            val descendantIds = uiState.statsSelectedCategoryId?.let { parentId ->
                getAllDescendantIds(parentId, uiState.allCategories) + parentId
            }

            uiState.productStats.filter { stat ->
                val matchesSearch = stat.name.contains(uiState.productSearchQuery, ignoreCase = true)
                val matchesCategory = descendantIds == null || stat.categoryId in descendantIds
                matchesSearch && matchesCategory
            }.sortedByDescending { it.totalSpent }
        }

        val totalProducts = filteredStats.size
        val totalSum = filteredStats.sumOf { it.totalSpent }

        ProductSummaryCard(totalProducts = totalProducts, totalSum = totalSum)

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(filteredStats) { stat ->
                ProductStatItem(stat) { onProductClick(stat) }
            }
        }
    }
}

@Composable
fun ProductSummaryCard(totalProducts: Int, totalSum: Double) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.products),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = totalProducts.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.total_spent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(totalSum, context),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsFilterPanel(
    selectedCategoryId: Long?,
    onCategoryClick: (Long?) -> Unit,
    allCategories: List<com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (allCategories.isNotEmpty()) {
            Text(stringResource(R.string.categories), style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onCategoryClick(null) },
                        label = { Text(stringResource(R.string.all)) }
                    )
                }
                items(allCategories, key = { it.id }) { category ->
                    val isSelected = category.id == selectedCategoryId
                    val categoryColor = safeParseColor(category.color)

                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategoryClick(category.id) },
                        label = { Text(category.name) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = categoryColor.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

private fun getAllDescendantIds(
    parentId: Long,
    allCategories: List<com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity>
): Set<Long> {
    val descendants = mutableSetOf<Long>()
    val children = allCategories.filter { it.parentId == parentId }
    children.forEach { child ->
        descendants.add(child.id)
        descendants.addAll(getAllDescendantIds(child.id, allCategories))
    }
    return descendants
}


@Composable
fun MonthPicker(
    selectedMonth: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous))
        }
        Text(selectedMonth, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next))
        }
    }
}

@Composable
fun MonthlyComparisonCard(currentTotal: Double, previousTotal: Double) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.total_spent), style = MaterialTheme.typography.labelMedium)
            Text(
                com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(currentTotal, context),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            val diff = currentTotal - previousTotal
            val percent = if (previousTotal > 0) (diff / previousTotal) * 100 else 0.0
            val color = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            val trend = if (diff > 0) "↑" else "↓"

            Text(
                stringResource(R.string.vs_last_month, trend, Math.abs(diff), percent),
                color = color,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ProductStatItem(stat: ProductStat, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ListItem(
        headlineContent = { Text(stat.name) },
        supportingContent = { Text(stringResource(R.string.quantity) + ": " + String.format("%.1f", stat.quantity)) },
        trailingContent = { 
            Text(
                com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(stat.totalSpent, context),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ) 
        },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun PriceTrendDialog(product: ProductStat, viewModel: AnalyticsViewModel, onDismiss: () -> Unit) {
    val prices by viewModel.getPriceHistory(product.productId).collectAsState(initial = emptyList())
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.price_trend, product.name)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                if (prices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_price_history))
                    }
                } else {
                    val sortedPrices = prices.sortedBy { it.date }
                    val entries = sortedPrices.mapIndexed { index, price ->
                        Entry(index.toFloat(), price.value.toFloat())
                    }
                    val labels = sortedPrices.map { 
                        Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
                            .format(DateTimeFormatter.ofPattern("MMM dd"))
                    }
                    val dataSet = LineDataSet(entries, stringResource(R.string.price)).apply {
                        lineWidth = 2f
                        setDrawCircles(true)
                        setDrawValues(true)
                        color = MaterialTheme.colorScheme.primary.toArgb()
                        setCircleColor(MaterialTheme.colorScheme.primary.toArgb())
                    }
                    
                    SpendingLineChart(
                        lineData = LineData(dataSet),
                        xLabels = labels,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
