package com.otakeeesen.byebyemoneylist.ui.components.scanner

import java.text.Normalizer

object NameMatcher {

    fun normalize(input: String): String {
        val lower = input.lowercase()
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val withoutDiacritics = decomposed.replace(Regex("\\p{M}+"), "")
        val withoutPunctuation = withoutDiacritics.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        return withoutPunctuation.replace(Regex("\\s+"), " ").trim()
    }

    fun jaroWinkler(s1: String, s2: String): Double {
        val a = normalize(s1)
        val b = normalize(s2)
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val len1 = a.length
        val len2 = b.length
        val matchDistance = maxOf(0, maxOf(len1, len2) / 2 - 1)

        val aMatches = BooleanArray(len1)
        val bMatches = BooleanArray(len2)

        var matches = 0
        for (i in 0 until len1) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (bMatches[j] || a[i] != b[j]) continue
                aMatches[i] = true
                bMatches[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in 0 until len1) {
            if (!aMatches[i]) continue
            while (!bMatches[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }

        val jaro = (
            matches.toDouble() / len1 +
                matches.toDouble() / len2 +
                (matches - transpositions / 2.0) / matches
            ) / 3.0

        var prefix = 0
        val maxPrefix = minOf(4, minOf(len1, len2))
        while (prefix < maxPrefix && a[prefix] == b[prefix]) prefix++

        return jaro + prefix * 0.1 * (1 - jaro)
    }

    fun <T> findBest(
        input: String,
        candidates: List<T>,
        minLength: Int = 4,
        threshold: Double = 0.87,
        margin: Double = 0.10,
        nameOf: (T) -> String
    ): T? {
        if (normalize(input).length < minLength) return null

        var best: T? = null
        var bestScore = 0.0
        var secondScore = 0.0
        for (candidate in candidates) {
            val score = jaroWinkler(input, nameOf(candidate))
            if (score > bestScore) {
                secondScore = bestScore
                bestScore = score
                best = candidate
            } else if (score > secondScore) {
                secondScore = score
            }
        }

        if (best == null) return null
        if (bestScore < threshold) return null
        if (bestScore < 1.0 && bestScore - secondScore < margin) return null
        return best
    }

    fun levenshtein(s1: String, s2: String): Int {
        val a = normalize(s1)
        val b = normalize(s2)
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + substitutionCost
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    fun <T> findBestWithinDistance(
        input: String,
        candidates: List<T>,
        maxDistance: Int = 2,
        nameOf: (T) -> String
    ): T? {
        var best: T? = null
        var bestDistance = Int.MAX_VALUE
        var secondBestDistance = Int.MAX_VALUE
        for (candidate in candidates) {
            val distance = levenshtein(input, nameOf(candidate))
            if (distance < bestDistance) {
                secondBestDistance = bestDistance
                bestDistance = distance
                best = candidate
            } else if (distance < secondBestDistance) {
                secondBestDistance = distance
            }
        }

        if (best == null || bestDistance > maxDistance) return null
        if (bestDistance == secondBestDistance) return null
        return best
    }
}
