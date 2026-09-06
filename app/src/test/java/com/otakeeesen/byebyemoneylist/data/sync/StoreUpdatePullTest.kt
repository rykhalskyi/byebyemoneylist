package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import org.junit.Assert.assertEquals
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
    fun `local-only store fields are not part of the sync domain`() {
        val plain = SyncProjection.storeLocal(localStore(logoPath = null, address = null, receiptName = null))
        val enriched = SyncProjection.storeLocal(
            localStore(logoPath = "/logo.png", address = "Somewhere", receiptName = "REWE")
        )

        assertEquals(plain, enriched)
    }

    @Test
    fun `after a remote rename pull the local projection equals the server projection`() {
        // The remote store was renamed to "Rewe City".
        val server = NextcloudStoreDto(id = "s-1", name = "Rewe City")
        val local = localStore(name = "Rewe")

        // Pull only overwrites the shared field (name); local-only fields survive.
        val pulled = local.copy(name = server.name)

        assertEquals(SyncProjection.storeServer(server), SyncProjection.storeLocal(pulled))
        assertEquals("/local/logo.png", pulled.logoPath)
        assertEquals("Hauptstrasse 1", pulled.address)
        assertEquals("REWE", pulled.receiptName)
    }

    @Test
    fun `base advanced to the server projection after a pull is in sync on the next run`() {
        val server = NextcloudStoreDto(id = "s-1", name = "Rewe City")
        val base = SyncProjection.storeServer(server)
        val localJson = SyncProjection.storeLocal(localStore(name = server.name))

        assertEquals(
            SyncContentState.IN_SYNC,
            SyncStateResolver.resolveState(baseJson = base, localJson = localJson, serverJson = base)
        )
    }
}
