package com.otakeeesen.byebyemoneylist.ui.components.analytics

import com.otakeeesen.byebyemoneylist.R
import android.graphics.Color as AndroidColor
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import com.otakeeesen.byebyemoneylist.util.CurrencyFormatter
import com.otakeeesen.byebyemoneylist.util.safeParseColor
import kotlin.math.*


// ─────────────────────────────────────────────────────────────────────────────
// Legacy MPAndroidChart-based helpers (kept for bar / line charts + store/list pie)
// ─────────────────────────────────────────────────────────────────────────────

class PieChartValueFormatter(
    private val chart: com.github.mikephil.charting.charts.PieChart,
    private val selectedCategoryId: Long?
) : ValueFormatter() {
    override fun getFormattedValue(value: Float): String = ""
    override fun getPieLabel(value: Float, pieEntry: PieEntry): String {
        val categoryId = pieEntry.data as? Long
        return if (value >= 5f || (selectedCategoryId != null && categoryId == selectedCategoryId)) {
            "${pieEntry.label} ${value.toInt()}%"
        } else ""
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
    val incomeColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val expenseColor = MaterialTheme.colorScheme.errorContainer.toArgb()
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
                        override fun getFormattedValue(value: Float): String =
                            String.format("%.2f", value)
                    }
                }
                chart.data = BarData(dataSet)
                chart.xAxis.valueFormatter = IndexAxisValueFormatter(listOf(incomeText, expensesText))
                chart.invalidate()
            }
        )
    }
}

/** Vertical bar chart for top-N lists (spending or quantity); list names in the legend. */
@Composable
fun TopListsBarChart(
    data: List<Pair<String, Double>>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val palette = listOf(
        Color(0xFF4E9AF1), Color(0xFFFF6B6B), Color(0xFF6BCB77),
        Color(0xFFFFD166), Color(0xFFBB86FC), Color(0xFF06D6A0),
        Color(0xFFEF476F), Color(0xFF118AB2), Color(0xFFFFB703), Color(0xFF8338EC)
    )
    val cappedData = data.take(palette.size)

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                BarChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(false)
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.setDrawLabels(false)
                    xAxis.setDrawAxisLine(false)
                    xAxis.textColor = textColor
                    xAxis.granularity = 1f
                    axisLeft.setDrawGridLines(true)
                    axisLeft.textColor = textColor
                    axisLeft.axisMinimum = 0f
                    axisRight.isEnabled = false
                    legend.isEnabled = true
                    legend.textColor = textColor
                    legend.textSize = 10f
                    legend.form = Legend.LegendForm.CIRCLE
                    legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                    legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                    legend.orientation = Legend.LegendOrientation.HORIZONTAL
                    legend.setDrawInside(false)
                    legend.setWordWrapEnabled(true)
                    setFitBars(true)
                }
            },
            update = { chart ->
                val dataSets = cappedData.mapIndexed { index, (name, value) ->
                    BarDataSet(listOf(BarEntry(index.toFloat(), value.toFloat())), name).apply {
                        color = palette[index % palette.size].toArgb()
                        valueTextColor = textColor
                        valueTextSize = 11f
                        setDrawValues(true)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String =
                                formatValue(value.toDouble())
                        }
                    }
                }
                chart.data = BarData(dataSets)
                chart.invalidate()
            }
        )
    }
}

/** Legacy MPAndroidChart pie – still used for store/list breakdowns. */
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
                com.github.mikephil.charting.charts.PieChart(context).apply {
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
                    setDrawEntryLabels(false)
                    legend.apply {
                        isEnabled = showLegend
                        verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                        horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                        orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
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
                            onSliceClick(-1L)
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
                chart.data.setValueFormatter(PieChartValueFormatter(chart, selectedCategoryId))
                chart.data.setValueTextColor(textColor)
                chart.data.setValueTextSize(14f)
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
                    safeParseColor(hexColor).toArgb()
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

// ─────────────────────────────────────────────────────────────────────────────
// New custom Compose donut chart with external labels + connecting lines
// ─────────────────────────────────────────────────────────────────────────────

/** One slice entry for the custom chart. */
data class DonutSlice(
    val id: Long,
    val label: String,
    val emoji: String?,
    val value: Float,
    val color: Color
)

/**
 * Full drilldown pie-chart section:
 * - Shows root categories as a donut with external labels + connecting polylines
 * - Tapping a slice that has children animates (Crossfade) to the sub-chart
 * - A back arrow returns to the root chart
 * - A scrollable chip-legend sits below the chart
 */
@Composable
fun DrilldownPieChartSection(
    rootSlices: List<DonutSlice>,
    subSlices: List<DonutSlice>,          // non-empty when a category is drilled into
    drilledCategoryName: String?,
    centerText: String,
    onSliceClick: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 320.dp
) {
    val isDrilled = subSlices.isNotEmpty()

    Column(modifier = modifier) {
        // Header row: back button + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDrilled) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back_to_categories),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = drilledCategoryName ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Crossfade(
            targetState = isDrilled,
            animationSpec = tween(durationMillis = 350),
            label = "pie_crossfade"
        ) { drilled ->
            val slices = if (drilled) subSlices else rootSlices
            Column {
                DonutChartWithLabels(
                    slices = slices,
                    centerText = centerText,
                    onSliceClick = if (drilled) { _ -> } else onSliceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChipLegend(slices = slices)
            }
        }
    }
}

/** Donut chart drawn purely with Compose Canvas + external emoji labels. */
@Composable
fun DonutChartWithLabels(
    slices: List<DonutSlice>,
    centerText: String,
    onSliceClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline
    val context = LocalContext.current

    var selectedId by remember { mutableStateOf<Long?>(null) }
    // Keep canvasSize in a plain state so it's read reactively inside the coroutine
    val canvasSizeState = remember { mutableStateOf(IntSize.Zero) }

    val total = slices.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f

    // rememberUpdatedState ensures the lambdas inside pointerInput always use the
    // *current* slices/total/onSliceClick even if the composable recomposes with new values.
    val currentSlices by rememberUpdatedState(slices)
    val currentTotal by rememberUpdatedState(total)
    val currentOnClick by rememberUpdatedState(onSliceClick)

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 500),
        label = "sweep"
    )

    // Fixed label margin in dp so the donut is always large
    val labelMarginDp = 52.dp

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSizeState.value = it }
            // Use Unit as key so the coroutine restarts on each recomposition,
            // but currentOnClick is always fresh via rememberUpdatedState.
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val sz = canvasSizeState.value
                        if (sz == IntSize.Zero) return@detectTapGestures
                        val labelMarginPx = labelMarginDp.toPx()
                        val cx = sz.width / 2f
                        val cy = sz.height / 2f
                        val radius = ((minOf(sz.width, sz.height) / 2f - labelMarginPx) * 0.85f).coerceAtLeast(40f)
                        val holeRadius = radius * 0.50f
                        val strokeWidth = radius - holeRadius
                        val innerBound = radius - strokeWidth / 2f
                        val outerBound = radius + strokeWidth / 2f
                        val dx = tapOffset.x - cx
                        val dy = tapOffset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist in innerBound..outerBound) {
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                            if (angle < 0f) angle += 360f
                            angle %= 360f
                            var cumulative = 0f
                            for (slice in currentSlices) {
                                val sliceSweep = (slice.value / currentTotal) * 360f
                                if (angle <= cumulative + sliceSweep) {
                                    currentOnClick(slice.id)
                                    break
                                }
                                cumulative += sliceSweep
                            }
                        }
                    },
                    onTap = { tapOffset ->
                        val sz = canvasSizeState.value
                        if (sz == IntSize.Zero) return@detectTapGestures
                        val labelMarginPx = labelMarginDp.toPx()
                        val cx = sz.width / 2f
                        val cy = sz.height / 2f
                        val radius = ((minOf(sz.width, sz.height) / 2f - labelMarginPx) * 0.85f).coerceAtLeast(40f)
                        val holeRadius = radius * 0.50f
                        val strokeWidth = radius - holeRadius
                        val innerBound = radius - strokeWidth / 2f
                        val outerBound = radius + strokeWidth / 2f
                        val dx = tapOffset.x - cx
                        val dy = tapOffset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist in innerBound..outerBound) {
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                            if (angle < 0f) angle += 360f
                            angle %= 360f
                            var cumulative = 0f
                            for (slice in currentSlices) {
                                val sliceSweep = (slice.value / currentTotal) * 360f
                                if (angle <= cumulative + sliceSweep) {
                                    selectedId = if (selectedId == slice.id) null else slice.id
                                    break
                                }
                                cumulative += sliceSweep
                            }
                        } else {
                            // Tap outside the donut segments deselects
                            selectedId = null
                        }
                    }
                )
            }
    ) {
        val labelMarginPx = labelMarginDp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Make radius 15% smaller
        val radius = ((minOf(size.width, size.height) / 2f - labelMarginPx) * 0.85f).coerceAtLeast(40f)
        val holeRadius = radius * 0.50f
        val strokeWidth = radius - holeRadius

        drawDonutSlices(
            slices = slices,
            total = total,
            cx = cx, cy = cy,
            radius = radius,
            strokeWidth = strokeWidth,
            animProgress = animProgress,
            selectedId = selectedId
        )

        drawExternalEmojiLabels(
            slices = slices,
            total = total,
            cx = cx, cy = cy,
            outerRadius = radius,
            labelRadius = radius + labelMarginPx * 1.15f,
            lineColor = outline.copy(alpha = 0.45f),
            textMeasurer = textMeasurer,
            textColor = onSurface,
            animProgress = animProgress
        )

        // Center hole text: if a slice is selected, show its name, percentage, and total amount. Otherwise, show the default centerText.
        val activeCenterText = if (selectedId != null) {
            val selectedSlice = slices.find { it.id == selectedId }
            if (selectedSlice != null) {
                val pct = (selectedSlice.value / total * 100).toInt()
                "${selectedSlice.label}\n$pct%\n${CurrencyFormatter.format(selectedSlice.value.toDouble(), context)}"
            } else centerText
        } else {
            centerText
        }

        if (activeCenterText.isNotEmpty()) {
            // Split strictly by newline (\n) to prevent breaking multi-word category names
            val lines = activeCenterText.split("\n")
            val labelStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = onSurface)
            val valueStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onSurface)
            val measurements = lines.mapIndexed { index, line ->
                textMeasurer.measure(line, if (index == 0) labelStyle else valueStyle)
            }
            val lineSpacing = 2f
            val totalH = measurements.sumOf { it.size.height.toDouble() }.toFloat() + lineSpacing * (lines.size - 1)
            var y = cy - totalH / 2f
            lines.forEachIndexed { index, line ->
                val m = measurements[index]
                drawText(
                    textMeasurer = textMeasurer,
                    text = line,
                    topLeft = Offset(cx - m.size.width / 2f, y),
                    style = if (index == 0) labelStyle else valueStyle
                )
                y += m.size.height + lineSpacing
            }
        }
    }
}

private fun DrawScope.drawDonutSlices(
    slices: List<DonutSlice>,
    total: Float,
    cx: Float,
    cy: Float,
    radius: Float,
    strokeWidth: Float,
    animProgress: Float,
    selectedId: Long?
) {
    var startAngle = -90f
    val gap = 1.5f
    for (slice in slices) {
        val sweepAngle = (slice.value / total) * 360f * animProgress - gap
        val isSelected = slice.id == selectedId
        val outerR = if (isSelected) radius + strokeWidth * 0.07f else radius
        val sw = if (isSelected) strokeWidth * 1.06f else strokeWidth

        drawArc(
            color = slice.color,
            startAngle = startAngle + gap / 2f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(cx - outerR, cy - outerR),
            size = Size(outerR * 2, outerR * 2),
            style = Stroke(width = sw, cap = StrokeCap.Butt)
        )
        startAngle += sweepAngle + gap
    }
}

/**
 * Draws emoji-only labels around the donut with connecting lines.
 * Only draws if the slice has an emoji.
 */
private fun DrawScope.drawExternalEmojiLabels(
    slices: List<DonutSlice>,
    total: Float,
    cx: Float,
    cy: Float,
    outerRadius: Float,
    labelRadius: Float,
    lineColor: Color,
    textMeasurer: TextMeasurer,
    textColor: Color,
    animProgress: Float
) {
    var startAngle = -90f
    val minPct = 0.04f // skip tiny slices

    for (slice in slices) {
        val sweepAngle = (slice.value / total) * 360f * animProgress
        val pct = slice.value / total

        // Draw ONLY if it has an emoji AND meets the minimum percentage threshold
        if (slice.emoji != null && pct >= minPct) {
            val midAngle = startAngle + sweepAngle / 2f
            val midRad = Math.toRadians(midAngle.toDouble())

            // Anchor on the outer rim of the donut stroke
            val anchorX = cx + outerRadius * cos(midRad).toFloat()
            val anchorY = cy + outerRadius * sin(midRad).toFloat()

            // Label position
            val lx = cx + labelRadius * cos(midRad).toFloat()
            val ly = cy + labelRadius * sin(midRad).toFloat()

            // Connecting line: anchor → label center
            drawLine(
                color = slice.color.copy(alpha = 0.6f),
                start = Offset(anchorX, anchorY),
                end = Offset(lx, ly),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Dot at anchor
            drawCircle(
                color = slice.color,
                radius = 2.2.dp.toPx(),
                center = Offset(anchorX, anchorY)
            )

            val labelChar = slice.emoji
            val emojiStyle = TextStyle(
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Unspecified
            )

            val measured = textMeasurer.measure(labelChar, emojiStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = labelChar,
                topLeft = Offset(lx - measured.size.width / 2f, ly - measured.size.height / 2f),
                style = emojiStyle
            )
        }

        startAngle += sweepAngle
    }
}

/** Horizontally scrollable chip legend. */
@Composable
fun ChipLegend(slices: List<DonutSlice>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (slice in slices) {
            val pct = (slice.value / slices.sumOf { it.value.toDouble() }.toFloat() * 100).toInt()
            Surface(
                shape = RoundedCornerShape(50),
                color = slice.color.copy(alpha = 0.18f),
                tonalElevation = 0.dp,
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(slice.color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (slice.emoji != null) {
                        Text(slice.emoji, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = "${slice.label} $pct%",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** Convert a raw spending map + category metadata into [DonutSlice] list. */
fun buildDonutSlices(
    spendingMap: Map<Long, Double>,
    categoryNames: Map<Long, String>,
    categoryEmojis: Map<Long, String?>,
    categoryColors: Map<Long, String>?
): List<DonutSlice> {
    val total = spendingMap.values.sumOf { maxOf(0.0, it) }.toFloat().takeIf { it > 0f } ?: return emptyList()
    return spendingMap
        .filter { it.value > 0.0 }
        .entries
        .sortedByDescending { it.value }
        .mapIndexed { index, (id, amount) ->
            val hexColor = categoryColors?.get(id)
            val color = if (hexColor != null) {
                safeParseColor(hexColor)
            } else {
                // Fallback palette
                val palette = listOf(
                    Color(0xFF4E9AF1), Color(0xFFFF6B6B), Color(0xFF6BCB77),
                    Color(0xFFFFD166), Color(0xFFBB86FC), Color(0xFF06D6A0),
                    Color(0xFFEF476F), Color(0xFF118AB2), Color(0xFFFFB703), Color(0xFF8338EC)
                )
                palette[index % palette.size]
            }
            DonutSlice(
                id = id,
                label = categoryNames[id] ?: "?",
                emoji = categoryEmojis[id],
                value = amount.toFloat(),
                color = color
            )
        }
}
