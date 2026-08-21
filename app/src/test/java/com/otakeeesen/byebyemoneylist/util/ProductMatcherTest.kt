package com.otakeeesen.byebyemoneylist.util

import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductMatcherTest {

    private fun product(id: Long, name: String) =
        ProductEntity(id = id, name = name, barcode = "", picturePath = null)

    @Test
    fun `matches name with trailing quantity`() {
        val products = listOf(product(1, "BirneConference"))
        assertEquals(1L, ProductMatcher.findBestMatch("Birne Conference 1kg", products)?.id)
    }

    @Test
    fun `matches missing space in reverse direction`() {
        val products = listOf(product(1, "Birne Conference 1kg"))
        assertEquals(1L, ProductMatcher.findBestMatch("BirneConference", products)?.id)
    }

    @Test
    fun `matches glued unit against spaced unit`() {
        val products = listOf(product(1, "BirneConference1kg"))
        assertEquals(1L, ProductMatcher.findBestMatch("Birne Conference 1kg", products)?.id)
    }

    @Test
    fun `matches spaced unit against glued unit`() {
        val products = listOf(product(1, "Birne Conference 1kg"))
        assertEquals(1L, ProductMatcher.findBestMatch("BirneConference1kg", products)?.id)
    }

    @Test
    fun `matches diacritics differences`() {
        val products = listOf(product(2, "Muller Milch"))
        assertEquals(2L, ProductMatcher.findBestMatch("Müller Milch", products)?.id)
    }

    @Test
    fun `matches punctuation differences`() {
        val products = listOf(product(4, "Coca-Cola"))
        assertEquals(4L, ProductMatcher.findBestMatch("Coca Cola", products)?.id)
    }

    @Test
    fun `matches exact name ignoring case`() {
        val products = listOf(product(5, "Bananen"))
        assertEquals(5L, ProductMatcher.findBestMatch("bananen", products)?.id)
    }

    @Test
    fun `does not match loose product to composed variant`() {
        val products = listOf(product(3, "Bananen-Chips"))
        assertNull(ProductMatcher.findBestMatch("Bananen", products))
    }

    @Test
    fun `does not match different products`() {
        val products = listOf(product(6, "Ananas"))
        assertNull(ProductMatcher.findBestMatch("Apfel", products))
    }

    @Test
    fun `does not match brand to generic product`() {
        val products = listOf(product(7, "Milka Schokolade"))
        assertNull(ProductMatcher.findBestMatch("Milka", products))
    }

    @Test
    fun `does not match with extra attribute token`() {
        val products = listOf(product(8, "Bananen"))
        assertNull(ProductMatcher.findBestMatch("Bio Bananen", products))
    }

    @Test
    fun `blank name or empty products returns null`() {
        assertNull(ProductMatcher.findBestMatch(null, listOf(product(1, "Apfel"))))
        assertNull(ProductMatcher.findBestMatch("   ", listOf(product(1, "Apfel"))))
        assertNull(ProductMatcher.findBestMatch("Apfel", emptyList()))
    }

    @Test
    fun `normalize strips trailing units and numbers`() {
        assertEquals("birne conference", ProductMatcher.normalize("Birne Conference 1kg"))
        assertEquals("birne conference", ProductMatcher.normalize("Birne Conference 1,5 kg"))
        assertEquals("bananen", ProductMatcher.normalize("Bananen 500g"))
        assertEquals("cola", ProductMatcher.normalize("Cola 0,5 l"))
        assertEquals("bananen", ProductMatcher.normalize("Bananen 2 Stück"))
    }

    @Test
    fun `normalize keeps quantity in the middle`() {
        assertEquals("bio 1kg bananen", ProductMatcher.normalize("Bio 1kg Bananen"))
    }

    @Test
    fun `normalize strips glued unit suffix`() {
        assertEquals("birneconference", ProductMatcher.normalize("BirneConference1kg"))
        assertEquals("birne conference", ProductMatcher.normalize("Birne Conference 1kg"))
        assertEquals("bananen", ProductMatcher.normalize("Bananen500g"))
    }

    @Test
    fun `normalize keeps glued non-unit suffix`() {
        assertEquals("milka 3er", ProductMatcher.normalize("Milka 3er"))
        assertEquals("rewe", ProductMatcher.normalize("Rewe"))
    }
}
