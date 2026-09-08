package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.sync.SyncProjection.ShoppingListItemDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShoppingListProjectionTest {

    private val purchaseEpoch = 1767333600000L

    private fun localList(
        name: String = "Weekly",
        storeId: Long? = 1L,
        isFinished: Boolean = false,
        finalTotal: Double? = null,
        purchaseDate: Long? = purchaseEpoch,
        position: Int = 0,
    ) = ShoppingListEntity(
        name = name,
        createDate = purchaseEpoch,
        purchaseDate = purchaseDate,
        storeId = storeId,
        isFinished = isFinished,
        finalTotal = finalTotal,
        position = position,
    )

    private fun item(
        productServerId: String,
        quantity: Double,
        price: Double? = null,
        discount: Double? = null,
        customName: String? = null,
        position: Int = 0,
    ): ShoppingListItemDigest = SyncProjection.shoppingListItemDigest(
        productServerId = productServerId,
        quantity = quantity,
        price = price,
        discount = discount,
        customName = customName,
        position = position,
    )

    @Test
    fun `list projection is equal for identical content regardless of item order`() {
        val local = SyncProjection.shoppingListLocal(
            list = localList(),
            storeServerId = "st-1",
            categoryServerIds = listOf("c-2", "c-1", "c-2"),
            items = listOf(item("p-1", 2.0, price = 1.5), item("p-2", 1.0))
        )
        val server = SyncProjection.shoppingListServer(
            server = NextcloudListDto(
                id = "l-1",
                name = "Weekly",
                storeId = "st-1",
                categoryIds = listOf("c-1", "c-2"),
                purchaseDate = NextcloudSyncDates.formatEpochToIso(purchaseEpoch),
                position = 0,
            ),
            items = listOf(item("p-2", 1.0), item("p-1", 2.0, price = 1.5))
        )

        assertEquals(local, server)
        assertEquals(SyncProjection.hash(local), SyncProjection.hash(server))
    }

    @Test
    fun `list projection differs when name items or flags change`() {
        val base = SyncProjection.shoppingListLocal(
            list = localList(),
            storeServerId = "st-1",
            categoryServerIds = listOf("c-1"),
            items = listOf(item("p-1", 1.0))
        )

        val renamed = SyncProjection.shoppingListLocal(
            list = localList(name = "Monthly"),
            storeServerId = "st-1",
            categoryServerIds = listOf("c-1"),
            items = listOf(item("p-1", 1.0))
        )
        val finished = SyncProjection.shoppingListLocal(
            list = localList(isFinished = true),
            storeServerId = "st-1",
            categoryServerIds = listOf("c-1"),
            items = listOf(item("p-1", 1.0))
        )
        val quantityChanged = SyncProjection.shoppingListLocal(
            list = localList(),
            storeServerId = "st-1",
            categoryServerIds = listOf("c-1"),
            items = listOf(item("p-1", 2.0))
        )
        val itemAdded = SyncProjection.shoppingListLocal(
            list = localList(),
            storeServerId = "st-1",
            categoryServerIds = listOf("c-1"),
            items = listOf(item("p-1", 1.0), item("p-2", 1.0))
        )

        listOf(renamed, finished, quantityChanged, itemAdded).forEach {
            assertNotEquals(base, it)
        }
    }

    @Test
    fun `list projection normalises blank refs and ids`() {
        val plain = SyncProjection.shoppingListLocal(
            list = localList(storeId = null, purchaseDate = null),
            storeServerId = null,
            categoryServerIds = emptyList(),
            items = listOf(item("p-1", 1.0, customName = "  "))
        )
        val blanked = SyncProjection.shoppingListLocal(
            list = localList(storeId = null, purchaseDate = null),
            storeServerId = " ",
            categoryServerIds = listOf("", "   "),
            items = listOf(item("p-1", 1.0, customName = null))
        )

        assertEquals(plain, blanked)
    }

    @Test
    fun `item digest normalises custom name trimming and length`() {
        val longName = "x".repeat(300)
        val trimmed = "x".repeat(255)

        assertEquals(
            item("p-1", 1.0, customName = " Milk  "),
            item("p-1", 1.0, customName = "Milk")
        )
        assertEquals(
            item("p-1", 1.0, customName = longName),
            item("p-1", 1.0, customName = trimmed)
        )
    }
}
