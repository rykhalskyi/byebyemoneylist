package com.otakeeesen.byebyemoneylist.data.local.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreShortlistUtilTest {

    @Test
    fun `shortlist prefers recent then frequent without duplicates`() {
        val result = StoreShortlistUtil.build(
            byRecency = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L),
            byFrequency = listOf(4L, 5L, 6L, 8L, 9L, 10L),
            recent = 5,
            frequent = 3,
            cap = 7
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), result)
    }

    @Test
    fun `shortlist respects cap`() {
        val result = StoreShortlistUtil.build(
            byRecency = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
            byFrequency = listOf(11L, 12L, 13L, 14L),
            recent = 5,
            frequent = 3,
            cap = 7
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 11L, 12L), result)
    }

    @Test
    fun `shortlist returns all stores when below cap`() {
        val result = StoreShortlistUtil.build(
            byRecency = listOf(1L, 2L, 3L),
            byFrequency = listOf(2L, 3L, 1L),
            recent = 5,
            frequent = 3,
            cap = 7
        )
        assertEquals(listOf(1L, 2L, 3L), result)
    }

    @Test
    fun `shortlist handles empty input`() {
        assertEquals(emptyList<Long>(), StoreShortlistUtil.build(emptyList(), emptyList()))
    }
}
