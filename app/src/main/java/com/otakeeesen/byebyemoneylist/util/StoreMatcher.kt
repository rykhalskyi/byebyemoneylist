package com.otakeeesen.byebyemoneylist.util

import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity

object StoreMatcher {

    private val LEGAL_SUFFIXES = setOf(
        "gmbh", "ag", "kgaa", "inc", "incorporated", "ltd", "limited",
        "llc", "srl", "bv", "sa", "corp", "corporation", "co", "kg", "holding"
    )

    fun findBestMatch(name: String?, stores: List<StoreEntity>, threshold: Double = 0.8): StoreEntity? {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty() || stores.isEmpty()) return null

        stores.forEach { store ->
            if (store.name.equals(trimmed, ignoreCase = true)) return store
            if (store.receiptName?.equals(trimmed, ignoreCase = true) == true) return store
        }

        val normalizedTarget = normalize(trimmed)
        if (normalizedTarget.isEmpty()) return null

        var best: StoreEntity? = null
        var bestScore = 0.0
        for (store in stores) {
            val candidates = buildList {
                add(store.name)
                if (!store.receiptName.isNullOrBlank()) add(store.receiptName)
            }
            for (candidate in candidates) {
                val score = similarity(normalizedTarget, normalize(candidate))
                if (score > bestScore) {
                    bestScore = score
                    best = store
                }
            }
        }
        return if (bestScore >= threshold) best else null
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
        while (tokens.size > 1 && tokens.last() in LEGAL_SUFFIXES) {
            tokens.removeAt(tokens.lastIndex)
        }
        return tokens.joinToString(" ")
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val maxLen = maxOf(a.length, b.length)
        val distance = levenshtein(a, b)
        val ratio = 1.0 - distance.toDouble() / maxLen
        val contains = (a.length >= 3 && b.contains(a)) || (b.length >= 3 && a.contains(b))
        return if (contains) maxOf(ratio, 0.9) else ratio
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
