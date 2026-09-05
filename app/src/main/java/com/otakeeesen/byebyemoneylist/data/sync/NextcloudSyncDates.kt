package com.otakeeesen.byebyemoneylist.data.sync

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Conversions between the client's epoch-millis timestamps and the ISO-8601
 * (ATOM) date strings the Nextcloud server serializes.
 */
object NextcloudSyncDates {

    /** Parses an ISO-8601 string ("2026-09-05T10:00:00+00:00") to epoch millis, or null when absent/unparseable. */
    fun parseIsoToEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }

    /** Formats an epoch-millis timestamp as an ISO-8601 UTC string, or null when absent. */
    fun formatEpochToIso(millis: Long?): String? {
        return millis?.let { Instant.ofEpochMilli(it).toString() }
    }
}

/**
 * Mirror change detection: a linked list (local `serverId` set) needs to be
 * pushed when its last local modification is newer than the server's
 * `updated_at`. After every successful pull/push the client keeps
 * `lastModifiedAt` equal to the server `updated_at`, so only genuine local
 * edits make the list dirty.
 */
fun shouldPushLinkedList(localLastModifiedAt: Long, serverUpdatedAtEpochMillis: Long): Boolean {
    return localLastModifiedAt > serverUpdatedAtEpochMillis
}
