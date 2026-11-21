package com.vknxworks.adctrl.data.timed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ClientPauseTest {
    private val base = listOf("||ads.example^", "# my comment", "@@||ok.example^\$important")

    @Test
    fun aPauseIsTwoLinesAtTheEnd() {
        val rules = ClientPause.add(base, "192.168.1.20", 1_000L, "21:00")
        assertEquals(base, rules.take(3))
        assertEquals("! AdCtrl pause until 1000 (21:00), removed automatically", rules[3])
        assertEquals("@@||*^\$important,client=192.168.1.20", rules[4])
        assertEquals(1_000L, ClientPause.untilFor(rules, "192.168.1.20"))
        assertNull(ClientPause.untilFor(rules, "192.168.1.21"))
        assertNull(ClientPause.untilFor(base, "192.168.1.20"))
    }

    @Test
    fun namesWithSpacesAndQuotesAreQuoted() {
        val rules = ClientPause.add(base, "Kid's tablet, old", 5L, "x")
        assertEquals("@@||*^\$important,client='Kid\\'s tablet\\, old'", rules.last())
        assertEquals(5L, ClientPause.untilFor(rules, "Kid's tablet, old"))
        assertEquals(base, ClientPause.remove(rules, "Kid's tablet, old"))
    }

    @Test
    fun pausingAgainReplacesTheEarlierPause() {
        val once = ClientPause.add(base, "phone", 10L, "a")
        val twice = ClientPause.add(once, "phone", 20L, "b")
        assertEquals(base.size + 2, twice.size)
        assertEquals(20L, ClientPause.untilFor(twice, "phone"))
    }

    @Test
    fun onlyExpiredPausesAreRemoved() {
        var rules = ClientPause.add(base, "a", 100L, "x")
        rules = ClientPause.add(rules, "b", 200L, "y")
        assertSame(rules, ClientPause.removeExpired(rules, 99L))
        val later = ClientPause.removeExpired(rules, 100L)
        assertNull(ClientPause.untilFor(later, "a"))
        assertEquals(200L, ClientPause.untilFor(later, "b"))
        assertEquals(200L, ClientPause.nextEnd(later))
        assertEquals(base, ClientPause.removeExpired(rules, 200L))
        assertNull(ClientPause.nextEnd(base))
    }

    @Test
    fun otherRulesAreNeverTouched() {
        // A comment that looks like a pause but has no rule after it, and a client allow rule without a comment.
        val odd = listOf("! AdCtrl pause until 5 (x), removed automatically", "||ads.example^", "@@||*^\$important,client=nas", "! AdCtrl pause until soon")
        assertSame(odd, ClientPause.removeExpired(odd, Long.MAX_VALUE))
        assertSame(odd, ClientPause.remove(odd, "nas"))
        assertNull(ClientPause.untilFor(odd, "nas"))
    }

    @Test
    fun scheduledPauseTimes() {
        val midnight = 1_000_000_000_000L
        val at = 21 * 60
        // Before 21:00 today: today. After: tomorrow. Exactly at: now.
        assertEquals(midnight + at * 60_000L, PauseTimes.nextStart(at, midnight + 3_600_000L, midnight))
        assertEquals(midnight + at * 60_000L + PauseTimes.DAY_MS, PauseTimes.nextStart(at, midnight + 22 * 3_600_000L, midnight))
        assertEquals(midnight + at * 60_000L, PauseTimes.nextStart(at, midnight + at * 60_000L, midnight))
        // On time: the whole pause. Late: what is left. Too late: skipped.
        assertEquals(3_600_000L, PauseTimes.remaining(1_000L, 3_600_000L, 1_000L))
        assertEquals(3_600_000L, PauseTimes.remaining(1_000L, 3_600_000L, 500L))
        assertEquals(3_000_000L, PauseTimes.remaining(1_000L, 3_600_000L, 601_000L))
        assertEquals(0L, PauseTimes.remaining(1_000L, 3_600_000L, 3_601_000L))
    }
}
