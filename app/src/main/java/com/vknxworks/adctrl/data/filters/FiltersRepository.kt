package com.vknxworks.adctrl.data.filters

import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghJson
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.BlockedService
import com.vknxworks.adctrl.data.api.BlockedServicesAll
import com.vknxworks.adctrl.data.api.BlockedServicesConfig
import com.vknxworks.adctrl.data.api.EnabledStatus
import com.vknxworks.adctrl.data.api.FilteringStatus
import com.vknxworks.adctrl.data.api.PauseSchedule
import com.vknxworks.adctrl.data.api.RefreshResult
import com.vknxworks.adctrl.data.api.Rewrite
import com.vknxworks.adctrl.data.api.RewriteSettings
import com.vknxworks.adctrl.data.api.SafeSearch
import com.vknxworks.adctrl.data.server.Sessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class FiltersState(
    val serverId: String = "",
    val filtering: FilteringStatus? = null,
    val services: List<BlockedService> = emptyList(),
    val blocked: BlockedServicesConfig? = null,
    val rewrites: List<Rewrite>? = null,
    /** Null on servers before 0.107.68, where rewrites can't be switched off. */
    val rewritesEnabled: Boolean? = null,
    val safeBrowsing: Boolean? = null,
    val parental: Boolean? = null,
    val safeSearch: SafeSearch? = null,
    /** Unsaved edits of the custom rules, kept while switching tabs; null when nothing is being edited. */
    val rulesDraft: String? = null,
    val loading: Boolean = false,
    val error: AghError? = null,
)

/**
 * The Filters tab for the active server: filter lists, custom rules, blocked services, DNS rewrites
 * and safe browsing, parental control and safe search. Every change is followed by reading the
 * section again, so what's shown is what the server has.
 */
class FiltersRepository(private val sessions: Sessions, scope: CoroutineScope) {
    private val _state = MutableStateFlow(FiltersState())
    val state: StateFlow<FiltersState> = _state.asStateFlow()

    /** One change at a time, so quick taps (blocked services) can't overwrite each other. */
    private val writeLock = Mutex()

    init {
        scope.launch {
            sessions.active.map { it?.server?.id }.distinctUntilChanged().collect { id ->
                _state.value = FiltersState(serverId = id.orEmpty())
            }
        }
    }

    private fun api(): AghClient = sessions.active.value?.client ?: throw AghError.Unreachable(null)

    private fun version(): AghVersion = AghVersion.parse(sessions.active.value?.server?.version.orEmpty())

    /** Runs [block] for the active server, keeping its result only if the server didn't change meanwhile. */
    private suspend fun <T> load(block: suspend (AghClient) -> T, apply: (FiltersState, T) -> FiltersState) {
        val id = sessions.active.value?.server?.id ?: return
        _state.update { it.copy(loading = true) }
        try {
            val result = block(api())
            if (sessions.active.value?.server?.id == id) _state.update { apply(it, result).copy(loading = false, error = null) }
        } catch (e: AghError) {
            sessions.reportError(id, e)
            _state.update { it.copy(loading = false, error = e) }
        }
    }

    // ---------- Loading ----------

    suspend fun loadLists() = load({ it.get("filtering/status", FilteringStatus.serializer()) }) { s, f -> s.copy(filtering = f) }

    suspend fun loadServices() = load({ api ->
        api.get("blocked_services/all", BlockedServicesAll.serializer()) to api.get("blocked_services/get", BlockedServicesConfig.serializer())
    }) { s, (all, cfg) -> s.copy(services = all.services.sortedBy { it.name.lowercase() }, blocked = cfg) }

    suspend fun loadRewrites() = load({ api ->
        val list = api.get("rewrite/list", ListSerializer(Rewrite.serializer()))
        val enabled = if (version().rewriteToggle) runCatching { api.get("rewrite/settings", RewriteSettings.serializer()).enabled }.getOrNull() else null
        list to enabled
    }) { s, (list, enabled) -> s.copy(rewrites = list, rewritesEnabled = enabled) }

    suspend fun loadProtection() = load({ api ->
        Triple(
            api.get("safebrowsing/status", EnabledStatus.serializer()).enabled,
            api.get("parental/status", EnabledStatus.serializer()).enabled,
            api.get("safesearch/status", SafeSearch.serializer()),
        )
    }) { s, (sb, pc, ss) -> s.copy(safeBrowsing = sb, parental = pc, safeSearch = ss) }

    // ---------- Filter lists ----------

    suspend fun setFiltering(enabled: Boolean, intervalHours: Int) = write({ loadLists() }) {
        it.post("filtering/config", buildJsonObject { put("enabled", enabled); put("interval", intervalHours) })
    }

    suspend fun addList(name: String, url: String, allowlist: Boolean) = write({ loadLists() }) {
        it.post("filtering/add_url", buildJsonObject { put("name", name); put("url", url); put("whitelist", allowlist) })
    }

    suspend fun removeList(url: String, allowlist: Boolean) = write({ loadLists() }) {
        it.post("filtering/remove_url", buildJsonObject { put("url", url); put("whitelist", allowlist) })
    }

    /** Changes a list's name, address or on/off ([url] is its current address). */
    suspend fun setList(url: String, allowlist: Boolean, enabled: Boolean, name: String, newUrl: String) = write({ loadLists() }) {
        it.post(
            "filtering/set_url",
            buildJsonObject {
                put("url", url)
                put("whitelist", allowlist)
                put("data", buildJsonObject { put("enabled", enabled); put("name", name); put("url", newUrl) })
            },
        )
    }

    /** Downloads every blocklist and allowlist again now; returns how many changed. */
    suspend fun refreshLists(): Int {
        var updated = 0
        write({ loadLists() }) { api ->
            listOf(false, true).forEach { allow ->
                val r = api.post("filtering/refresh", buildJsonObject { put("whitelist", allow) })
                updated += runCatching { AghJson.decodeFromJsonElement(RefreshResult.serializer(), r).updated }.getOrDefault(0)
            }
        }
        return updated
    }

    // ---------- Custom rules ----------

    fun setRulesDraft(text: String?) = _state.update { it.copy(rulesDraft = text) }

    /** Replaces the custom rules with [rules] (AdGuard Home always takes the whole list). */
    suspend fun saveRules(rules: List<String>) = write({ loadLists() }) {
        it.post("filtering/set_rules", buildJsonObject { put("rules", JsonArray(rules.map { r -> JsonPrimitive(r) })) })
        // Only once it's saved: a failed save keeps the edits.
        setRulesDraft(null)
    }

    // ---------- Blocked services ----------

    /** Blocks or unblocks one service, keeping the others and the schedule as the server has them. */
    suspend fun setServiceBlocked(id: String, blocked: Boolean) {
        val current = _state.value.blocked ?: return
        val ids = if (blocked) (current.ids + id).distinct() else current.ids - id
        // Show it at once; the server's answer follows.
        _state.update { it.copy(blocked = current.copy(ids = ids)) }
        saveServices { cfg -> cfg.copy(ids = if (blocked) (cfg.ids + id).distinct() else cfg.ids - id) }
    }

    suspend fun setSchedule(schedule: PauseSchedule) = saveServices { it.copy(schedule = schedule) }

    /** Reads the server's current list first, so a change made elsewhere isn't lost. */
    private suspend fun saveServices(change: (BlockedServicesConfig) -> BlockedServicesConfig) = write({ loadServices() }) { api ->
        val fresh = api.get("blocked_services/get", BlockedServicesConfig.serializer())
        val updated = change(fresh)
        api.put("blocked_services/update", AghJson.encodeToJsonElement(BlockedServicesConfig.serializer(), updated))
    }

    // ---------- DNS rewrites ----------

    suspend fun addRewrite(r: Rewrite) = write({ loadRewrites() }) {
        it.post("rewrite/add", rewriteJson(r, includeEnabled = version().rewriteToggle))
    }

    suspend fun deleteRewrite(r: Rewrite) = write({ loadRewrites() }) {
        it.post("rewrite/delete", rewriteJson(r, includeEnabled = false))
    }

    suspend fun updateRewrite(old: Rewrite, new: Rewrite) = write({ loadRewrites() }) {
        it.put(
            "rewrite/update",
            buildJsonObject {
                put("target", rewriteJson(old, includeEnabled = false))
                put("update", rewriteJson(new, includeEnabled = version().rewriteToggle))
            },
        )
    }

    suspend fun setRewritesEnabled(enabled: Boolean) = write({ loadRewrites() }) {
        it.put("rewrite/settings/update", buildJsonObject { put("enabled", enabled) })
    }

    private fun rewriteJson(r: Rewrite, includeEnabled: Boolean): JsonElement = buildJsonObject {
        put("domain", r.domain)
        put("answer", r.answer)
        if (includeEnabled) put("enabled", r.enabled)
    }

    // ---------- Safe browsing, parental control, safe search ----------

    suspend fun setSafeBrowsing(on: Boolean) = write({ loadProtection() }) { it.post(if (on) "safebrowsing/enable" else "safebrowsing/disable") }

    suspend fun setParental(on: Boolean) = write({ loadProtection() }) { it.post(if (on) "parental/enable" else "parental/disable") }

    suspend fun setSafeSearch(s: SafeSearch) = write({ loadProtection() }) {
        it.put("safesearch/settings", AghJson.encodeToJsonElement(SafeSearch.serializer(), s))
    }

    /** Sends a change, then reloads the section (also after a failure, to show what the server really has). */
    private suspend fun write(reload: suspend () -> Unit, block: suspend (AghClient) -> Unit) {
        writeLock.withLock {
            try {
                block(api())
            } finally {
                reload()
            }
        }
    }
}
