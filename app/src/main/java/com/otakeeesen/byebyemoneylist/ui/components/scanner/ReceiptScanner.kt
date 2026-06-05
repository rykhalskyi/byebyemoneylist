package com.otakeeesen.byebyemoneylist.ui.components.scanner

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.tan

object ReceiptScanner {

    private val TOTAL_KEYWORDS = setOf(
        // English
        "total", "grand total", "balance due", "amount due", "to pay",
        // German
        "summe", "gesamt", "betrag", "endbetrag", "zahlbetrag",
        // Ukrainian
        "сума", "разом", "усього", "до сплати",
        // Polish
        "suma", "razem", "do zapłaty", "płatność", "kwota"
    )

    private val PRICE_REGEX = Regex("""(\d+[\.,]\d{2})""")
    private val DATE_REGEX = Regex("""\d{2}[\./]\d{2}[\./]\d{2,4}""")

    /**
     * Attempts to find the total sum from the recognized ML Kit Text.
     */
    fun extractTotal(text: Text): Double? {
        val allLines = text.textBlocks.flatMap { it.lines }.map { mapToSimpleLine(it) }
        return extractTotalFromLines(allLines)
    }

    /**
     * Internal logic that can be tested without ML Kit dependency.
     */
    fun extractTotalFromLines(allLines: List<SimpleTextLine>): Double? {
        if (allLines.isEmpty()) return null

        val keywordLines = allLines.filter { line ->
            val lowerText = line.text.lowercase()
            TOTAL_KEYWORDS.any { lowerText.contains(it) }
        }

        var bestCandidate: Double? = null

        // 1. Keyword-based Search with Tilt Alignment
        for (kwLine in keywordLines) {
            val alignedLines = allLines.filter { candidate ->
                isAligned(kwLine, candidate) && candidate.centerX >= (kwLine.centerX - kwLine.height())
            }
            
            val price = findMaxPriceInLines(alignedLines)
            if (price != null && (bestCandidate == null || price > bestCandidate)) {
                bestCandidate = price
            }
        }

        // 2. Smart Fallback: Visual Hierarchy (Font Size)
        if (bestCandidate == null) {
            val priceLines = allLines.filter { 
                PRICE_REGEX.containsMatchIn(it.text) && !DATE_REGEX.containsMatchIn(it.text)
            }
            
            val sortedByVisualWeight = priceLines.sortedWith(
                compareByDescending<SimpleTextLine> { it.height() }
                .thenByDescending { parseDouble(PRICE_REGEX.find(it.text)?.value ?: "0") ?: 0.0 }
            )
            
            val likelyTotal = sortedByVisualWeight.firstOrNull()
            if (likelyTotal != null) {
                bestCandidate = parseDouble(PRICE_REGEX.find(likelyTotal.text)?.value ?: "")
            }
        }

        return bestCandidate
    }

    private fun mapToSimpleLine(line: Text.Line): SimpleTextLine {
        val rect = line.boundingBox ?: Rect(0, 0, 0, 0)
        val points = line.cornerPoints
        
        if (points == null || points.size < 4) {
            return SimpleTextLine(
                text = line.text,
                top = rect.top,
                bottom = rect.bottom,
                left = rect.left,
                right = rect.right,
                centerX = rect.centerX().toFloat(),
                centerY = rect.centerY().toFloat(),
                angle = 0.0
            )
        }
        
        val centerX = points.map { it.x }.average().toFloat()
        val centerY = points.map { it.y }.average().toFloat()
        
        // Calculate average angle from horizontal sides
        val angleTop = atan2((points[1].y - points[0].y).toDouble(), (points[1].x - points[0].x).toDouble())
        val angleBottom = atan2((points[2].y - points[3].y).toDouble(), (points[2].x - points[3].x).toDouble())
        val angle = (angleTop + angleBottom) / 2.0
        
        return SimpleTextLine(
            text = line.text,
            top = rect.top,
            bottom = rect.bottom,
            left = rect.left,
            right = rect.right,
            centerX = centerX,
            centerY = centerY,
            angle = angle
        )
    }

    private fun isAligned(base: SimpleTextLine, candidate: SimpleTextLine): Boolean {
        if (base === candidate) return true
        
        // y - y0 = tan(angle) * (x - x0)
        val dx = candidate.centerX - base.centerX
        val expectedY = base.centerY + tan(base.angle).toFloat() * dx
        
        // Vertical margin: 70% of line height
        val margin = base.height() * 0.7f
        return abs(candidate.centerY - expectedY) < margin
    }

    private fun findMaxPriceInLines(lines: List<SimpleTextLine>): Double? {
        return lines.mapNotNull { line ->
            PRICE_REGEX.find(line.text)?.let { parseDouble(it.value) }
        }.maxOrNull()
    }

    private fun parseDouble(value: String): Double? {
        return value.replace(',', '.').toDoubleOrNull()
    }
}
