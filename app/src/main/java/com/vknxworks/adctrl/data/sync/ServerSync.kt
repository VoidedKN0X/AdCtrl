package com.vknxworks.adctrl.data.sync

import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghJson
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.BlockedServicesConfig
import com.vknxworks.adctrl.data.api.ClientsResponse
import com.vknxworks.adctrl.data.api.EnabledStatus
import com.vknxworks.adctrl.data.api.FilteringStatus
import com.vknxworks.adctrl.data.api.Rewrite
import com.vknxworks.adctrl.data.api.RewriteSettings
import com.vknxworks.adctrl.data.api.SafeSearch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/*
 * Copying AdGuard Home settings from one server to another, through the same API calls the Filters
 * and Clients tabs use (openapi.yaml of v0.107.79): filtering/status, add_url, remove_url, set_url,
 * config and set_rules; rewrite/list, add, delete, update and settings; blocked_services/get and
 * update; safebrowsing, parental and safesearch; clients, clients/add, update and delete.
 */

/** Reads everything a sync compares from one server. */
suspend fun readSnapshot(api: AghClient, version: AghVersion): ServerSnapshot {
    val filtering = api.get("filtering/status", FilteringStatus.serializer())
    val rewrites = api.get("rewrite/list", ListSerializer(Rewrite.serializer()))
    val rewritesEnabled = if (version.rewriteToggle) api.get("rewrite/settings", RewriteSettings.serializer()).enabled else null
    val services = api.get("blocked_services/get", BlockedServicesConfig.serializer())
    val safeBrowsing = api.get("safebrowsing/status", EnabledStatus.serializer()).enabled
    val parental = api.get("parental/status", EnabledStatus.serializer()).enabled
    val safeSearch = api.get("safesearch/status", SafeSearch.serializer())
    val clients = api.get("clients", ClientsResponse.serializer()).clients.mapNotNull { raw ->
        val name = (raw["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        SyncClient(name, JsonObject(raw - CLIENT_OUTPUT_ONLY))
    }
    return ServerSnapshot(
        lists = filtering.filters,
        allowlists = filtering.whitelistFilters,
        filteringEnabled = filtering.enabled,
        updateIntervalHours = filtering.interval,
        rules = filtering.userRules,
        rewrites = rewrites,
        rewritesEnabled = rewritesEnabled,
        services = services,
        safeBrowsing = safeBrowsing,
        parental = parental,
        safeSearch = safeSearch,
        clients = clients,
    )
}

/** One request of a sync that the target refused, with what it was about. */
class SyncFailure(val part: SyncPart, val subject: String, val error: AghError)

private fun rewriteJson(r: Rewrite, withEnabled: Boolean) = buildJsonObject {
    put("domain", r.domain)
    put("answer", r.answer)
    if (withEnabled) put("enabled", r.enabled)
}

/**
 * Applies [plan] to the target server behind [api], one request after the other: removals first, then
 * additions, then changes. A request the server refuses is noted and the rest goes on; read-only
 * mode, a cancelled "Confirm changes" and a server that can't be reached stop everything at once
 * (thrown). [onProgress] gets the number of requests done so far.
 */
suspend fun applySync(plan: SyncPlan, api: AghClient, version: AghVersion, onProgress: (Int) -> Unit): List<SyncFailure> {
    val failures = mutableListOf<SyncFailure>()
    var done = 0

    suspend fun step(part: SyncPart, subject: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: AghError.Server) {
            failures += SyncFailure(part, subject, e)
        } catch (e: AghError.Forbidden) {
            failures += SyncFailure(part, subject, e)
        }
        done++
        onProgress(done)
    }

    suspend fun setUrl(l: ListChange) = api.post(
        "filtering/set_url",
        buildJsonObject {
            put("url", l.url)
            put("whitelist", l.allowlist)
            put("data", buildJsonObject { put("enabled", l.enabled); put("name", l.name); put("url", l.url) })
        },
    )

    plan.listsRemove.forEach { l ->
        step(SyncPart.LISTS, l.name.ifBlank { l.url }) { api.post("filtering/remove_url", buildJsonObject { put("url", l.url); put("whitelist", l.allowlist) }) }
    }
    plan.listsAdd.forEach { l ->
        val before = failures.size
        step(SyncPart.LISTS, l.name.ifBlank { l.url }) { api.post("filtering/add_url", buildJsonObject { put("name", l.name); put("url", l.url); put("whitelist", l.allowlist) }) }
        // A list is switched on when it's added; one that is off on the source is switched off right after.
        if (!l.enabled) {
            if (failures.size == before) step(SyncPart.LISTS, l.name.ifBlank { l.url }) { setUrl(l) } else onProgress(++done)
        }
    }
    plan.listsUpdate.forEach { l -> step(SyncPart.LISTS, l.name.ifBlank { l.url }) { setUrl(l) } }
    plan.filteringConfig?.let { (enabled, interval) ->
        step(SyncPart.LISTS, "") { api.post("filtering/config", buildJsonObject { put("enabled", enabled); put("interval", interval) }) }
    }

    plan.rules?.let { rules ->
        step(SyncPart.RULES, "") { api.post("filtering/set_rules", buildJsonObject { put("rules", JsonArray(rules.map { JsonPrimitive(it) })) }) }
    }

    plan.rewritesRemove.forEach { r -> step(SyncPart.REWRITES, r.domain) { api.post("rewrite/delete", rewriteJson(r, withEnabled = false)) } }
    plan.rewritesAdd.forEach { r -> step(SyncPart.REWRITES, r.domain) { api.post("rewrite/add", rewriteJson(r, withEnabled = version.rewriteToggle)) } }
    plan.rewritesToggle.forEach { r ->
        step(SyncPart.REWRITES, r.domain) {
            api.put("rewrite/update", buildJsonObject { put("target", rewriteJson(r, withEnabled = false)); put("update", rewriteJson(r, withEnabled = true)) })
        }
    }
    plan.rewritesEnabled?.let { on -> step(SyncPart.REWRITES, "") { api.put("rewrite/settings/update", buildJsonObject { put("enabled", on) }) } }

    plan.services?.let { s -> step(SyncPart.SERVICES, "") { api.put("blocked_services/update", AghJson.encodeToJsonElement(BlockedServicesConfig.serializer(), s)) } }

    plan.safeBrowsing?.let { on -> step(SyncPart.PROTECTION, "") { api.post(if (on) "safebrowsing/enable" else "safebrowsing/disable") } }
    plan.parental?.let { on -> step(SyncPart.PROTECTION, "") { api.post(if (on) "parental/enable" else "parental/disable") } }
    plan.safeSearch?.let { s -> step(SyncPart.PROTECTION, "") { api.put("safesearch/settings", AghJson.encodeToJsonElement(SafeSearch.serializer(), s)) } }

    plan.clientsRemove.forEach { name -> step(SyncPart.CLIENTS, name) { api.post("clients/delete", buildJsonObject { put("name", name) }) } }
    plan.clientsAdd.forEach { c -> step(SyncPart.CLIENTS, c.name) { api.post("clients/add", c.data) } }
    plan.clientsUpdate.forEach { c -> step(SyncPart.CLIENTS, c.name) { api.post("clients/update", buildJsonObject { put("name", c.name); put("data", c.data) }) } }

    return failures
}
