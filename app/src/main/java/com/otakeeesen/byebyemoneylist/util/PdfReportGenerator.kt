package com.otakeeesen.byebyemoneylist.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.ui.viewmodel.AnalyticsUiState
import com.otakeeesen.byebyemoneylist.data.ProductStat
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595f // A4 Width in points
    private const val PAGE_HEIGHT = 842f // A4 Height in points
    private const val MARGIN = 36f // 0.5 inch margin
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    // A data holder for constructing category trees
    private data class CategoryNode(
        val id: Long,
        val name: String,
        val color: String,
        var directSpending: Double = 0.0,
        val subnodes: MutableList<CategoryNode> = mutableListOf()
    ) {
        fun totalSpending(): Double {
            return directSpending + subnodes.sumOf { it.totalSpending() }
        }
    }

    fun generate(context: Context, state: AnalyticsUiState, outputStream: OutputStream) {
        val pdfDocument = PdfDocument()
        var pageCount = 0
        var currentPage: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var currentY = MARGIN

        // Localized Month format
        val locale = Locale.getDefault()
        val monthStr = state.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

        fun drawFooter(canvas: Canvas, pageNumber: Int) {
            val footerPaint = Paint().apply {
                color = AndroidColor.GRAY
                textSize = 8f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            
            // Draw a thin border line above the footer
            canvas.drawLine(MARGIN, PAGE_HEIGHT - MARGIN - 10f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - MARGIN - 10f, Paint().apply {
                color = AndroidColor.LTGRAY
                strokeWidth = 0.5f
            })

            val dateText = context.getString(R.string.pdf_generated_on, LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", locale)))
            canvas.drawText(dateText, MARGIN, PAGE_HEIGHT - MARGIN + 2f, footerPaint)

            // Will replace total pages later or just draw page number
            val pageText = context.getString(R.string.pdf_page, pageNumber, pageNumber) // Simplified page indicator
            val textWidth = footerPaint.measureText(pageText)
            canvas.drawText(pageText, PAGE_WIDTH - MARGIN - textWidth, PAGE_HEIGHT - MARGIN + 2f, footerPaint)
        }

        fun drawHeader(canvas: Canvas) {
            val appPaint = Paint().apply {
                color = AndroidColor.parseColor("#6650a4") // Primary Purple
                textSize = 10f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val titlePaint = Paint().apply {
                color = AndroidColor.DKGRAY
                textSize = 10f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }

            canvas.drawText("ByeByeMoney", MARGIN, MARGIN - 15f, appPaint)
            val headerTitle = "${context.getString(R.string.pdf_report_title)} - $monthStr"
            val titleWidth = titlePaint.measureText(headerTitle)
            canvas.drawText(headerTitle, PAGE_WIDTH - MARGIN - titleWidth, MARGIN - 15f, titlePaint)

            // Divider Line
            canvas.drawLine(MARGIN, MARGIN - 8f, PAGE_WIDTH - MARGIN, MARGIN - 8f, Paint().apply {
                color = AndroidColor.parseColor("#6650a4")
                strokeWidth = 1f
            })
        }

        fun startNewPage() {
            currentPage?.let { page ->
                drawFooter(page.canvas, pageCount)
                pdfDocument.finishPage(page)
            }

            pageCount++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageCount).create()
            val newPage = pdfDocument.startPage(pageInfo)
            currentPage = newPage
            canvas = newPage.canvas
            currentY = MARGIN + 10f // Start below header
            
            drawHeader(newPage.canvas)
        }

        fun ensureSpace(requiredHeight: Float) {
            if (currentPage == null || currentY + requiredHeight > PAGE_HEIGHT - MARGIN - 25f) {
                startNewPage()
            }
        }

        fun drawSectionHeader(title: String) {
            ensureSpace(28f)
            val cv = canvas ?: return
            
            val paint = Paint().apply {
                color = AndroidColor.parseColor("#333333")
                textSize = 12f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            
            currentY += 8f
            cv.drawText(title, MARGIN, currentY, paint)
            currentY += 4f
            
            cv.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, Paint().apply {
                color = AndroidColor.LTGRAY
                strokeWidth = 0.8f
            })
            currentY += 8f
        }

        // Initialize first page
        startNewPage()

        // 1. Draw Report Cover Header
        val cv = canvas!!
        val titlePaint = Paint().apply {
            color = AndroidColor.parseColor("#212121")
            textSize = 20f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            color = AndroidColor.GRAY
            textSize = 12f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        cv.drawText(context.getString(R.string.pdf_report_title), MARGIN, currentY + 15f, titlePaint)
        cv.drawText(monthStr, MARGIN, currentY + 32f, subPaint)
        currentY += 48f

        // 2. Draw Key Metrics Panel
        ensureSpace(70f)
        val metricCv = canvas!!
        val bgPaint = Paint().apply {
            color = AndroidColor.parseColor("#F8F9FA")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = AndroidColor.parseColor("#E9ECEF")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        
        metricCv.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 60f, bgPaint)
        metricCv.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 60f, borderPaint)

        val colWidth = CONTENT_WIDTH / 4f
        val metricLabelPaint = Paint().apply {
            color = AndroidColor.parseColor("#6C757D")
            textSize = 8f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val metricValuePaint = Paint().apply {
            textSize = 11f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val metrics = listOf(
            Triple(context.getString(R.string.pdf_total_income), CurrencyFormatter.format(state.totalIncome, context), AndroidColor.parseColor("#2D936C")),
            Triple(context.getString(R.string.pdf_total_expenses), CurrencyFormatter.format(state.totalSpent, context), AndroidColor.parseColor("#FF6B6B")),
            Triple(
                context.getString(R.string.pdf_net_balance),
                CurrencyFormatter.format(state.totalIncome - state.totalSpent, context),
                if (state.totalIncome - state.totalSpent >= 0) AndroidColor.parseColor("#2D936C") else AndroidColor.parseColor("#FF6B6B")
            ),
            Triple(
                context.getString(R.string.pdf_savings_rate),
                if (state.totalIncome > 0) "%.1f%%".format(((state.totalIncome - state.totalSpent) / state.totalIncome) * 100) else "0.0%",
                AndroidColor.parseColor("#4D9DE0")
            )
        )

        metrics.forEachIndexed { idx, item ->
            val x = MARGIN + idx * colWidth + 10f
            metricCv.drawText(item.first, x, currentY + 20f, metricLabelPaint)
            
            metricValuePaint.color = item.third
            metricCv.drawText(item.second, x, currentY + 40f, metricValuePaint)
            
            // Draw separating vertical lines
            if (idx > 0) {
                metricCv.drawLine(MARGIN + idx * colWidth, currentY + 10f, MARGIN + idx * colWidth, currentY + 50f, borderPaint)
            }
        }
        currentY += 75f

        // 3. Draw Diagrams (Pie Chart & Bar Chart side-by-side)
        ensureSpace(140f)
        val chartCv = canvas!!
        
        // --- Custom Pie Chart (Top Expenses by Category) ---
        val rootSpendingExpenses = state.rootCategorySpending.mapValues { Math.max(0.0, it.value) }.filter { it.value > 0 }
        val categoryColors = state.allCategories.associate { it.id to it.color }
        val totalCategoryExpenses = rootSpendingExpenses.values.sum()
        
        val pieCenterX = MARGIN + 60f
        val pieCenterY = currentY + 60f
        val pieRadius = 45f
        val pieRect = RectF(pieCenterX - pieRadius, pieCenterY - pieRadius, pieCenterX + pieRadius, pieCenterY + pieRadius)
        
        if (totalCategoryExpenses > 0) {
            var startAngle = -90f
            val sortedCategorySpend = rootSpendingExpenses.toList().sortedByDescending { it.second }
            val topCategories = sortedCategorySpend.take(5)
            val otherCategorySum = sortedCategorySpend.drop(5).sumOf { it.second }
            
            val slices = topCategories.toMutableList()
            if (otherCategorySum > 0) {
                slices.add(Pair(-2L, otherCategorySum)) // ID -2L for "Others"
            }
            
            slices.forEach { (catId, amt) ->
                val sweepAngle = ((amt / totalCategoryExpenses) * 360f).toFloat()
                val hexColor = if (catId == -2L) "#999999" else (categoryColors[catId] ?: "#FF6B6B")
                
                val slicePaint = Paint().apply {
                    color = safeParseColor(hexColor).toArgb()
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                chartCv.drawArc(pieRect, startAngle, sweepAngle, true, slicePaint)
                startAngle += sweepAngle
            }
            
            // Legend X offset
            var legendY = currentY + 15f
            val legendPaint = Paint().apply {
                color = AndroidColor.DKGRAY
                textSize = 8f
                isAntiAlias = true
            }
            
            slices.forEach { (catId, amt) ->
                val hexColor = if (catId == -2L) "#999999" else (categoryColors[catId] ?: "#FF6B6B")
                val catName = when (catId) {
                    -2L -> context.getString(R.string.pdf_direct_other)
                    -1L -> context.getString(R.string.pdf_direct_other)
                    else -> state.categoryNames[catId] ?: "Unknown"
                }
                
                // Draw color dot
                chartCv.drawCircle(MARGIN + 130f, legendY - 2.5f, 3.5f, Paint().apply {
                    color = safeParseColor(hexColor).toArgb()
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })
                
                // Draw Text
                val percentage = (amt / totalCategoryExpenses) * 100
                val legendText = "%s (%.1f%%)".format(catName, percentage)
                chartCv.drawText(legendText, MARGIN + 142f, legendY, legendPaint)
                legendY += 16f
            }
        } else {
            // Draw empty circle fallback
            chartCv.drawCircle(pieCenterX, pieCenterY, pieRadius, Paint().apply {
                color = AndroidColor.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 2f
            })
            chartCv.drawText("No Expense Data", pieCenterX - 35f, pieCenterY + 3f, Paint().apply {
                color = AndroidColor.GRAY
                textSize = 9f
            })
        }
        
        // --- Custom Bar Chart (Income vs Expense) ---
        val barChartLeft = PAGE_WIDTH / 2f + 40f
        val barChartBottom = currentY + 105f
        val barChartHeight = 85f
        val barWidth = 30f
        
        val maxVal = Math.max(state.totalIncome, state.totalSpent).coerceAtLeast(1.0)
        val incomeHeight = ((state.totalIncome / maxVal) * barChartHeight).toFloat()
        val expenseHeight = ((state.totalSpent / maxVal) * barChartHeight).toFloat()
        
        // Draw baseline
        chartCv.drawLine(barChartLeft - 20f, barChartBottom, barChartLeft + 100f, barChartBottom, Paint().apply {
            color = AndroidColor.DKGRAY
            strokeWidth = 1f
        })
        
        // Draw Income Bar
        val incomePaint = Paint().apply {
            color = AndroidColor.parseColor("#2D936C")
            style = Paint.Style.FILL
        }
        chartCv.drawRect(barChartLeft, barChartBottom - incomeHeight, barChartLeft + barWidth, barChartBottom, incomePaint)
        
        // Draw Expense Bar
        val expensePaint = Paint().apply {
            color = AndroidColor.parseColor("#FF6B6B")
            style = Paint.Style.FILL
        }
        chartCv.drawRect(barChartLeft + barWidth + 25f, barChartBottom - expenseHeight, barChartLeft + 2 * barWidth + 25f, barChartBottom, expensePaint)
        
        // Text labels
        val textPaint = Paint().apply {
            color = AndroidColor.DKGRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = AndroidColor.DKGRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        
        chartCv.drawText(context.getString(R.string.pdf_total_income), barChartLeft - 3f, barChartBottom + 12f, textPaint)
        chartCv.drawText(CurrencyFormatter.format(state.totalIncome, context), barChartLeft - 3f, barChartBottom - incomeHeight - 4f, valuePaint)
        
        chartCv.drawText(context.getString(R.string.pdf_total_expenses), barChartLeft + barWidth + 20f, barChartBottom + 12f, textPaint)
        chartCv.drawText(CurrencyFormatter.format(state.totalSpent, context), barChartLeft + barWidth + 20f, barChartBottom - expenseHeight - 4f, valuePaint)
        
        currentY += 140f

        // Helper to draw clean styled table rows
        fun drawRow(
            columns: List<String>,
            widths: List<Float>,
            alignments: List<Paint.Align>,
            isHeader: Boolean = false,
            isBold: Boolean = false,
            rowBgColor: Int? = null,
            indentFirstColumn: Float = 0f,
            dotColor: Int? = null
        ) {
            var maxCellLines = 1
            val textPaints = widths.map { w ->
                TextPaint().apply {
                    textSize = 9f
                    isAntiAlias = true
                    color = if (isHeader) AndroidColor.WHITE else AndroidColor.BLACK
                    typeface = if (isBold || isHeader) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                }
            }
            
            val layouts = columns.mapIndexed { idx, text ->
                val colWidth = widths[idx] - (if (idx == 0) indentFirstColumn + (if (dotColor != null) 12f else 0f) else 0f)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(text, 0, text.length, textPaints[idx], colWidth.toInt().coerceAtLeast(1))
                        .setAlignment(when (alignments[idx]) {
                            Paint.Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
                            Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                            else -> Layout.Alignment.ALIGN_CENTER
                        })
                        .setLineSpacing(0f, 1f)
                        .setIncludePad(false)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(text, textPaints[idx], colWidth.toInt().coerceAtLeast(1), when (alignments[idx]) {
                        Paint.Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
                        Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                        else -> Layout.Alignment.ALIGN_CENTER
                    }, 1f, 0f, false)
                }
            }
            
            val rowHeight = layouts.maxOf { it.height }.toFloat() + 8f // 4f padding top/bottom
            
            ensureSpace(rowHeight)
            val cvPage = canvas!!
            
            if (rowBgColor != null) {
                val bg = Paint().apply {
                    color = rowBgColor
                    style = Paint.Style.FILL
                }
                cvPage.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + rowHeight, bg)
            } else if (!isHeader) {
                // Border line
                val border = Paint().apply {
                    color = AndroidColor.LTGRAY
                    strokeWidth = 0.5f
                    style = Paint.Style.STROKE
                }
                cvPage.drawLine(MARGIN, currentY + rowHeight, PAGE_WIDTH - MARGIN, currentY + rowHeight, border)
            }
            
            var currX = MARGIN
            columns.forEachIndexed { idx, _ ->
                val w = widths[idx]
                val layout = layouts[idx]
                
                cvPage.save()
                var textX = currX
                if (idx == 0) {
                    textX += indentFirstColumn
                    if (dotColor != null) {
                        val dot = Paint().apply {
                            color = dotColor
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        cvPage.drawCircle(textX + 5f, currentY + rowHeight / 2f, 3.5f, dot)
                        textX += 12f
                    }
                }
                
                cvPage.translate(textX, currentY + 4f)
                layout.draw(cvPage)
                cvPage.restore()
                currX += w
            }
            currentY += rowHeight
        }

        // 4. Draw Table: Top 10 Most Expensive Purchases
        drawSectionHeader(context.getString(R.string.pdf_top_expensive))
        
        val topPurchases = state.productStats
            .filter { it.totalSpent > 0 }
            .sortedByDescending { it.totalSpent }
            .take(10)
        
        val colWidthsTop = listOf(CONTENT_WIDTH * 0.45f, CONTENT_WIDTH * 0.25f, CONTENT_WIDTH * 0.12f, CONTENT_WIDTH * 0.18f)
        val colAlignTop = listOf(Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT)
        
        drawRow(
            columns = listOf(
                context.getString(R.string.pdf_col_name),
                context.getString(R.string.pdf_col_category),
                context.getString(R.string.quantity),
                context.getString(R.string.pdf_col_amount)
            ),
            widths = colWidthsTop,
            alignments = colAlignTop,
            isHeader = true,
            rowBgColor = AndroidColor.parseColor("#6650a4")
        )
        
        if (topPurchases.isEmpty()) {
            drawRow(
                columns = listOf("No purchases data available", "", "", ""),
                widths = colWidthsTop,
                alignments = colAlignTop,
                isHeader = false,
                isBold = false
            )
        } else {
            topPurchases.forEachIndexed { index, stat ->
                val catName = state.categoryNames[stat.categoryId ?: -1L] ?: context.getString(R.string.pdf_direct_other)
                val isAltRow = index % 2 == 1
                val bgColor = if (isAltRow) AndroidColor.parseColor("#F8F9FA") else null
                
                drawRow(
                    columns = listOf(
                        stat.name,
                        catName,
                        "%.1f".format(stat.quantity),
                        CurrencyFormatter.format(stat.totalSpent, context)
                    ),
                    widths = colWidthsTop,
                    alignments = colAlignTop,
                    isHeader = false,
                    rowBgColor = bgColor
                )
            }
        }
        currentY += 15f

        // 5. Draw Table: Hierarchical Category breakdown
        drawSectionHeader(context.getString(R.string.pdf_category_breakdown))
        
        // Construct Category Hierarchy Tree
        val nodes = state.allCategories.associate { cat ->
            cat.id to CategoryNode(
                id = cat.id,
                name = cat.name,
                color = cat.color
            )
        }.toMutableMap()
        
        val uncategorizedNode = CategoryNode(
            id = -1L,
            name = context.getString(R.string.pdf_direct_other),
            color = "#999999"
        )
        nodes[-1L] = uncategorizedNode
        
        state.productStats.forEach { stat ->
            if (stat.totalSpent > 0) {
                val catId = stat.categoryId ?: -1L
                val node = nodes[catId]
                if (node != null) {
                    node.directSpending += stat.totalSpent
                } else {
                    uncategorizedNode.directSpending += stat.totalSpent
                }
            }
        }
        
        val rootNodes = mutableListOf<CategoryNode>()
        state.allCategories.forEach { cat ->
            val node = nodes[cat.id] ?: return@forEach
            if (cat.isIncome) return@forEach // Expenses only
            
            if (cat.parentId != null) {
                val parentNode = nodes[cat.parentId]
                if (parentNode != null) {
                    parentNode.subnodes.add(node)
                } else {
                    rootNodes.add(node)
                }
            } else {
                rootNodes.add(node)
            }
        }
        
        if (uncategorizedNode.directSpending > 0) {
            rootNodes.add(uncategorizedNode)
        }
        
        val filteredRootNodes = rootNodes
            .filter { it.totalSpending() > 0 }
            .sortedByDescending { it.totalSpending() }
            
        val colWidthsCat = listOf(CONTENT_WIDTH * 0.60f, CONTENT_WIDTH * 0.20f, CONTENT_WIDTH * 0.20f)
        val colAlignCat = listOf(Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT)
        
        drawRow(
            columns = listOf(
                context.getString(R.string.pdf_col_category),
                context.getString(R.string.pdf_col_amount),
                context.getString(R.string.pdf_col_percentage)
            ),
            widths = colWidthsCat,
            alignments = colAlignCat,
            isHeader = true,
            rowBgColor = AndroidColor.parseColor("#6650a4")
        )
        
        val overallExpensesSum = filteredRootNodes.sumOf { it.totalSpending() }.coerceAtLeast(1.0)
        
        if (filteredRootNodes.isEmpty()) {
            drawRow(
                columns = listOf("No category expenses data available", "", ""),
                widths = colWidthsCat,
                alignments = colAlignCat,
                isHeader = false
            )
        } else {
            var rowIdx = 0
            filteredRootNodes.forEach { root ->
                val parentPercent = (root.totalSpending() / overallExpensesSum) * 100
                val parentColorArgb = safeParseColor(root.color).toArgb()
                val isAltRow = rowIdx % 2 == 1
                val rowBg = if (isAltRow) AndroidColor.parseColor("#F8F9FA") else null
                rowIdx++
                
                // Draw Parent Category summary row
                drawRow(
                    columns = listOf(
                        root.name,
                        CurrencyFormatter.format(root.totalSpending(), context),
                        "%.1f%%".format(parentPercent)
                    ),
                    widths = colWidthsCat,
                    alignments = colAlignCat,
                    isHeader = false,
                    isBold = true,
                    rowBgColor = rowBg,
                    dotColor = parentColorArgb
                )
                
                // Draw direct spending if parent has subnodes
                if (root.directSpending > 0 && root.subnodes.isNotEmpty()) {
                    val directPercent = (root.directSpending / overallExpensesSum) * 100
                    drawRow(
                        columns = listOf(
                            context.getString(R.string.pdf_direct_other),
                            CurrencyFormatter.format(root.directSpending, context),
                            "%.1f%%".format(directPercent)
                        ),
                        widths = colWidthsCat,
                        alignments = colAlignCat,
                        isHeader = false,
                        isBold = false,
                        rowBgColor = null,
                        indentFirstColumn = 15f
                    )
                }
                
                // Draw subcategories detail
                root.subnodes.filter { it.totalSpending() > 0 }.sortedByDescending { it.totalSpending() }.forEach { child ->
                    val childPercent = (child.totalSpending() / overallExpensesSum) * 100
                    val childColorArgb = safeParseColor(child.color).toArgb()
                    drawRow(
                        columns = listOf(
                            child.name,
                            CurrencyFormatter.format(child.totalSpending(), context),
                            "%.1f%%".format(childPercent)
                        ),
                        widths = colWidthsCat,
                        alignments = colAlignCat,
                        isHeader = false,
                        isBold = false,
                        rowBgColor = null,
                        indentFirstColumn = 15f,
                        dotColor = childColorArgb
                    )
                }
            }
        }
        currentY += 15f

        // 6. Draw Table: Store Breakdown
        drawSectionHeader(context.getString(R.string.pdf_store_breakdown))
        
        val storeBreakdown = state.storeSpending
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
            
        val colWidthsStore = listOf(CONTENT_WIDTH * 0.60f, CONTENT_WIDTH * 0.20f, CONTENT_WIDTH * 0.20f)
        val colAlignStore = listOf(Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT)
        
        drawRow(
            columns = listOf(
                context.getString(R.string.pdf_col_store),
                context.getString(R.string.pdf_col_amount),
                context.getString(R.string.pdf_col_percentage)
            ),
            widths = colWidthsStore,
            alignments = colAlignStore,
            isHeader = true,
            rowBgColor = AndroidColor.parseColor("#6650a4")
        )
        
        val totalStoreSpendingSum = storeBreakdown.sumOf { it.second }.coerceAtLeast(1.0)
        
        if (storeBreakdown.isEmpty()) {
            drawRow(
                columns = listOf("No stores expenses data available", "", ""),
                widths = colWidthsStore,
                alignments = colAlignStore,
                isHeader = false
            )
        } else {
            storeBreakdown.forEachIndexed { index, (storeId, amount) ->
                val storeName = state.storeNames[storeId] ?: "Unknown Store"
                val percentage = (amount / totalStoreSpendingSum) * 100
                val isAltRow = index % 2 == 1
                val bgColor = if (isAltRow) AndroidColor.parseColor("#F8F9FA") else null
                
                drawRow(
                    columns = listOf(
                        storeName,
                        CurrencyFormatter.format(amount, context),
                        "%.1f%%".format(percentage)
                    ),
                    widths = colWidthsStore,
                    alignments = colAlignStore,
                    isHeader = false,
                    rowBgColor = bgColor
                )
            }
        }
        currentY += 15f

        // 7. Draw Table: Shopping List Breakdown
        drawSectionHeader(context.getString(R.string.pdf_list_breakdown))
        
        val listBreakdown = state.listSpending
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
            
        val colWidthsList = listOf(CONTENT_WIDTH * 0.60f, CONTENT_WIDTH * 0.20f, CONTENT_WIDTH * 0.20f)
        val colAlignList = listOf(Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT)
        
        drawRow(
            columns = listOf(
                "List Name",
                context.getString(R.string.pdf_col_amount),
                context.getString(R.string.pdf_col_percentage)
            ),
            widths = colWidthsList,
            alignments = colAlignList,
            isHeader = true,
            rowBgColor = AndroidColor.parseColor("#6650a4")
        )
        
        val totalListSpendingSum = listBreakdown.sumOf { it.second }.coerceAtLeast(1.0)
        
        if (listBreakdown.isEmpty()) {
            drawRow(
                columns = listOf("No shopping lists expenses data available", "", ""),
                widths = colWidthsList,
                alignments = colAlignList,
                isHeader = false
            )
        } else {
            listBreakdown.forEachIndexed { index, (listId, amount) ->
                val listName = state.listNames[listId] ?: "Unknown List"
                val percentage = (amount / totalListSpendingSum) * 100
                val isAltRow = index % 2 == 1
                val bgColor = if (isAltRow) AndroidColor.parseColor("#F8F9FA") else null
                
                drawRow(
                    columns = listOf(
                        listName,
                        CurrencyFormatter.format(amount, context),
                        "%.1f%%".format(percentage)
                    ),
                    widths = colWidthsList,
                    alignments = colAlignList,
                    isHeader = false,
                    rowBgColor = bgColor
                )
            }
        }
        
        // Finalize document
        currentPage?.let { page ->
            drawFooter(page.canvas, pageCount)
            pdfDocument.finishPage(page)
        }
        
        // Write to stream
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }
}
