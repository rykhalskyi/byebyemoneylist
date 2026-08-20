package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.ui.components.scanner.NameMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {

    @Test
    fun `normalize lowercases and strips diacritics and punctuation`() {
        assertEquals("rewe city", NameMatcher.normalize(" REWE  CITY! "))
        assertEquals("cafe", NameMatcher.normalize("Café"))
        assertEquals("muller", NameMatcher.normalize("Müller"))
        assertEquals("продукти", NameMatcher.normalize("Продукти"))
    }

    @Test
    fun `jaro winkler identical strings score one`() {
        assertEquals(1.0, NameMatcher.jaroWinkler("REWE", "rewe"), 1e-9)
    }

    @Test
    fun `jaro winkler tolerates transposition`() {
        val score = NameMatcher.jaroWinkler("Aldi", "Adli")
        assertTrue(score > 0.8)
    }

    @Test
    fun `findBest returns exact match`() {
        val result = NameMatcher.findBest("REWE", listOf("Lidl", "REWE", "Aldi")) { it }
        assertEquals("REWE", result)
    }

    @Test
    fun `findBest returns close match after normalization`() {
        val result = NameMatcher.findBest("Muller", listOf("Müller", "Aldi")) { it }
        assertEquals("Müller", result)
    }

    @Test
    fun `findBest returns null when no confident match`() {
        assertNull(NameMatcher.findBest("Rewe", listOf("Aldi", "Lidl", "Netto")) { it })
    }

    @Test
    fun `findBest ignores short inputs`() {
        assertNull(NameMatcher.findBest("AB", listOf("AB", "AC")) { it })
    }

    @Test
    fun `findBest returns null when ambiguous`() {
        assertNull(NameMatcher.findBest("Billa", listOf("Billa Plus", "Billa City")) { it })
    }

    @Test
    fun `levenshtein counts single letter mistakes`() {
        assertEquals(1, NameMatcher.levenshtein("Butter", "Buter"))
        assertEquals(2, NameMatcher.levenshtein("Butter", "Bttr"))
        assertEquals(1, NameMatcher.levenshtein("Joghurt", "Jogurt"))
        assertEquals(0, NameMatcher.levenshtein("REWE", "rewe"))
    }

    @Test
    fun `normalization collapses accented variants to zero distance`() {
        assertEquals(0, NameMatcher.levenshtein("Müller", "Muller"))
    }

    @Test
    fun `findBestWithinDistance catches one letter typo`() {
        val result = NameMatcher.findBestWithinDistance("Buter", listOf("Butter", "Butterkäse", "Bier")) { it }
        assertEquals("Butter", result)
    }

    @Test
    fun `findBestWithinDistance rejects prefix lookalikes`() {
        assertNull(NameMatcher.findBestWithinDistance("Butterkäse", listOf("Butter")) { it })
    }

    @Test
    fun `findBestWithinDistance ignores extra token variants`() {
        assertNull(NameMatcher.findBestWithinDistance("Milch 3,5%", listOf("Milch", "Saft")) { it })
    }

    @Test
    fun `findBestWithinDistance returns null when tied`() {
        assertNull(NameMatcher.findBestWithinDistance("Batter", listOf("Butter", "Better")) { it })
    }

    @Test
    fun `findBestWithinDistance returns null when too far`() {
        assertNull(NameMatcher.findBestWithinDistance("Apfelschorle", listOf("Milch", "Brot")) { it })
    }
}
