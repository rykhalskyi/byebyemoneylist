package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.PriceEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PricePushRequestsTest {

    private fun price(
        id: Long,
        productId: Long,
        storeId: Long? = null,
        value: Double = 1.0,
        date: Long = 1_000_000_000L
    ) = PriceEntity(id = id, productId = productId, storeId = storeId, value = value, date = date)

    @Test
    fun `keeps records with a resolvable product and no store`() {
        val requests = buildPricePushRequests(
            prices = listOf(price(id = 1, productId = 11)),
            productServerIdByLocalId = mapOf(11L to "p-11"),
            storeServerIdByLocalId = emptyMap()
        )

        assertEquals(1, requests.size)
        assertEquals("p-11", requests[0].productId)
        assertNull(requests[0].storeId)
        assertEquals(1.0, requests[0].value, 0.0)
        assertEquals(Instant.ofEpochMilli(1_000_000_000L).toString(), requests[0].date)
    }

    @Test
    fun `drops records whose product is not on the server yet`() {
        val requests = buildPricePushRequests(
            prices = listOf(price(id = 1, productId = 11), price(id = 2, productId = 22)),
            productServerIdByLocalId = mapOf(11L to "p-11"),
            storeServerIdByLocalId = emptyMap()
        )

        assertEquals(1, requests.size)
        assertEquals("p-11", requests[0].productId)
    }

    @Test
    fun `resolves the store id and drops records with an unsynced store`() {
        val requests = buildPricePushRequests(
            prices = listOf(
                price(id = 1, productId = 11, storeId = 1),
                price(id = 2, productId = 11, storeId = 2),
                price(id = 3, productId = 11, storeId = null)
            ),
            productServerIdByLocalId = mapOf(11L to "p-11"),
            storeServerIdByLocalId = mapOf(1L to "s-1")
        )

        assertEquals(2, requests.size)
        assertEquals("s-1", requests[0].storeId)
        assertNull(requests[1].storeId)
    }
}
