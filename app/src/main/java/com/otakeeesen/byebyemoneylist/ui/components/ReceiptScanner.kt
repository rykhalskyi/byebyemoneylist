package com.otakeeesen.byebyemoneylist.ui.components

import com.google.mlkit.vision.text.Text

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

    /**
     * Extracts total from ML Kit Text object.
     */
    fun extractTotal(text: Text): Double? {
        val lines = text.textBlocks.flatMap { it.lines }.map { it.text }
        return extractTotalFromText(lines, text.text)
    }

    /**
     * Core logic (testable).
     */
    fun extractTotalFromText(lines: List<String>, fullText: String): Double? {
        val lowerLines = lines.map { it.lowercase().trim() }
        if (lowerLines.isEmpty()) return null

        var bestCandidate: Double? = null

        for (i in lowerLines.indices) {
            val line = lowerLines[i]
            if (TOTAL_KEYWORDS.any { line.contains(it) }) {
                val match = PRICE_REGEX.find(line)
                if (match != null) {
                    val value = parseDouble(match.value)
                    if (value != null && (bestCandidate == null || value > bestCandidate)) {
                        bestCandidate = value
                    }
                } else if (i + 1 < lowerLines.size) {
                    val nextLine = lowerLines[i + 1]
                    val nextMatch = PRICE_REGEX.find(nextLine)
                    if (nextMatch != null) {
                        val value = parseDouble(nextMatch.value)
                        if (value != null && (bestCandidate == null || value > bestCandidate)) {
                            bestCandidate = value
                        }
                    }
                }
            }
        }

        if (bestCandidate == null) {
            val allNumbers = PRICE_REGEX.findAll(fullText)
                .mapNotNull { parseDouble(it.value) }
                .toList()
            bestCandidate = allNumbers.maxOrNull()
        }

        return bestCandidate
    }

    private fun parseDouble(value: String): Double? {
        return value.replace(',', '.').toDoubleOrNull()
    }
}
