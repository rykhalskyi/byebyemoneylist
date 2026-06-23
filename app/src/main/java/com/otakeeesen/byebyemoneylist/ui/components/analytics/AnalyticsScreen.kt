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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.otakeeesen.byebyemoneylist.data.getAllDescendantIds
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

    LaunchedEffect(uiState.isLlmEnabled) {
        if (!uiState.isLlmEnabled && selectedTabIndex == 2) {
            selectedTabIndex = 0
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                    IconButton(onClick = {
                        viewModel.toggleSearchPanel()
                        selectedTabIndex = 1
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_toggle_search),
                            tint = if (uiState.productSearchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        viewModel.toggleStatsFilterPanel()
                        selectedTabIndex = 1
                    }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.cd_toggle_filter),
                            tint = if (uiState.statsSelectedCategoryId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
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
                AnimatedVisibility(
                    visible = uiState.showSearchPanel,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    AnalyticsSearchPanel(
                        query = uiState.productSearchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) }
                    )
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

                Spacer(modifier = Modifier.height(4.dp))
                MonthPicker(
                    selectedMonth = uiState.selectedMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.getDefault())),
                    onPrevious = { viewModel.previousMonth() },
                    onNext = { viewModel.nextMonth() }
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                MonthlyComparisonCard(
                    currentTotal = uiState.totalSpent,
                    currentIncome = uiState.totalIncome,
                    previousTotal = uiState.previousMonthTotal,
                    selectedMonth = uiState.selectedMonth
                )

                Spacer(modifier = Modifier.height(8.dp))

                val maxTabIndex = if (uiState.isLlmEnabled) 2 else 1
                SecondaryScrollableTabRow(selectedTabIndex = selectedTabIndex.coerceIn(0, maxTabIndex)) {
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
                    if (uiState.isLlmEnabled) {
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = { Text(stringResource(R.string.tab_ai_assistant)) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTabIndex) {
                        0 -> AnalyticsOverviewTab(uiState = uiState, viewModel = viewModel)
                        1 -> ProductStatsTab(uiState = uiState, viewModel = viewModel) { showTrendDialog = it }
                        2 -> AgentChatTab(uiState = uiState, viewModel = viewModel)
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
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
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

        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.balance), style = MaterialTheme.typography.titleMedium)
        BalanceBarChart(
            income = uiState.totalIncome,
            expenses = uiState.totalSpent,
            modifier = Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            if (uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING) 
                stringResource(R.string.spending_by_category) 
            else stringResource(R.string.quantity), 
            style = MaterialTheme.typography.titleMedium
        )
        
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            val isSplit = uiState.currentRootCategoryId != null && 
                (if (uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING) uiState.subCategorySpending.isNotEmpty() else uiState.subCategoryQuantity.isNotEmpty())
            
            // For Pie Chart, we use only positive values (exclude discounts from slices)
            val rootData = if (uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING) 
                uiState.rootCategorySpending.mapValues { Math.max(0.0, it.value) } 
                else uiState.rootCategoryQuantity
            val subData = if (uiState.overviewMode == com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode.SPENDING) 
                uiState.subCategorySpending.mapValues { Math.max(0.0, it.value) }
                else uiState.subCategoryQuantity

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

        if (uiState.rootCategoryIncome.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.income_breakdown), style = MaterialTheme.typography.titleMedium)
            SpendingPieChart(
                pieData = createPieData(uiState.rootCategoryIncome, uiState.categoryNames, stringResource(R.string.income), categoryColors),
                onSliceClick = { },
                modifier = Modifier.fillMaxWidth().height(250.dp)
            )
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

        val filteredStats = remember(uiState.productStats, uiState.productSearchQuery, uiState.statsSelectedCategoryId, uiState.allCategories) {
            val descendantIds = uiState.statsSelectedCategoryId?.let { parentId ->
                getAllDescendantIds(parentId, uiState.allCategories) + parentId
            }

            uiState.productStats.filter { stat ->
                val matchesSearch = stat.name.contains(uiState.productSearchQuery, ignoreCase = true)
                val matchesCategory = descendantIds == null || stat.categoryId in descendantIds
                matchesSearch && matchesCategory && stat.totalSpent > 0
            }.sortedByDescending { it.totalSpent }
        }

        val totalProducts = filteredStats.size
        val totalSum = filteredStats.sumOf { it.totalSpent }
        val totalQuantity = filteredStats.sumOf { it.quantity }

        ProductSummaryCard(
            totalProducts = totalProducts,
            totalQuantity = totalQuantity,
            totalSum = totalSum,
            hasMismatch = uiState.hasProductTotalMismatch,
            listLevelTotal = uiState.totalSpent
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(filteredStats) { stat ->
                ProductStatItem(stat) { onProductClick(stat) }
            }
        }
    }
}

@Composable
fun ProductSummaryCard(totalProducts: Int, totalQuantity: Double, totalSum: Double, hasMismatch: Boolean = false, listLevelTotal: Double = 0.0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showMismatchDialog by remember { mutableStateOf(false) }
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
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = totalProducts.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "(${String.format(Locale.getDefault(), "%.1f", totalQuantity)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.total_spent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (hasMismatch) {
                        IconButton(
                            onClick = { showMismatchDialog = true },
                            modifier = Modifier.size(20.dp).padding(start = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.product_total_mismatch_title),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                Text(
                    text = com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(totalSum, context),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    if (showMismatchDialog) {
        AlertDialog(
            onDismissRequest = { showMismatchDialog = false },
            title = { Text(stringResource(R.string.product_total_mismatch_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.product_total_mismatch_message))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.list_level_total), style = MaterialTheme.typography.bodySmall)
                        Text(
                            com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(listLevelTotal, context),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.product_sum_total), style = MaterialTheme.typography.bodySmall)
                        Text(
                            com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(totalSum, context),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMismatchDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_products)) },
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
fun MonthlyComparisonCard(currentTotal: Double, currentIncome: Double, previousTotal: Double, selectedMonth: java.time.YearMonth) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val balance = currentIncome - currentTotal
    val isCurrentMonth = selectedMonth == java.time.YearMonth.now()
    var expanded by remember { mutableStateOf(true) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.balance), style = MaterialTheme.typography.titleMedium)
                    Text(
                        com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(balance, context),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(stringResource(R.string.income), style = MaterialTheme.typography.labelSmall)
                            Text(
                                com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(currentIncome, context),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.expenses), style = MaterialTheme.typography.labelSmall)
                            Text(
                                com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(currentTotal, context),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Savings Rate & Comparison logic
                    val diff = currentTotal - previousTotal
                    val percent = if (previousTotal > 0) (diff / previousTotal) * 100 else 0.0
                    val color = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val trend = if (diff > 0) "↑" else "↓"
                    val savingsRate = if (currentIncome > 0) ((currentIncome - currentTotal) / currentIncome) * 100 else 0.0

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (currentIncome > 0) {
                            Row {
                                Text(stringResource(R.string.savings_rate) + ": ", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    String.format("%.1f%%", savingsRate),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (savingsRate >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Text(
                            text = stringResource(R.string.vs_last_month, trend, Math.abs(diff), percent),
                            color = color,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (isCurrentMonth && previousTotal > 0) {
                        val remaining = previousTotal - currentTotal
                        val daysInMonth = selectedMonth.lengthOfMonth()
                        val daysLeft = daysInMonth - java.time.LocalDate.now().dayOfMonth + 1

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.remaining_budget) + ": ", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    if (remaining >= 0) com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(remaining, context)
                                    else stringResource(R.string.over_budget),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            if (daysLeft > 0 && remaining > 0) {
                                Text(
                                    com.otakeeesen.byebyemoneylist.util.CurrencyFormatter.format(remaining / daysLeft, context) + " / " + stringResource(R.string.days_left, daysLeft),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
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
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
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
