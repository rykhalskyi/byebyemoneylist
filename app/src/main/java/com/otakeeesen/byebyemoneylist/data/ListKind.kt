package com.otakeeesen.byebyemoneylist.data

/**
 * Explicit discriminator for what a `shopping_lists` row actually represents.
 *
 * - [NEED_TO_BUY] - Plain-text note for planning what to buy (no prices, store, category).
 * - [PURCHASE] - Finalized expense record with money and store/category links.
 * - [INCOME] - Finalized income record.
 * - [SUBSCRIPTION] - Finalized recurring subscription record.
 */
enum class ListKind {
    NEED_TO_BUY,
    PURCHASE,
    INCOME,
    SUBSCRIPTION;

    companion object {
        fun derive(isIncome: Boolean, isSubscription: Boolean, isFinished: Boolean): ListKind = when {
            isIncome -> INCOME
            isSubscription -> SUBSCRIPTION
            isFinished -> PURCHASE
            else -> NEED_TO_BUY
        }

        fun from(value: String?): ListKind? =
            value?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
