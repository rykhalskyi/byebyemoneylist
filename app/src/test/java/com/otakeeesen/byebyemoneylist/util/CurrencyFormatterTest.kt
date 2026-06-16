package com.otakeeesen.byebyemoneylist.util

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale

class CurrencyFormatterTest {

    private val context: Context = mock()
    private val sharedPrefs: SharedPreferences = mock()

    @Before
    fun setup() {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPrefs)
        // Ensure Locale is consistent for tests
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `format positive price with default symbol`() {
        whenever(sharedPrefs.getString("currency_symbol", null)).thenReturn(null)
        
        val result = CurrencyFormatter.format(12.34, context)
        assertEquals("$12.34", result)
    }

    @Test
    fun `format negative price displays as positive`() {
        whenever(sharedPrefs.getString("currency_symbol", null)).thenReturn(null)
        
        val result = CurrencyFormatter.format(-12.34, context)
        assertEquals("$12.34", result)
    }

    @Test
    fun `format price with custom symbol`() {
        whenever(sharedPrefs.getString("currency_symbol", null)).thenReturn("€")
        
        val result = CurrencyFormatter.format(100.0, context)
        assertEquals("€100.00", result)
    }

    @Test
    fun `format negative price with custom symbol displays as positive`() {
        whenever(sharedPrefs.getString("currency_symbol", null)).thenReturn("€")
        
        val result = CurrencyFormatter.format(-100.0, context)
        assertEquals("€100.00", result)
    }

    @Test
    fun `format price with no symbol`() {
        whenever(sharedPrefs.getString("currency_symbol", null)).thenReturn("")
        
        val result = CurrencyFormatter.format(50.5, context)
        assertEquals("50.50", result)
    }

    @Test
    fun `format negative price with no symbol displays as positive`() {
        whenever(sharedPrefs.getString("currency_symbol", null)).thenReturn("")
        
        val result = CurrencyFormatter.format(-50.5, context)
        assertEquals("50.50", result)
    }
}
