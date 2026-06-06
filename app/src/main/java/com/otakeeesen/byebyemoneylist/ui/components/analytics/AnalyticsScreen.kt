package com.otakeeesen.byebyemoneylist.ui.components.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.PieData
import com.otakeeesen.byebyemoneylist.ui.viewmodel.AnalyticsViewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.TimeRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // TODO: Map uiState.categorySpending to pieData and uiState.monthlyTrend to lineData
    val pieData: PieData? = null 
    val lineData: LineData? = null 
    val xLabels: List<String> = emptyList()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { CenterAlignedTopAppBar(title = { Text("Analytics") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TabRow(selectedTabIndex = uiState.timeRange.ordinal) {
                TimeRange.entries.forEach { range ->
                    Tab(
                        selected = uiState.timeRange == range,
                        onClick = { viewModel.setTimeRange(range) },
                        text = { Text(range.name) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.currentRootCategoryId != null) {
                Button(onClick = { viewModel.setRootCategory(null) }) {
                    Text("Back to Root")
                }
            }
            
            SpendingPieChart(
                pieData = pieData,
                onSliceClick = { viewModel.setRootCategory(it) },
                modifier = Modifier.fillMaxWidth().height(300.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SpendingLineChart(
                lineData = lineData,
                xLabels = xLabels,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}
