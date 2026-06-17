package com.otakeeesen.byebyemoneylist.ui.components.analytics

import com.otakeeesen.byebyemoneylist.R
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate

class PieChartValueFormatter(
    private val chart: PieChart,
    private val selectedCategoryId: Long?
) : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        return "" // Not used for PieChart labels in this way
    }

    override fun getPieLabel(value: Float, pieEntry: PieEntry): String {
        val categoryId = pieEntry.data as? Long
        // Show label if value >= 5% OR if this category is currently selected
        return if (value >= 5f || (selectedCategoryId != null && categoryId == selectedCategoryId)) {
            "${pieEntry.label} ${value.toInt()}%"
        } else {
            ""
        }
    }
}

@Composable
fun BalanceBarChart(
    income: Double,
    expenses: Double,
    modifier: Modifier = Modifier
) {
    if (income == 0.0 && expenses == 0.0) return

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    // Improve visibility in dark mode using Container colors
    val incomeColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val expenseColor = MaterialTheme.colorScheme.errorContainer.toArgb()
    
    // Get localized strings
    val incomeText = androidx.compose.ui.res.stringResource(R.string.income)
    val expensesText = androidx.compose.ui.res.stringResource(R.string.expenses)

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                BarChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(false)
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.textColor = textColor
                    xAxis.granularity = 1f
                    
                    axisLeft.setDrawGridLines(true)
                    axisLeft.textColor = textColor
                    axisLeft.axisMinimum = 0f

                    axisRight.isEnabled = false
                    legend.textColor = textColor
                    
                    setFitBars(true)
                }
            },
            update = { chart ->
                val entries = listOf(
                    BarEntry(0f, income.toFloat()),
                    BarEntry(1f, expenses.toFloat())
                )
                val dataSet = BarDataSet(entries, "").apply {
                    colors = listOf(incomeColor, expenseColor)
                    valueTextColor = textColor
                    valueTextSize = 12f
                    setDrawValues(true)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.2f", value)
                        }
                    }
                }
                chart.data = BarData(dataSet)
                chart.xAxis.valueFormatter = IndexAxisValueFormatter(listOf(incomeText, expensesText))
                chart.invalidate()
            }
        )
    }
}

@Composable
fun SpendingPieChart(
    pieData: PieData?,
    onSliceClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
    centerLabel: String = ""
) {
    if (pieData == null || pieData.entryCount == 0) return

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val holeColor = MaterialTheme.colorScheme.surface.toArgb()
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PieChart(context).apply {
                    description.isEnabled = false
                    setUsePercentValues(true)
                    isDrawHoleEnabled = true
                    setHoleColor(holeColor)
                    setTransparentCircleColor(AndroidColor.WHITE)
                    setTransparentCircleAlpha(110)
                    holeRadius = 50f
                    transparentCircleRadius = 55f
                    setDrawCenterText(centerLabel.isNotEmpty())
                    setCenterText(centerLabel)
                    isRotationEnabled = false
                    setDrawEntryLabels(false) // Disable separate category names to avoid overlap
                    
                    legend.apply {
                        isEnabled = showLegend
                        verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                        horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                        orientation = Legend.LegendOrientation.HORIZONTAL
                        setDrawInside(false)
                        this.textColor = textColor
                    }

                    setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                        override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                            if (e is PieEntry) {
                                val categoryId = e.data as? Long
                                selectedCategoryId = categoryId
                                categoryId?.let { onSliceClick(it) }
                            }
                        }
                        override fun onNothingSelected() {
                            selectedCategoryId = null
                            onSliceClick(-1L) // Special value for clearing selection
                        }
                    })
                }
            },
            update = { chart ->
                chart.data = pieData
                chart.setCenterText(centerLabel)
                chart.setDrawCenterText(centerLabel.isNotEmpty())
                chart.legend.isEnabled = showLegend
                chart.legend.textColor = textColor
                if (pieData != null) {
                    chart.data.setValueFormatter(PieChartValueFormatter(chart, selectedCategoryId))
                    chart.data.setValueTextColor(textColor)
                    chart.data.setValueTextSize(14f)
                }
                chart.invalidate()
            }
        )
    }
}

@Composable
fun SpendingLineChart(
    lineData: LineData?,
    xLabels: List<String>,
    modifier: Modifier = Modifier
) {
    if (lineData == null || lineData.entryCount == 0) return

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.toArgb()

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(false)
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.textColor = textColor
                    
                    axisLeft.setDrawGridLines(true)
                    axisLeft.gridColor = gridColor
                    axisLeft.textColor = textColor

                    axisRight.isEnabled = false
                    legend.textColor = textColor
                }
            },
            update = { chart ->
                chart.data = lineData
                chart.xAxis.valueFormatter = IndexAxisValueFormatter(xLabels)
                chart.invalidate()
            }
        )
    }
}

fun createPieData(
    spendingMap: Map<Long, Double>,
    categoryNames: Map<Long, String>,
    label: String,
    categoryColors: Map<Long, String>? = null
): PieData {
    val entries = spendingMap.map { (id, amount) ->
        PieEntry(amount.toFloat(), categoryNames[id] ?: "Unknown", id)
    }
    val dataSet = PieDataSet(entries, label).apply {
        if (categoryColors != null) {
            colors = entries.map { entry ->
                val categoryId = entry.data as? Long
                val hexColor = categoryColors[categoryId]
                if (hexColor != null) {
                    com.otakeeesen.byebyemoneylist.util.safeParseColor(hexColor).toArgb()
                } else {
                    ColorTemplate.MATERIAL_COLORS[entries.indexOf(entry) % ColorTemplate.MATERIAL_COLORS.size]
                }
            }
        } else {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
        }
        sliceSpace = 3f
        valueTextSize = 12f
        valueTextColor = AndroidColor.WHITE
    }
    return PieData(dataSet)
}
