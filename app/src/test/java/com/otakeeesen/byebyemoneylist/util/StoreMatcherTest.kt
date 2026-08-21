package com.otakeeesen.byebyemoneylist.util

import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreMatcherTest {

    private val stores = listOf(
        StoreEntity(id = 1, name = "REWE City", logoPath = null, address = "Main St 1", receiptName = "REWE"),
        StoreEntity(id = 2, name = "Aldi", logoPath = null, receiptName = "ALDI SÜD"),
        StoreEntity(id = 3, name = "Lidl", logoPath = null)
    )

    @Test
    fun `exact name match returns store`() {
        assertEquals(stores[2], StoreMatcher.findBestMatch("Lidl", stores))
        assertEquals(stores[2], StoreMatcher.findBestMatch("lidl", stores))
    }

    @Test
    fun `receiptName match returns store`() {
        assertEquals(stores[0], StoreMatcher.findBestMatch("REWE", stores))
    }

    @Test
    fun `fuzzy containment match returns store`() {
        assertEquals(stores[0], StoreMatcher.findBestMatch("rewe city", stores))
    }

    @Test
    fun `legal suffix is stripped during fuzzy match`() {
        val storesWithSuffix = listOf(
            StoreEntity(id = 1, name = "Rewe Markt", logoPath = null),
            StoreEntity(id = 2, name = "Lidl", logoPath = null)
        )
        assertEquals(storesWithSuffix[0], StoreMatcher.findBestMatch("Rewe Markt GmbH", storesWithSuffix))
    }

    @Test
    fun `non-ascii name matches ascii variant`() {
        assertEquals(stores[1], StoreMatcher.findBestMatch("ALDI SÜD", stores))
    }

    @Test
    fun `no close match returns null`() {
        assertNull(StoreMatcher.findBestMatch("Tesco", stores))
    }

    @Test
    fun `blank or null name returns null`() {
        assertNull(StoreMatcher.findBestMatch(null, stores))
        assertNull(StoreMatcher.findBestMatch("   ", stores))
        assertNull(StoreMatcher.findBestMatch("", emptyList()))
    }

    @Test
    fun `empty stores returns null`() {
        assertNull(StoreMatcher.findBestMatch("Lidl", emptyList()))
    }

    @Test
    fun `normalize strips legal suffixes and punctuation`() {
        assertEquals("rewe markt", StoreMatcher.normalize("REWE Markt GmbH & Co. KG"))
        assertEquals("aldi sud", StoreMatcher.normalize("ALDI SÜD"))
    }
}
