package com.otakeeesen.byebyemoneylist.ui.components

import com.google.mlkit.vision.text.Text
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptScannerTest {

    @Test
    fun `extractTotal finds English total with dot`() {
        val result = ReceiptScanner.extractTotalFromText(
            listOf("STORE NAME", "Items", "TOTAL 12.34", "Thank you"),
            "STORE NAME\nItems\nTOTAL 12.34\nThank you"
        )
        assertEquals(12.34, result!!, 0.001)
    }

    @Test
    fun `extractTotal finds German sum with comma`() {
        val result = ReceiptScanner.extractTotalFromText(
            listOf("SUPERMARKT", "SUMME 45,67", "BARGEZZAHLT"),
            "SUPERMARKT\nSUMME 45,67\nBARGEZZAHLT"
        )
        assertEquals(45.67, result!!, 0.001)
    }

    @Test
    fun `extractTotal finds Ukrainian total`() {
        val result = ReceiptScanner.extractTotalFromText(
            listOf("АТБ МАРКЕТ", "РАЗОМ 120,50", "ПДВ 20%"),
            "АТБ МАРКЕТ\nРАЗОМ 120,50\nПДВ 20%"
        )
        assertEquals(120.50, result!!, 0.001)
    }

    @Test
    fun `extractTotal finds Polish total`() {
        val result = ReceiptScanner.extractTotalFromText(
            listOf("BIEDRONKA", "DO ZAPŁATY 9,99", "SUMA"),
            "BIEDRONKA\nDO ZAPŁATY 9,99\nSUMA"
        )
        assertEquals(9.99, result!!, 0.001)
    }

    @Test
    fun `extractTotal finds largest number if no keywords found`() {
        val result = ReceiptScanner.extractTotalFromText(
            listOf("Unknown text", "1.20", "99.99", "0.50"),
            "Unknown text\n1.20\n99.99\n0.50"
        )
        assertEquals(99.99, result!!, 0.001)
    }

    @Test
    fun `extractTotal handles price on the next line`() {
        val result = ReceiptScanner.extractTotalFromText(
            listOf("TOTAL", "15.00"),
            "TOTAL\n15.00"
        )
        assertEquals(15.00, result!!, 0.001)
    }

    @Test
    fun `extractTotal returns null when no numbers found`() {
        val result = ReceiptScanner.extractTotalFromText(
            listOf("Hello World", "No prices here"),
            "Hello World\nNo prices here"
        )
        assertEquals(null, result)
    }
}
