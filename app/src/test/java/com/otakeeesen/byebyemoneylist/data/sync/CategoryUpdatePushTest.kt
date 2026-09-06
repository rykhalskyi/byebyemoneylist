package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryUpdatePushTest {

    private fun category(
        id: Long,
        name: String,
        color: String = "#FF6B6B",
        parentId: Long? = null,
        isIncome: Boolean = false,
        emoji: String? = null,
        serverId: String? = null
    ) = CategoryEntity(
        id = id,
        name = name,
        color = color,
        parentId = parentId,
        isIncome = isIncome,
        emoji = emoji,
        serverId = serverId
    )

    @Test
    fun `parents are ordered before children even when children come first`() {
        val parent = category(1, "Food", serverId = "c-1")
        val child = category(2, "Bread", parentId = 1, serverId = "c-2")
        val grandchild = category(3, "Sourdough", parentId = 2, serverId = "c-3")

        val ordered = orderParentsBeforeChildren(listOf(grandchild, child, parent))

        assertEquals(listOf(1L, 2L, 3L), ordered.map { it.id })
    }

    @Test
    fun `unrelated categories keep their relative order`() {
        val a = category(1, "A", serverId = "c-1")
        val b = category(2, "B", parentId = 7, serverId = "c-2")
        val c = category(3, "C", serverId = "c-3")

        val ordered = orderParentsBeforeChildren(listOf(a, b, c))

        assertEquals(listOf(1L, 2L, 3L), ordered.map { it.id })
    }

    @Test
    fun `update request normalises colour to server hex and references the server parent`() {
        val cat = category(1, "Groceries", color = "#aabbcc", parentId = 9, serverId = "c-1")

        val request = buildCategoryUpdateRequest(cat, parentServerId = "c-parent")

        assertEquals("Groceries", request.name)
        assertEquals("#AABBCC", request.color)
        assertEquals("c-parent", request.parentId)
        assertEquals(false, request.income)
    }

    @Test
    fun `update request has null parent when the category is a root`() {
        val cat = category(1, "Groceries", serverId = "c-1")

        val request = buildCategoryUpdateRequest(cat, parentServerId = null)

        assertEquals(null, request.parentId)
    }

    @Test
    fun `base advanced to the local projection after a push is in sync on the next run`() {
        // Local rename; after the PUT the base snapshot is the current local projection.
        val renamed = category(1, "Groceries", color = "#AABBCC", serverId = "c-1")
        val parentServerId = "c-parent"
        val base = SyncProjection.categoryLocal(renamed, parentServerId)

        // Server echoes the same content back (normalised server form).
        val server = NextcloudCategoryDto(
            id = "c-1", name = "Groceries", color = "#AABBCC", parentId = parentServerId, income = false
        )

        val serverJson = SyncProjection.categoryServer(server)
        assertEquals(base, serverJson)
        assertEquals(
            SyncContentState.IN_SYNC,
            SyncStateResolver.resolveState(baseJson = base, localJson = base, serverJson = serverJson)
        )
    }
}
