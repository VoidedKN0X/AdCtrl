package com.vknxworks.adctrl.data.sync

import com.vknxworks.adctrl.data.api.BlockedServicesConfig
import com.vknxworks.adctrl.data.api.FilterList
import com.vknxworks.adctrl.data.api.Rewrite
import com.vknxworks.adctrl.data.api.SafeSearch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The groups of AdGuard Home settings that can be copied from one server to another. */
enum class SyncPart { LISTS, RULES, REWRITES, SERVICES, PROTECTION, CLIENTS }

/** A persistent client: its name and AdGuard Home's own JSON for it, without the fields the server fills in itself. */
data class SyncClient(val name: String, val data: JsonObject)

/** Everything a sync reads from one server. Upstream DNS, DHCP, encryption and the access list are never part of it. */
data class ServerSnapshot(
    val lists: List<FilterList> = emptyList(),
    val allowlists: List<FilterList> = emptyList(),
    val filteringEnabled: Boolean = true,
    val updateIntervalHours: Int = 24,
    val rules: List<String> = emptyList(),
    val rewrites: List<Rewrite> = emptyList(),
    /** Null on servers older than 0.107.68, which can't switch rewrites off. */
    val rewritesEnabled: Boolean? = null,
    val services: BlockedServicesConfig = BlockedServicesConfig(),
    val safeBrowsing: Boolean = false,
    val parental: Boolean = false,
    val safeSearch: SafeSearch = SafeSearch(),
    val clients: List<SyncClient> = emptyList(),
)

/** A blocklist or allowlist to add, remove or change on the target. */
data class ListChange(val url: String, val name: String, val allowlist: Boolean, val enabled: Boolean)

/**
 * What has to change on the target so that it matches the source, for the chosen parts. A null or
 * empty entry means "already the same" (or "not chosen").
 */
data class SyncPlan(
    val listsAdd: List<ListChange> = emptyList(),
    val listsRemove: List<ListChange> = emptyList(),
    val listsUpdate: List<ListChange> = emptyList(),
    /** Filtering on or off and the update interval in hours, when they differ. */
    val filteringConfig: Pair<Boolean, Int>? = null,
    /** The source's custom rules, when the target's differ. */
    val rules: List<String>? = null,
    /** How many custom rules the target has now. */
    val rulesBefore: Int = 0,
    val rewritesAdd: List<Rewrite> = emptyList(),
    val rewritesRemove: List<Rewrite> = emptyList(),
    /** Rewrites both have, whose on/off state differs: as they are on the source. */
    val rewritesToggle: List<Rewrite> = emptyList(),
    val rewritesEnabled: Boolean? = null,
    val services: BlockedServicesConfig? = null,
    val safeBrowsing: Boolean? = null,
    val parental: Boolean? = null,
    val safeSearch: SafeSearch? = null,
    val clientsAdd: List<SyncClient> = emptyList(),
    val clientsUpdate: List<SyncClient> = emptyList(),
    val clientsRemove: List<String> = emptyList(),
) {
    /** How many requests applying this plan takes (a list added switched off takes two). */
    val steps: Int
        get() = listsAdd.size + listsAdd.count { !it.enabled } + listsRemove.size + listsUpdate.size +
            (if (filteringConfig != null) 1 else 0) + (if (rules != null) 1 else 0) +
            rewritesAdd.size + rewritesRemove.size + rewritesToggle.size + (if (rewritesEnabled != null) 1 else 0) +
            (if (services != null) 1 else 0) + (if (safeBrowsing != null) 1 else 0) + (if (parental != null) 1 else 0) +
            (if (safeSearch != null) 1 else 0) + clientsAdd.size + clientsUpdate.size + clientsRemove.size

    val isEmpty: Boolean get() = steps == 0

    /** Things that exist on the target and would be gone after the sync. */
    val removals: Int get() = listsRemove.size + rewritesRemove.size + clientsRemove.size
}

/** Fields of a client that the server reports but doesn't take back. */
internal val CLIENT_OUTPUT_ONLY = setOf("disallowed", "disallowed_rule", "whois_info")

/** A client's settings without the server-filled fields and without explicit nulls, so equal settings compare equal. */
internal fun normalizeClient(raw: JsonObject): JsonObject =
    JsonObject(raw.filter { (k, v) -> k !in CLIENT_OUTPUT_ONLY && !(v is JsonPrimitive && !v.isString && v.content == "null") })

private fun listKey(l: FilterList, allow: Boolean) = (if (allow) "A" else "B") + l.url

/** Works out what to change on [target] to make the chosen [parts] equal to [source]. Changes nothing. */
fun planSync(source: ServerSnapshot, target: ServerSnapshot, parts: Set<SyncPart>): SyncPlan {
    var plan = SyncPlan(rulesBefore = target.rules.size)

    if (SyncPart.LISTS in parts) {
        val src = source.lists.map { it to false } + source.allowlists.map { it to true }
        val dst = target.lists.map { it to false } + target.allowlists.map { it to true }
        val dstByKey = dst.associateBy { (l, a) -> listKey(l, a) }
        val srcKeys = src.map { (l, a) -> listKey(l, a) }.toSet()
        plan = plan.copy(
            listsAdd = src.filter { (l, a) -> listKey(l, a) !in dstByKey }.map { (l, a) -> ListChange(l.url, l.name, a, l.enabled) },
            listsRemove = dst.filter { (l, a) -> listKey(l, a) !in srcKeys }.map { (l, a) -> ListChange(l.url, l.name, a, l.enabled) },
            listsUpdate = src.mapNotNull { (l, a) ->
                val there = dstByKey[listKey(l, a)]?.first ?: return@mapNotNull null
                if (there.enabled != l.enabled || there.name != l.name) ListChange(l.url, l.name, a, l.enabled) else null
            },
            filteringConfig = if (source.filteringEnabled != target.filteringEnabled || source.updateIntervalHours != target.updateIntervalHours) {
                source.filteringEnabled to source.updateIntervalHours
            } else {
                null
            },
        )
    }

    if (SyncPart.RULES in parts && source.rules != target.rules) plan = plan.copy(rules = source.rules)

    if (SyncPart.REWRITES in parts) {
        val dstByKey = target.rewrites.associateBy { it.key }
        val srcKeys = source.rewrites.map { it.key }.toSet()
        plan = plan.copy(
            rewritesAdd = source.rewrites.filter { it.key !in dstByKey }.distinctBy { it.key },
            rewritesRemove = target.rewrites.filter { it.key !in srcKeys }.distinctBy { it.key },
            // Only where the target can switch rewrites at all.
            rewritesToggle = if (target.rewritesEnabled == null) emptyList() else source.rewrites.filter { r -> dstByKey[r.key]?.let { it.enabled != r.enabled } == true },
            rewritesEnabled = source.rewritesEnabled?.takeIf { target.rewritesEnabled != null && it != target.rewritesEnabled },
        )
    }

    if (SyncPart.SERVICES in parts) {
        val same = source.services.ids.toSet() == target.services.ids.toSet() && source.services.schedule == target.services.schedule
        if (!same) plan = plan.copy(services = source.services)
    }

    if (SyncPart.PROTECTION in parts) {
        plan = plan.copy(
            safeBrowsing = source.safeBrowsing.takeIf { it != target.safeBrowsing },
            parental = source.parental.takeIf { it != target.parental },
            safeSearch = source.safeSearch.takeIf { it != target.safeSearch },
        )
    }

    if (SyncPart.CLIENTS in parts) {
        val dstByName = target.clients.associateBy { it.name }
        val srcNames = source.clients.map { it.name }.toSet()
        plan = plan.copy(
            clientsAdd = source.clients.filter { it.name !in dstByName },
            clientsUpdate = source.clients.filter { c -> dstByName[c.name]?.let { normalizeClient(it.data) != normalizeClient(c.data) } == true },
            clientsRemove = target.clients.map { it.name }.filter { it !in srcNames },
        )
    }
    return plan
}
