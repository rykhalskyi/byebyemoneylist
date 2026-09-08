package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StoreUpdatePullTest {

    private fun localStore(
        id: Long = 1,
        name: String = "Rewe",
        serverId: String? = "s-1",
        logoPath: String? = "/local/logo.png",
        address: String? = "Hauptstrasse 1",
        receiptName: String? = "REWE"
    ) = StoreEntity(
        id = id,
        name = name,
        logoPath = logoPath,
        address = address,
        receiptName = receiptName,
        serverId = serverId
    )

    @Test
    fun `logoPath and receiptName are not part of the sync domain but address is`() {
        val plain = SyncProjection.storeLocal(localStore(logoPath = null, address = null, receiptName = null))
        val withLocalOnly = SyncProjection.storeLocal(
            localStore(logoPath = "/logo.png", address = null, receiptName = "REWE")
        )

        assertEquals(plain, withLocalOnly)

        val withAddress = SyncProjection.storeLocal(localStore(logoPath = "/logo.png", receiptName = "REWE"))
        assertNotEquals(plain, withAddress)
    }

    @Test
    fun `after a remote pull the local projection equals the server projection`() {
        // The remote store was renamed, its address changed and its category links updated.
        val server = NextcloudStoreDto(
            id = "s-1",
            name = "Rewe City",
            address = "Zeil 10",
            categoryIds = listOf("c-1", "c-2")
        )
        val local = localStore(name = "Rewe")

        // Pull only overwrites the shared fields (name / address / categories);
        // local-only fields survive.
        val pulled = local.copy(name = server.name, address = server.address)

        assertEquals(
            SyncProjection.storeServer(server),
            SyncProjection.storeLocal(pulled, categoryServerIds = listOf("c-1", "c-2"))
        )
        assertEquals("/local/logo.png", pulled.logoPath)
        assertEquals("REWE", pulled.receiptName)
    }

    @Test
    fun `base advanced to the server projection after a pull is in sync on the next run`() {
        val server = NextcloudStoreDto(id = "s-1", name = "Rewe City", categoryIds = listOf("c-1"))
        val base = SyncProjection.storeServer(server)
        val localJson = SyncProjection.storeLocal(
            localStore(name = server.name, address = null),
            categoryServerIds = listOf("c-1")
        )

        assertEquals(
            SyncContentState.IN_SYNC,
            SyncStateResolver.resolveState(baseJson = base, localJson = localJson, serverJson = base)
        )
    }
}
