package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.ui.components.ReceiptLineProcessor
import com.otakeeesen.byebyemoneylist.ui.components.SimpleTextLine
import com.otakeeesen.byebyemoneylist.ui.components.ReceiptScanner
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.tan

class ReceiptScannerTest {

    private fun createLine(
        text: String,
        top: Int,
        bottom: Int,
        left: Int,
        right: Int,
        angle: Double = 0.0
    ): SimpleTextLine {
        return SimpleTextLine(
            text = text,
            top = top,
            bottom = bottom,
            left = left,
            right = right,
            centerX = (left + right) / 2f,
            centerY = (top + bottom) / 2f,
            angle = angle
        )
    }

    @Test
    fun `test row grouping logic`() {
        // Line 1: "Total" (top: 10, bottom: 20)
        // Line 2: "10.00" (top: 11, bottom: 19) - Same row
        // Line 3: "Item" (top: 30, bottom: 40) - New row
        val lines = listOf(
            createLine("Total", 10, 20, 0, 40),
            createLine("10.00", 11, 19, 50, 90),
            createLine("Item", 30, 40, 0, 40)
        )

        val rows = ReceiptLineProcessor.groupLinesIntoRows(lines)

        assertEquals(2, rows.size)
        assertEquals(2, rows[0].size)
        assertEquals(1, rows[1].size)
        assertEquals("Total", rows[0][0].text)
        assertEquals("10.00", rows[0][1].text)
    }

    @Test
    fun `test diagonal recognition`() {
        // 30 degree tilt (approx 0.52 radians)
        val angle = 0.52 
        val t = tan(angle)
        
        // Start point (Total keyword)
        val kwX = 100f
        val kwY = 100f
        val kwLine = createLine("TOTAL", 90, 110, 80, 120, angle)
        
        // Target point (Price) - shifted right and down
        val dx = 200f
        val targetX = kwX + dx
        val targetY = kwY + (t * dx)
        
        val priceLine = createLine("25.50", (targetY - 10).toInt(), (targetY + 10).toInt(), (targetX - 20).toInt(), (targetX + 20).toInt(), angle)
        
        val allLines = listOf(kwLine, priceLine)
        val result = ReceiptScanner.extractTotalFromLines(allLines)
        
        assertEquals(25.50, result ?: 0.0, 0.01)
    }

    @Test
    fun `test fallback to largest font`() {
        val smallLine = createLine("1.99", 100, 110, 10, 50) // height 10
        val bigLine = createLine("99.99", 200, 230, 10, 100) // height 30 (Bold/Large total)
        
        val allLines = listOf(smallLine, bigLine)
        val result = ReceiptScanner.extractTotalFromLines(allLines)
        
        assertEquals(99.99, result ?: 0.0, 0.01)
    }
}
