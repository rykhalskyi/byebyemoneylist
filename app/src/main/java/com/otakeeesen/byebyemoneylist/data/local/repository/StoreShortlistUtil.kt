package com.otakeeesen.byebyemoneylist.data.local.repository

object StoreShortlistUtil {

    fun build(
        byRecency: List<Long>,
        byFrequency: List<Long>,
        recent: Int = 5,
        frequent: Int = 3,
        cap: Int = 7
    ): List<Long> {
        val result = LinkedHashSet<Long>()
        byRecency.take(recent).forEach { result.add(it) }
        byFrequency.take(frequent).forEach { result.add(it) }
        return result.take(cap)
    }
}
