package com.otakeeesen.byebyemoneylist.ui.components.settings

import com.otakeeesen.byebyemoneylist.data.sync.model.SyncCandidate
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncConflict
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncGroupCounts
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatchCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncGroupCountsTest {

    private fun match(id: Int, state: SyncContentState) = SyncMatch(
        local = "local-$id",
        server = "server-$id",
        reason = "test",
        contentState = state
    )

    @Test
    fun `counts split updates and unresolved conflicts out of the matched total`() {
        val editor = SyncGroupEditorState<String, String>(
            planGenerated = true,
            matched = listOf(
                SyncMatchCandidate(match = match(1, SyncContentState.LOCAL_CHANGED), selected = true),
                SyncMatchCandidate(match = match(2, SyncContentState.SERVER_CHANGED), selected = true),
                SyncMatchCandidate(match = match(3, SyncContentState.SERVER_CHANGED), selected = false),
                SyncMatchCandidate(match = match(4, SyncContentState.IN_SYNC), selected = true)
            ),
            conflicts = listOf(
                SyncConflict(match = match(5, SyncContentState.CONFLICT), resolvedTo = null),
                SyncConflict(
                    match = match(6, SyncContentState.CONFLICT),
                    resolvedTo = SyncContentState.LOCAL_CHANGED
                )
            ),
            upload = listOf(
                SyncCandidate(item = "u-1", selected = true),
                SyncCandidate(item = "u-2", selected = true),
                SyncCandidate(item = "u-3", selected = false)
            ),
            download = listOf(
                SyncCandidate(item = "d-1", selected = true),
                SyncCandidate(item = "d-2", selected = true)
            )
        )

        assertEquals(
            SyncGroupCounts(
                matched = 4,
                upload = 2,
                download = 2,
                updates = 2,
                conflicts = 1
            ),
            editor.counts()
        )
    }

    @Test
    fun `deselecting a pending update removes it from the updates count`() {
        val editor = SyncGroupEditorState<String, String>(
            matched = listOf(
                SyncMatchCandidate(match = match(1, SyncContentState.LOCAL_CHANGED), selected = true),
                SyncMatchCandidate(match = match(2, SyncContentState.SERVER_CHANGED), selected = false)
            )
        )

        assertEquals(1, editor.counts().updates)
    }

    @Test
    fun `resolving a conflict removes it from the conflicts count`() {
        val conflict = SyncConflict(match = match(1, SyncContentState.CONFLICT), resolvedTo = null)
        val resolved = conflict.copy(resolvedTo = SyncContentState.SERVER_CHANGED)

        assertEquals(1, SyncGroupEditorState<String, String>(conflicts = listOf(conflict)).counts().conflicts)
        assertEquals(0, SyncGroupEditorState<String, String>(conflicts = listOf(resolved)).counts().conflicts)
    }

    @Test
    fun `empty editor reports all zero counts`() {
        assertEquals(SyncGroupCounts(), SyncGroupEditorState<String, String>().counts())
    }
}
