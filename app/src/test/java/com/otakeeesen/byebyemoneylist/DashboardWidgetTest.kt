package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.DashboardWidgetConfig
import com.otakeeesen.byebyemoneylist.data.DashboardWidgetType
import com.otakeeesen.byebyemoneylist.data.WidgetData
import com.otakeeesen.byebyemoneylist.data.createDashboardWidget
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets.CategoryWidget
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets.QuickPurchaseWidget
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets.SpentTodayWidget
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets.ThisMonthWidget
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardWidgetTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── createDashboardWidget factory ──────────────────────────────────────────

    @Test
    fun `createDashboardWidget returns SpentTodayWidget for SPENT_TODAY type`() {
        val config = DashboardWidgetConfig(id = "id1", type = DashboardWidgetType.SPENT_TODAY, order = 0)
        val widget = createDashboardWidget(config)
        assertTrue("Expected SpentTodayWidget", widget is SpentTodayWidget)
        assertEquals(config, widget.config)
    }

    @Test
    fun `createDashboardWidget returns ThisMonthWidget for THIS_MONTH type`() {
        val config = DashboardWidgetConfig(id = "id2", type = DashboardWidgetType.THIS_MONTH, order = 1)
        val widget = createDashboardWidget(config)
        assertTrue("Expected ThisMonthWidget", widget is ThisMonthWidget)
        assertEquals(config, widget.config)
    }

    @Test
    fun `createDashboardWidget returns QuickPurchaseWidget for QUICK_PURCHASE type`() {
        val config = DashboardWidgetConfig(id = "id3", type = DashboardWidgetType.QUICK_PURCHASE, order = 2)
        val widget = createDashboardWidget(config)
        assertTrue("Expected QuickPurchaseWidget", widget is QuickPurchaseWidget)
        assertEquals(config, widget.config)
    }

    @Test
    fun `createDashboardWidget returns CategoryWidget for CATEGORY_SPENDING type`() {
        val config = DashboardWidgetConfig(id = "id4", type = DashboardWidgetType.CATEGORY_SPENDING, order = 3, categoryId = 42L)
        val widget = createDashboardWidget(config)
        assertTrue("Expected CategoryWidget", widget is CategoryWidget)
        assertEquals(config, widget.config)
        assertEquals(42L, widget.config.categoryId)
    }

    // ── DashboardWidgetConfig serialization ────────────────────────────────────

    @Test
    fun `DashboardWidgetConfig serializes and deserializes correctly with categoryId`() {
        val config = DashboardWidgetConfig(id = "abc-123", type = DashboardWidgetType.CATEGORY_SPENDING, order = 0, categoryId = 99L)
        val encoded = json.encodeToString(config)
        val decoded: DashboardWidgetConfig = json.decodeFromString(encoded)
        assertEquals(config, decoded)
        assertEquals(99L, decoded.categoryId)
    }

    @Test
    fun `DashboardWidgetConfig serializes and deserializes with null categoryId`() {
        val config = DashboardWidgetConfig(id = "xyz-456", type = DashboardWidgetType.SPENT_TODAY, order = 1)
        val encoded = json.encodeToString(config)
        val decoded: DashboardWidgetConfig = json.decodeFromString(encoded)
        assertEquals(config, decoded)
        assertNull(decoded.categoryId)
    }

    @Test
    fun `List of DashboardWidgetConfig serializes and deserializes correctly`() {
        val configs = listOf(
            DashboardWidgetConfig(id = "a", type = DashboardWidgetType.SPENT_TODAY, order = 0),
            DashboardWidgetConfig(id = "b", type = DashboardWidgetType.THIS_MONTH, order = 1),
            DashboardWidgetConfig(id = "c", type = DashboardWidgetType.QUICK_PURCHASE, order = 2),
            DashboardWidgetConfig(id = "d", type = DashboardWidgetType.CATEGORY_SPENDING, order = 3, categoryId = 7L),
        )
        val encoded = json.encodeToString(configs)
        val decoded: List<DashboardWidgetConfig> = json.decodeFromString(encoded)
        assertEquals(configs, decoded)
        assertEquals(4, decoded.size)
    }

    @Test
    fun `DashboardWidgetConfig order field is preserved after serialization`() {
        val configs = listOf(
            DashboardWidgetConfig(id = "first", type = DashboardWidgetType.THIS_MONTH, order = 10),
            DashboardWidgetConfig(id = "second", type = DashboardWidgetType.SPENT_TODAY, order = 5),
        )
        val encoded = json.encodeToString(configs)
        val decoded: List<DashboardWidgetConfig> = json.decodeFromString(encoded)
        assertEquals(10, decoded[0].order)
        assertEquals(5, decoded[1].order)
    }

    // ── WidgetData sealed class ────────────────────────────────────────────────

    @Test
    fun `WidgetData SpentToday holds correct total`() {
        val data = WidgetData.SpentToday(total = 42.5)
        assertEquals(42.5, data.total, 0.001)
    }

    @Test
    fun `WidgetData CategorySpending holds correct fields`() {
        val data = WidgetData.CategorySpending(
            monthTotal = 100.0,
            overallTotal = 500.0,
            categoryName = "Food",
            categoryColor = 0xFFFF6B6BL
        )
        assertEquals(100.0, data.monthTotal, 0.001)
        assertEquals(500.0, data.overallTotal, 0.001)
        assertEquals("Food", data.categoryName)
        assertEquals(0xFFFF6B6BL, data.categoryColor)
    }

    @Test
    fun `WidgetData ThisMonth trend calculation positive`() {
        val lastMonth = 100.0
        val thisMonth = 120.0
        val trend = (((thisMonth - lastMonth) / lastMonth) * 100.0).toFloat()
        val data = WidgetData.ThisMonth(total = thisMonth, lastMonthTotal = lastMonth, trendPercent = trend)
        assertTrue("Trend should be positive for increase", data.trendPercent > 0f)
        assertEquals(20f, data.trendPercent, 0.01f)
    }

    @Test
    fun `WidgetData ThisMonth trend calculation negative`() {
        val lastMonth = 100.0
        val thisMonth = 80.0
        val trend = (((thisMonth - lastMonth) / lastMonth) * 100.0).toFloat()
        val data = WidgetData.ThisMonth(total = thisMonth, lastMonthTotal = lastMonth, trendPercent = trend)
        assertTrue("Trend should be negative for decrease", data.trendPercent < 0f)
        assertEquals(-20f, data.trendPercent, 0.01f)
    }

    @Test
    fun `WidgetData ThisMonth trend is zero when last month was zero`() {
        val lastMonth = 0.0
        val thisMonth = 50.0
        val trend = if (lastMonth > 0.0) (((thisMonth - lastMonth) / lastMonth) * 100.0).toFloat() else 0f
        val data = WidgetData.ThisMonth(total = thisMonth, lastMonthTotal = lastMonth, trendPercent = trend)
        assertEquals(0f, data.trendPercent, 0.001f)
    }

    @Test
    fun `WidgetData QuickPurchase is a singleton object`() {
        val d1 = WidgetData.QuickPurchase
        val d2 = WidgetData.QuickPurchase
        assertTrue(d1 === d2)
    }

    @Test
    fun `WidgetData Loading is a singleton object`() {
        val d1 = WidgetData.Loading
        val d2 = WidgetData.Loading
        assertTrue(d1 === d2)
    }

    // ── DashboardWidgetType enum ───────────────────────────────────────────────

    @Test
    fun `DashboardWidgetType has exactly 5 entries`() {
        assertEquals(5, DashboardWidgetType.entries.size)
    }

    @Test
    fun `DashboardWidgetType CATEGORY_SPENDING serializes to string`() {
        val config = DashboardWidgetConfig(id = "x", type = DashboardWidgetType.CATEGORY_SPENDING, order = 0)
        val encoded = json.encodeToString(config)
        assertTrue("Serialized JSON should contain CATEGORY_SPENDING", encoded.contains("CATEGORY_SPENDING"))
    }

    // ── Widget config reconstruction preserves all types ──────────────────────

    @Test
    fun `All widget types survive round-trip JSON serialization`() {
        DashboardWidgetType.entries.forEach { type ->
            val config = DashboardWidgetConfig(
                id = "test-${type.name}",
                type = type,
                order = type.ordinal,
                categoryId = if (type == DashboardWidgetType.CATEGORY_SPENDING) 1L else null
            )
            val encoded = json.encodeToString(config)
            val decoded: DashboardWidgetConfig = json.decodeFromString(encoded)
            assertEquals("Round-trip failed for type $type", config, decoded)
        }
    }
}
