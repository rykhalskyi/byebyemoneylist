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
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

@Composable
fun SpendingPieChart(
    pieData: PieData?,
    onSliceClick: (Long) -> Unit,
    modifier: Modifier = Modifier
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
                    holeRadius = 58f
                    transparentCircleRadius = 61f
                    setDrawCenterText(true)
                    
                    legend.apply {
                        isEnabled = true
                        verticalAlignment = Legend.LegendVerticalAlignment.TOP
                        horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                        orientation = Legend.LegendOrientation.VERTICAL
                        setDrawInside(false)
                    }

                    setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                        override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                            if (e is com.github.mikephil.charting.data.PieEntry) {
                                // Assuming we store CategoryId in the Entry data
                                val categoryId = e.data as? Long
                                categoryId?.let { onSliceClick(it) }
                            }
                        }
                        override fun onNothingSelected() {}
                    })
                }
            },
            update = { chart ->
                chart.data = pieData
                chart.setEntryLabelColor(textColor)
                chart.legend.textColor = textColor
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
