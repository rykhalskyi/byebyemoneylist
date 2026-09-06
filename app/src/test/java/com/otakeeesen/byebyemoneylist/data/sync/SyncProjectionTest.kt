package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyncProjectionTest {

    // ---- Store ------------------------------------------------------------------

    @Test
    fun `store projection is equal for identical names and hash is stable`() {
        val local = SyncProjection.storeLocal(StoreEntity(id = 1, name = "Rewe", logoPath = null))
        val server = SyncProjection.storeServer(NextcloudStoreDto(id = "s-1", name = "Rewe"))

        assertEquals(local, server)
        assertEquals(SyncProjection.hash(local), SyncProjection.hash(server))
    }

    @Test
    fun `store projection differs on rename and hash changes`() {
        val before = SyncProjection.storeLocal(StoreEntity(id = 1, name = "Rewe", logoPath = null))
        val after = SyncProjection.storeLocal(StoreEntity(id = 1, name = "REWE City", logoPath = null))

        assertNotEquals(before, after)
        assertNotEquals(SyncProjection.hash(before), SyncProjection.hash(after))
    }

    // ---- Category ---------------------------------------------------------------

    @Test
    fun `category projection normalises colour case across local and server`() {
        val local = SyncProjection.categoryLocal(
            category = CategoryEntity(id = 1, name = "Food", color = "#aabbcc", parentId = null),
            parentServerId = null
        )
        val server = SyncProjection.categoryServer(
            NextcloudCategoryDto(id = "c-1", name = "Food", color = "#AABBCC", income = false)
        )

        assertEquals(local, server)
    }

    @Test
    fun `category projection differs on rename recolour income emoji and parent`() {
        val base = CategoryEntity(id = 1, name = "Food", color = "#FF6B6B", isIncome = false, emoji = null)

        val renamed = SyncProjection.categoryLocal(base.copy(name = "Groceries"), null)
        val recoloured = SyncProjection.categoryLocal(base.copy(color = "#123456"), null)
        val incomeChanged = SyncProjection.categoryLocal(base.copy(isIncome = true), null)
        val emojiAdded = SyncProjection.categoryLocal(base.copy(emoji = "🛒"), null)
        val parentAdded = SyncProjection.categoryLocal(base, parentServerId = "c-parent")
        val original = SyncProjection.categoryLocal(base, null)

        val all = listOf(renamed, recoloured, incomeChanged, emojiAdded, parentAdded)
        all.forEach { assertNotEquals(original, it) }
    }

    @Test
    fun `category projection treats blank parent as null on both sides`() {
        val serverBlank = SyncProjection.categoryServer(
            NextcloudCategoryDto(id = "c-1", name = "Food", parentId = "  ")
        )
        val serverNull = SyncProjection.categoryServer(
            NextcloudCategoryDto(id = "c-1", name = "Food", parentId = null)
        )

        assertEquals(serverBlank, serverNull)
    }

    // ---- Product ----------------------------------------------------------------

    @Test
    fun `product projection treats blank and null barcode as equal`() {
        val local = SyncProjection.productLocal(
            product = ProductEntity(id = 1, name = "Milk", barcode = "   ", picturePath = null),
            categoryServerId = null,
            aliases = emptyList()
        )
        val server = SyncProjection.productServer(
            NextcloudProductDto(id = "p-1", name = "Milk", barcode = null)
        )

        assertEquals(local, server)
    }

    @Test
    fun `product projection ignores alias order and duplicates`() {
        val local = SyncProjection.productLocal(
            product = ProductEntity(id = 1, name = "Milk", barcode = "123", picturePath = null),
            categoryServerId = "c-1",
            aliases = listOf("Fresh Milk", "Farm", "Fresh Milk")
        )
        val server = SyncProjection.productServer(
            NextcloudProductDto(
                id = "p-1", name = "Milk", barcode = "123", categoryId = "c-1",
                aliases = listOf("Farm", "Fresh Milk")
            )
        )

        assertEquals(local, server)
    }

    @Test
    fun `product projection differs when aliases category or flags change`() {
        val base = ProductEntity(
            id = 1, name = "Milk", barcode = "123", picturePath = null,
            categoryId = null, isFavorite = false, isSubscription = false, isIncome = false
        )

        val noAlias = SyncProjection.productLocal(base, null, emptyList())
        val withAlias = SyncProjection.productLocal(base, null, listOf("Farm"))
        val withCategory = SyncProjection.productLocal(base, "c-9", emptyList())
        val favourited = SyncProjection.productLocal(base.copy(isFavorite = true), null, emptyList())

        assertNotEquals(noAlias, withAlias)
        assertNotEquals(noAlias, withCategory)
        assertNotEquals(noAlias, favourited)
    }

    @Test
    fun `product status is not part of the projection`() {
        val local = SyncProjection.productLocal(
            product = ProductEntity(
                id = 1, name = "Milk", barcode = "123", picturePath = null, status = "added"
            ),
            categoryServerId = null,
            aliases = emptyList()
        )
        val server = SyncProjection.productServer(
            NextcloudProductDto(id = "p-1", name = "Milk", barcode = "123", status = "confirmed")
        )

        assertEquals(local, server)
    }
}
