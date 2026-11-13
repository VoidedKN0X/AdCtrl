package com.vknxworks.adctrl.data.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEditTest {

    @Test
    fun parsesRules() {
        val p = RuleEdit.parse("@@||Example.com^\$important,client='Axel\\'s phone'")!!
        assertTrue(p.allow)
        assertEquals("||example.com^", p.pattern)
        assertTrue(p.important)
        assertEquals("'Axel\\'s phone'", p.client)
        assertTrue(p.otherModifiers.isEmpty())

        assertNull(RuleEdit.parse("! a comment"))
        assertNull(RuleEdit.parse("# another"))
        assertNull(RuleEdit.parse("   "))
        assertNull(RuleEdit.parse("/ads[0-9]+\\.example\\.com/"))
        assertNull(RuleEdit.parse("0.0.0.0 example.com"))
    }

    @Test
    fun splitsModifiersOutsideQuotes() {
        assertEquals(listOf("important", "client='a\\, b'"), RuleEdit.splitModifiers("important,client='a\\, b'"))
        assertEquals(listOf("dnstype=AAAA", "client=1.2.3.4"), RuleEdit.splitModifiers("dnstype=AAAA, client=1.2.3.4"))
    }

    @Test
    fun findsExactOpposites() {
        val rules = listOf(
            "! my rules",
            "@@||example.com^\$important",
            "@@||example.com^",
            "@@||other.com^\$important",
            "@@||example.com^\$important,client=192.168.1.20",
            "@@||example.com^\$dnstype=AAAA",
            "||example.com^",
        )
        // Blocking example.com for everyone: both global allow rules, not the client one or the $dnstype one.
        assertEquals(
            listOf("@@||example.com^\$important", "@@||example.com^"),
            RuleEdit.opposites(rules, "||example.com^"),
        )
        // Blocking it for one client: only that client's allow rule.
        assertEquals(
            listOf("@@||example.com^\$important,client=192.168.1.20"),
            RuleEdit.opposites(rules, "||example.com^\$client=192.168.1.20"),
        )
        // Allowing it: the block rule.
        assertEquals(listOf("||example.com^"), RuleEdit.opposites(rules, "@@||example.com^\$important"))
        // A different scope isn't an exact opposite.
        assertTrue(RuleEdit.opposites(rules, "|example.com^").isEmpty())
    }

    @Test
    fun appendsOnlyOnce() {
        val rules = listOf("||a.com^", "  ||b.com^ ")
        assertEquals(listOf("||a.com^", "  ||b.com^ ", "||c.com^"), RuleEdit.append(rules, "||c.com^"))
        assertNull(RuleEdit.append(rules, "||b.com^"))
    }

    @Test
    fun removesEveryCopyAndRemembersWhere() {
        val rules = listOf("||a.com^", "@@||x.com^\$important", "||b.com^", " @@||x.com^\$important")
        val (kept, removed) = RuleEdit.remove(rules, listOf("@@||x.com^\$important"))
        assertEquals(listOf("||a.com^", "||b.com^"), kept)
        assertEquals(listOf(1, 3), removed.map { it.index })
    }

    @Test
    fun undoPutsRemovedLinesBackAndTakesAddedOnesOut() {
        val original = listOf("! header", "@@||x.com^\$important", "||a.com^")
        val (kept, removed) = RuleEdit.remove(original, listOf("@@||x.com^\$important"))
        val afterChange = RuleEdit.append(kept, "||x.com^")!!
        val change = RuleChange(added = listOf("||x.com^"), removed = removed)
        assertEquals(original, RuleEdit.undo(afterChange, change))
    }

    @Test
    fun undoKeepsOtherEditsMadeSince() {
        val change = RuleChange(added = listOf("||x.com^"))
        val now = listOf("||a.com^", "||x.com^", "||added-later.com^")
        assertEquals(listOf("||a.com^", "||added-later.com^"), RuleEdit.undo(now, change))
        // Already undone by hand: nothing else changes.
        assertEquals(listOf("||a.com^"), RuleEdit.undo(listOf("||a.com^"), change))
        // A removed line that's back already isn't added twice; an index past the end goes last.
        val back = RuleChange(removed = listOf(IndexedValue(5, "||gone.com^")))
        assertEquals(listOf("||a.com^", "||gone.com^"), RuleEdit.undo(listOf("||a.com^"), back))
        assertEquals(listOf("||gone.com^"), RuleEdit.undo(listOf("||gone.com^"), back))
    }
}
