package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductUpdatePushTest {

    private fun product(
        id: Long,
        name: String,
        barcode: String = "",
        categoryId: Long? = null,
        isFavorite: Boolean = false,
        isSubscription: Boolean = false,
        isIncome: Boolean = false,
        serverId: String? = null
    ) = ProductEntity(
        id = id,
        name = name,
        barcode = barcode,
        picturePath = null,
        categoryId = categoryId,
        isFavorite = isFavorite,
        isSubscription = isSubscription,
        isIncome = isIncome,
        serverId = serverId
    )

    @Test
    fun `push request trims aliases and drops blank barcode`() {
        val local = product(1, "Milk", barcode = "  123  ", serverId = "p-1")

        val request = buildProductSyncRequest(
            local = local,
            categoryServerId = "c-1",
            aliases = listOf("  Fresh Milk  ", "  ", "Farm")
        )

        assertEquals("Milk", request.name)
        assertEquals("123", request.barcode)
        assertEquals(listOf("Fresh Milk", "Farm"), request.aliases)
        assertEquals("c-1", request.categoryId)
    }

    @Test
    fun `push request sends null barcode when local barcode is blank`() {
        val local = product(1, "Milk", barcode = "   ", serverId = "p-1")

        val request = buildProductSyncRequest(local, categoryServerId = null, aliases = emptyList())

        assertNull(request.barcode)
    }

    @Test
    fun `push request carries flags and category id`() {
        val local = product(
            1, "Netflix", categoryId = 9,
            isFavorite = true, isSubscription = true, isIncome = true, serverId = "p-1"
        )

        val request = buildProductSyncRequest(local, categoryServerId = "c-sub", aliases = emptyList())

        assertEquals(true, request.isFavorite)
        assertEquals(true, request.isSubscription)
        assertEquals(true, request.isIncome)
        assertEquals("c-sub", request.categoryId)
    }

    @Test
    fun `base advanced after a product push is in sync on the next run`() {
        val local = product(1, "Milk", barcode = "123", categoryId = 9, serverId = "p-1")
        val aliases = listOf("Fresh Milk")
        val categoryServerId = "c-1"
        val base = SyncProjection.productLocal(local, categoryServerId, aliases)

        val server = NextcloudProductDto(
            id = "p-1", name = "Milk", barcode = "123", categoryId = "c-1",
            aliases = aliases, isFavorite = false, isSubscription = false, isIncome = false
        )

        val serverJson = SyncProjection.productServer(server)
        assertEquals(base, serverJson)
        assertEquals(
            SyncContentState.IN_SYNC,
            SyncStateResolver.resolveState(baseJson = base, localJson = base, serverJson = serverJson)
        )
    }
}
