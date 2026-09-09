package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingListSyncPlanTest {

    private fun linked(id: Long, state: SyncContentState) = LinkedShoppingListState(
        local = ShoppingListEntity(id = id, name = "List $id", createDate = 0L, purchaseDate = null, storeId = null),
        server = NextcloudListDto(id = "l-$id", name = "List $id"),
        state = state,
        localJson = "local-$id",
        serverJson = "server-$id",
    )

    @Test
    fun `plan counts split linked states`() {
        val plan = ShoppingListSyncPlan(
            linked = listOf(
                linked(1, SyncContentState.IN_SYNC),
                linked(2, SyncContentState.LOCAL_CHANGED),
                linked(3, SyncContentState.LOCAL_CHANGED),
                linked(4, SyncContentState.SERVER_CHANGED),
                linked(5, SyncContentState.CONFLICT),
                linked(6, SyncContentState.CONFLICT),
            ),
            pullToCreate = listOf(NextcloudListDto(id = "l-9", name = "Remote")),
            pushToCreate = listOf(ShoppingListEntity(name = "Local only", createDate = 0L, purchaseDate = null, storeId = null)),
        )

        assertEquals(6, plan.linkedCount)
        assertEquals(1, plan.inSyncCount)
        assertEquals(2, plan.localChangedCount)
        assertEquals(1, plan.serverChangedCount)
        assertEquals(2, plan.conflictCount)
    }

    @Test
    fun `auto action maps states and never auto-resolves a conflict`() {
        assertEquals(ShoppingListLinkAction.SKIP, defaultShoppingListAction(SyncContentState.IN_SYNC))
        assertEquals(ShoppingListLinkAction.PUSH_LOCAL, defaultShoppingListAction(SyncContentState.LOCAL_CHANGED))
        assertEquals(ShoppingListLinkAction.PULL_SERVER, defaultShoppingListAction(SyncContentState.SERVER_CHANGED))
        // An unresolved conflict must never silently overwrite one side.
        assertEquals(ShoppingListLinkAction.SKIP, defaultShoppingListAction(SyncContentState.CONFLICT))
    }

    @Test
    fun `unresolved conflicts exclude the lists the user already resolved`() {
        val plan = ShoppingListSyncPlan(
            linked = listOf(
                linked(1, SyncContentState.CONFLICT),
                linked(2, SyncContentState.CONFLICT),
                linked(3, SyncContentState.CONFLICT),
                linked(4, SyncContentState.LOCAL_CHANGED),
            )
        )

        assertEquals(3, plan.unresolvedConflictCount(emptyMap()))
        // Lists 1 and 2 are resolved; list 3 remains unresolved.
        assertEquals(
            1,
            plan.unresolvedConflictCount(mapOf(1L to ShoppingListResolution.USE_LOCAL, 2L to ShoppingListResolution.USE_SERVER))
        )
        // Non-conflict lists (LOCAL_CHANGED here) never count regardless of the map.
        assertEquals(
            0,
            plan.unresolvedConflictCount(
                mapOf(
                    1L to ShoppingListResolution.USE_LOCAL,
                    2L to ShoppingListResolution.USE_SERVER,
                    3L to ShoppingListResolution.USE_LOCAL,
                    4L to ShoppingListResolution.USE_LOCAL,
                )
            )
        )
    }
}
