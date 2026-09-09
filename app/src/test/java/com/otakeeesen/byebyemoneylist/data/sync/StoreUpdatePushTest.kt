package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreUpdatePushTest {

    private fun store(
        id: Long,
        name: String,
        address: String? = null,
        logoPath: String? = null,
        receiptName: String? = null,
        serverId: String? = "s-1"
    ) = StoreEntity(
        id = id,
        name = name,
        logoPath = logoPath,
        address = address,
        receiptName = receiptName,
        serverId = serverId
    )

    @Test
    fun `push request sends address and category ids`() {
        val local = store(1, "Rewe", address = "Hauptstrasse 1", logoPath = "/logo.png", receiptName = "REWE")

        val request = buildStoreSyncRequest(local, categoryServerIds = listOf("c-2", "c-1", "c-2"))

        assertEquals("Rewe", request.name)
        assertEquals("Hauptstrasse 1", request.address)
        assertEquals(listOf("c-1", "c-2"), request.categoryIds)
    }

    @Test
    fun `push request drops blank address and logo receiptName are not part of the payload`() {
        val local = store(1, "Rewe", address = "   ", logoPath = "/logo.png", receiptName = "REWE")

        val request = buildStoreSyncRequest(local, categoryServerIds = emptyList())

        assertNull(request.address)
        assertEquals(emptyList<String>(), request.categoryIds)
    }

    @Test
    fun `base advanced after a store push equals the server projection on the next run`() {
        val local = store(1, "Rewe", address = "Zeil 10", serverId = "s-1")
        val categoryServerIds = listOf("c-1", "c-2")
        val base = SyncProjection.storeLocal(local, categoryServerIds)

        val server = NextcloudStoreDto(
            id = "s-1", name = "Rewe", address = "Zeil 10", categoryIds = categoryServerIds
        )
        val serverJson = SyncProjection.storeServer(server)

        assertEquals(base, serverJson)
        assertEquals(
            SyncContentState.IN_SYNC,
            SyncStateResolver.resolveState(baseJson = base, localJson = base, serverJson = serverJson)
        )
    }
}
