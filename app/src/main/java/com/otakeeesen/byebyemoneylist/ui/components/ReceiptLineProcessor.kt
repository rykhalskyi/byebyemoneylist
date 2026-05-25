package com.otakeeesen.byebyemoneylist.ui.components

data class SimpleTextLine(
    val text: String,
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int,
    val centerX: Float,
    val centerY: Float,
    val angle: Double
) {
    fun height() = bottom - top
}

object ReceiptLineProcessor {

    fun groupLinesIntoRows(lines: List<SimpleTextLine>): List<List<SimpleTextLine>> {
        val rows = mutableListOf<MutableList<SimpleTextLine>>()

        for (line in lines) {
            var foundRow = false

            for (row in rows) {
                val rowRepresentative = row[0]
                if (isSameRow(line, rowRepresentative)) {
                    row.add(line)
                    foundRow = true
                    break
                }
            }

            if (!foundRow) {
                rows.add(mutableListOf(line))
            }
        }

        return rows.onEach { row ->
            row.sortBy { it.left }
        }.sortedBy { it[0].top }
    }

    private fun isSameRow(line1: SimpleTextLine, line2: SimpleTextLine): Boolean {
        val overlapTop = maxOf(line1.top, line2.top)
        val overlapBottom = minOf(line1.bottom, line2.bottom)
        val overlapHeight = overlapBottom - overlapTop
        if (overlapHeight <= 0) return false
        val minHeight = minOf(line1.height(), line2.height())
        return overlapHeight > (minHeight * 0.5)
    }
}
