package com.otakeeesen.byebyemoneylist.util

import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity

object ProductMatcher {

    private val UNIT_TOKENS = setOf(
        "kg", "g", "l", "ml", "cl", "liter", "stuck", "stk", "pack", "packung",
        "beutel", "tute", "flasche", "dose", "bund"
    )

    private const val CHAR_RATIO_STRONG = 0.9
    private const val CHAR_RATIO_MIN = 0.75
    private const val JACCARD_MIN = 0.6
    private const val MIN_SIGNIFICANT_TOKEN_LENGTH = 2

    private val GLUED_UNIT_REGEX = Regex("^(.*?)(\\d+)([a-z]+)$")

    fun findBestMatch(name: String?, products: List<ProductEntity>): ProductEntity? {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty() || products.isEmpty()) return null

        products.forEach { product ->
            if (product.name.equals(trimmed, ignoreCase = true)) return product
        }

        val targetNorm = normalize(trimmed)
        if (targetNorm.isEmpty()) return null
        val targetTokens = significantTokens(targetNorm)
        val targetCompact = targetNorm.replace(" ", "")

        var best: ProductEntity? = null
        var bestCharRatio = 0.0
        var bestJaccard = 0.0
        for (product in products) {
            val candidateNorm = normalize(product.name)
            if (candidateNorm.isEmpty()) continue
            val candidateCompact = candidateNorm.replace(" ", "")

            if (targetCompact.isNotEmpty() && candidateCompact.isNotEmpty() && targetCompact == candidateCompact) {
                return product
            }
            if (!passesPreFilter(targetNorm, targetTokens, targetCompact, candidateNorm, candidateCompact)) continue

            val candidateTokens = significantTokens(candidateNorm)
            val charRatio = charRatio(targetNorm, candidateNorm)
            val jaccard = tokenJaccard(targetTokens, candidateTokens)
            if (!isMatch(charRatio, jaccard)) continue

            if (charRatio > bestCharRatio || (charRatio == bestCharRatio && jaccard > bestJaccard)) {
                bestCharRatio = charRatio
                bestJaccard = jaccard
                best = product
            }
        }
        return best
    }

    fun normalize(raw: String): String {
        val decomposed = java.text.Normalizer.normalize(raw.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val tokens = decomposed
            .replace(Regex("[^a-z0-9]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .toMutableList()
        while (tokens.isNotEmpty() && stripTrailingNoise(tokens)) {
            // keep stripping
        }
        return tokens.joinToString(" ")
    }

    private fun stripTrailingNoise(tokens: MutableList<String>): Boolean {
        val last = tokens.last()
        val stripped = stripTrailingUnit(last)
        if (stripped == last) return false
        if (stripped.isEmpty()) {
            tokens.removeAt(tokens.lastIndex)
            return true
        }
        tokens[tokens.lastIndex] = stripped
        return false
    }

    private fun stripTrailingUnit(token: String): String {
        if (token in UNIT_TOKENS) return ""
        if (token.all { it.isDigit() }) return ""
        val m = GLUED_UNIT_REGEX.find(token) ?: return token
        if (m.groupValues[3] !in UNIT_TOKENS) return token
        return m.groupValues[1]
    }

    private fun significantTokens(normalized: String): List<String> {
        return normalized.split(" ").filter { it.length >= MIN_SIGNIFICANT_TOKEN_LENGTH }
    }

    private fun passesPreFilter(
        targetNorm: String,
        targetTokens: List<String>,
        targetCompact: String,
        candidateNorm: String,
        candidateCompact: String
    ): Boolean {
        if (candidateNorm.contains(targetNorm) || targetNorm.contains(candidateNorm)) return true
        if (targetCompact.length >= 4 && candidateCompact.contains(targetCompact)) return true
        if (candidateCompact.length >= 4 && targetCompact.contains(candidateCompact)) return true
        return targetTokens.any { token -> candidateNorm.split(" ").contains(token) }
    }

    private fun isMatch(charRatio: Double, jaccard: Double): Boolean {
        return charRatio >= CHAR_RATIO_STRONG ||
            (jaccard >= JACCARD_MIN && charRatio >= CHAR_RATIO_MIN)
    }

    private fun charRatio(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val maxLen = maxOf(a.length, b.length)
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    private fun tokenJaccard(a: List<String>, b: List<String>): Double {
        val setA = a.toSet()
        val setB = b.toSet()
        if (setA.isEmpty() && setB.isEmpty()) return 0.0
        val intersection = (setA intersect setB).size
        val union = (setA + setB).size
        return intersection.toDouble() / union
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
