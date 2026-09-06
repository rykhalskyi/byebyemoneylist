package com.otakeeesen.byebyemoneylist.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextcloudSyncDatesTest {

    @Test
    fun parseIsoToEpochMillis_handlesUtcZuluAndOffsets() {
        assertEquals(0L, NextcloudSyncDates.parseIsoToEpochMillis("1970-01-01T00:00:00Z"))
        assertEquals(
            1757060400000L,
            NextcloudSyncDates.parseIsoToEpochMillis("2025-09-05T08:20:00+00:00")
        )
        assertEquals(
            1757060400000L,
            NextcloudSyncDates.parseIsoToEpochMillis("2025-09-05T09:20:00+01:00")
        )
    }

    @Test
    fun parseIsoToEpochMillis_returnsNullForBlankOrGarbage() {
        assertNull(NextcloudSyncDates.parseIsoToEpochMillis(null))
        assertNull(NextcloudSyncDates.parseIsoToEpochMillis(""))
        assertNull(NextcloudSyncDates.parseIsoToEpochMillis("not-a-date"))
    }

    @Test
    fun formatEpochToIso_roundTrips() {
        val millis = 1757060400000L
        val iso = NextcloudSyncDates.formatEpochToIso(millis)
        assertEquals(millis, NextcloudSyncDates.parseIsoToEpochMillis(iso))
        assertNull(NextcloudSyncDates.formatEpochToIso(null))
    }

    @Test
    fun dirtyLinkedList_onlyWhenLocalEditIsNewer() {
        val server = 1000L
        // Equal to the server anchor -> clean (no redundant re-push).
        assertTrue(!shouldPushLinkedList(server, server))
        // A local edit after the last server contact -> dirty.
        assertTrue(shouldPushLinkedList(server + 1, server))
        // Server touched later (e.g. our own push) -> clean.
        assertTrue(!shouldPushLinkedList(server, server + 1))
    }
}
