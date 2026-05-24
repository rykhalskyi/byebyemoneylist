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
     * Attempts to find the total sum from the recognized ML Kit Text.
     */
    fun extractTotal(text: Text): Double? {
        val lines = text.textBlocks.flatMap { it.lines }
            .map { it.text.lowercase().trim() }

        if (lines.isEmpty()) return null

        var bestCandidate: Double? = null
        var foundKeyword = false

        // Heuristic 1: Look for a line with a "Total" keyword and extract the number from it or the next line
        for (i in lines.indices) {
            val line = lines[i]
            if (TOTAL_KEYWORDS.any { line.contains(it) }) {
                foundKeyword = true
                
                // Check if this line contains a number
                val match = PRICE_REGEX.find(line)
                if (match != null) {
                    val value = parseDouble(match.value)
                    if (value != null && (bestCandidate == null || value > bestCandidate)) {
                        bestCandidate = value
                    }
                } else if (i + 1 < lines.size) {
                    // Check the next line if the current one only had the keyword
                    val nextLine = lines[i + 1]
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

        // Heuristic 2: If no keyword match was definitive, pick the largest number found in the entire text
        // (This is common for receipts where the total is the largest and last number)
        if (bestCandidate == null) {
            val allNumbers = PRICE_REGEX.findAll(text.text)
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
