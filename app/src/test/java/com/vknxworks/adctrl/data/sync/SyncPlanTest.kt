package com.vknxworks.adctrl.data.sync

import com.vknxworks.adctrl.data.api.BlockedServicesConfig
import com.vknxworks.adctrl.data.api.DayRange
import com.vknxworks.adctrl.data.api.FilterList
import com.vknxworks.adctrl.data.api.PauseSchedule
import com.vknxworks.adctrl.data.api.Rewrite
import com.vknxworks.adctrl.data.api.SafeSearch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlanTest {
    private val all = SyncPart.entries.toSet()

    private fun list(url: String, name: String = url, enabled: Boolean = true) = FilterList(id = 1, enabled = enabled, name = name, url = url)

    private fun client(name: String, vararg pairs: Pair<String, Any?>) = SyncClient(
        name,
        JsonObject(
            mapOf("name" to JsonPrimitive(name)) + pairs.associate { (k, v) ->
                k to when (v) {
                    null -> JsonNull
                    is Boolean -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                }
            },
        ),
    )

    @Test
    fun identicalServersNeedNothing() {
        val s = ServerSnapshot(
            lists = listOf(list("https://a")),
            rules = listOf("||x^"),
            rewrites = listOf(Rewrite("nas.lan", "192.168.1.5")),
            rewritesEnabled = true,
            clients = listOf(client("Phone", "filtering_enabled" to true)),
        )
        val plan = planSync(s, s, all)
        assertTrue(plan.isEmpty)
        assertEquals(0, plan.steps)
    }

    @Test
    fun listsAreAddedRemovedAndChanged() {
        val source = ServerSnapshot(
            lists = listOf(list("https://a"), list("https://b", enabled = false), list("https://c", name = "New name")),
            allowlists = listOf(list("https://a")),
        )
        val target = ServerSnapshot(
            lists = listOf(list("https://a"), list("https://c", name = "Old name"), list("https://gone")),
            filteringEnabled = false,
        )
        val plan = planSync(source, target, setOf(SyncPart.LISTS))
        // The allowlist with the same address as a blocklist is its own list.
        assertEquals(listOf("https://b" to false, "https://a" to true), plan.listsAdd.map { it.url to it.allowlist })
        assertEquals(listOf("https://gone"), plan.listsRemove.map { it.url })
        assertEquals(listOf("New name"), plan.listsUpdate.map { it.name })
        assertEquals(true to 24, plan.filteringConfig)
        // Two adds (one of them switched off: one more request), one removal, one change, the config.
        assertEquals(2 + 1 + 1 + 1 + 1, plan.steps)
        assertEquals(1, plan.removals)
    }

    @Test
    fun onlyChosenPartsArePlanned() {
        val source = ServerSnapshot(rules = listOf("||a^"), rewrites = listOf(Rewrite("a", "1.1.1.1")), safeBrowsing = true)
        val target = ServerSnapshot()
        val plan = planSync(source, target, setOf(SyncPart.RULES))
        assertEquals(listOf("||a^"), plan.rules)
        assertTrue(plan.rewritesAdd.isEmpty())
        assertNull(plan.safeBrowsing)
        assertEquals(1, plan.steps)
        assertTrue(planSync(source, target, emptySet()).isEmpty)
    }

    @Test
    fun rewritesFollowTheSource() {
        val source = ServerSnapshot(
            rewrites = listOf(Rewrite("nas.lan", "192.168.1.5", enabled = false), Rewrite("new.lan", "192.168.1.6")),
            rewritesEnabled = false,
        )
        val target = ServerSnapshot(rewrites = listOf(Rewrite("nas.lan", "192.168.1.5"), Rewrite("old.lan", "192.168.1.7")), rewritesEnabled = true)
        val plan = planSync(source, target, setOf(SyncPart.REWRITES))
        assertEquals(listOf("new.lan"), plan.rewritesAdd.map { it.domain })
        assertEquals(listOf("old.lan"), plan.rewritesRemove.map { it.domain })
        assertEquals(listOf("nas.lan"), plan.rewritesToggle.map { it.domain })
        assertEquals(false, plan.rewritesEnabled)
        // A target that can't switch rewrites gets no switch requests.
        val old = planSync(source, target.copy(rewritesEnabled = null), setOf(SyncPart.REWRITES))
        assertTrue(old.rewritesToggle.isEmpty())
        assertNull(old.rewritesEnabled)
    }

    @Test
    fun servicesAndProtectionOnlyWhenDifferent() {
        val schedule = PauseSchedule(mon = DayRange(0, 3_600_000))
        val source = ServerSnapshot(services = BlockedServicesConfig(listOf("tiktok", "youtube"), schedule), parental = true, safeSearch = SafeSearch(enabled = true))
        val sameOrder = ServerSnapshot(services = BlockedServicesConfig(listOf("youtube", "tiktok"), schedule), parental = true, safeSearch = SafeSearch(enabled = true))
        assertTrue(planSync(source, sameOrder, all).isEmpty)
        val plan = planSync(source, ServerSnapshot(), all)
        assertEquals(listOf("tiktok", "youtube"), plan.services?.ids)
        assertEquals(true, plan.parental)
        assertNull(plan.safeBrowsing)
        assertEquals(true, plan.safeSearch?.enabled)
    }

    @Test
    fun clientsAreMatchedByName() {
        val source = ServerSnapshot(
            clients = listOf(
                client("Phone", "filtering_enabled" to true),
                client("Tablet", "filtering_enabled" to false),
                client("Same", "filtering_enabled" to true, "whois_info" to "x", "safe_search" to null),
            ),
        )
        val target = ServerSnapshot(
            clients = listOf(
                client("Tablet", "filtering_enabled" to true),
                client("Same", "filtering_enabled" to true),
                client("Old laptop"),
            ),
        )
        val plan = planSync(source, target, setOf(SyncPart.CLIENTS))
        assertEquals(listOf("Phone"), plan.clientsAdd.map { it.name })
        // Fields the server fills in itself, and explicit nulls, don't make a client "changed".
        assertEquals(listOf("Tablet"), plan.clientsUpdate.map { it.name })
        assertEquals(listOf("Old laptop"), plan.clientsRemove)
        assertEquals(1, plan.removals)
    }
}
