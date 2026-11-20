package com.vknxworks.adctrl.data.queries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueryCapTest {
    private fun cap(times: List<String>, max: Int) = capNewest(times, max) { it }

    @Test
    fun leavesAShortListAlone() {
        assertNull(cap(listOf("5", "4", "3"), 3))
        assertNull(cap(listOf("5", "4", "3"), 10))
        assertNull(cap(emptyList(), 3))
    }

    @Test
    fun keepsTheNewestRows() {
        assertEquals(listOf("5", "4", "3"), cap(listOf("5", "4", "3", "2", "1"), 3))
    }

    @Test
    fun keepsRowsWithTheSameTimeAsTheLastKeptRow() {
        // "Older than 3" would never bring the second and third "3" back, so they stay.
        assertEquals(listOf("5", "4", "3", "3", "3"), cap(listOf("5", "4", "3", "3", "3", "2", "1"), 3))
        // Nothing is left to cut once they are kept.
        assertNull(cap(listOf("5", "4", "3", "3"), 3))
    }

    @Test
    fun cutsNothingWithoutATimeToContinueFrom() {
        assertNull(cap(listOf("5", "4", "", "2", "1"), 3))
    }

    @Test
    fun everyRowCutIsStrictlyOlderThanTheLastKeptRow() {
        val times = (1000 downTo 1).flatMap { t -> List(1 + t % 3) { "%04d".format(t) } }
        for (max in 1..times.size) {
            val kept = cap(times, max) ?: times
            assertEquals(times.subList(0, kept.size), kept)
            val edge = kept.last()
            times.drop(kept.size).forEach { assert(it < edge) }
        }
    }
}
