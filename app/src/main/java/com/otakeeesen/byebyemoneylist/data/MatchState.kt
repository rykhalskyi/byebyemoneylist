package com.otakeeesen.byebyemoneylist.data

/**
 * Persisted outcome of reconciling a free-text list row against a purchase.
 * `null` (unset) means the row is still open on a New list.
 */
object MatchState {
    /** The row was matched to a bought product. */
    const val MATCHED = "MATCHED"

    /** The purchase finished without the row being bought. */
    const val NOT_BOUGHT = "NOT_BOUGHT"
}
