package com.otakeeesen.byebyemoneylist.ui.components.analytics

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate

@Composable
fun SpendingPieChart(
    pieData: PieData?,
    onSliceClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
    centerLabel: String = ""
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val holeColor = MaterialTheme.colorScheme.surface.toArgb()

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
                                categoryId?.let { onSliceClick(it) }
                            }
                        }
                        override fun onNothingSelected() {
                            onSliceClick(-1L) // Special value for clearing selection
                        }
                    })
                }
            },
            update = { chart ->
                chart.data = pieData
                chart.setEntryLabelColor(textColor)
                chart.setCenterText(centerLabel)
                chart.setDrawCenterText(centerLabel.isNotEmpty())
                chart.legend.isEnabled = showLegend
                chart.legend.textColor = textColor
                if (pieData != null) {
                    chart.data.setValueFormatter(PercentFormatter(chart))
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
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.toArgb()

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
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
    label: String
): PieData {
    val entries = spendingMap.map { (id, amount) ->
        PieEntry(amount.toFloat(), categoryNames[id] ?: "Unknown", id)
    }
    val dataSet = PieDataSet(entries, label).apply {
        colors = ColorTemplate.MATERIAL_COLORS.toList()
        sliceSpace = 3f
        valueTextSize = 12f
        valueTextColor = AndroidColor.WHITE
    }
    return PieData(dataSet)
}
