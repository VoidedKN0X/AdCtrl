package com.vknxworks.adctrl.data.queries

import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.CheckHostResult
import com.vknxworks.adctrl.data.api.FilteringStatus
import com.vknxworks.adctrl.data.api.QueryFilter
import com.vknxworks.adctrl.data.api.QueryLogItem
import com.vknxworks.adctrl.data.api.QueryLogPage
import com.vknxworks.adctrl.data.server.Sessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the Queries tab is filtered by. [client] is matched exactly; [text] matches domains and clients. */
data class QuerySearch(
    val text: String = "",
    val client: String = "",
    val filter: QueryFilter = QueryFilter.ALL,
) {
    val isEmpty: Boolean get() = text.isBlank() && client.isBlank() && filter == QueryFilter.ALL
}

data class QueriesState(
    val serverId: String = "",
    val search: QuerySearch = QuerySearch(),
    val items: List<QueryLogItem> = emptyList(),
    /** Time to ask for the next older page from; empty when there's nothing older. */
    val oldest: String = "",
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: AghError? = null,
    /** Filter list names by ID, for "blocked by …" (0 is the custom rules). */
    val listNames: Map<Long, String> = emptyMap(),
    val updatedAt: Long = 0,
) {
    val endReached: Boolean get() = oldest.isBlank()
}

/**
 * The query log of the active server. Held in memory only: it's never written to the phone's
 * storage, and it's cleared when switching servers.
 */
class QueriesRepository(private val sessions: Sessions, scope: CoroutineScope) {
    private val _state = MutableStateFlow(QueriesState())
    val state: StateFlow<QueriesState> = _state.asStateFlow()
    private val lock = Mutex()
    private var namesFetchedAt = 0L

    init {
        scope.launch {
            sessions.active.map { it?.server?.id }.distinctUntilChanged().collect { id ->
                namesFetchedAt = 0
                _state.value = QueriesState(serverId = id.orEmpty())
            }
        }
    }

    /** Sets what the log is filtered by (for example from a tap on the Overview) and reloads. */
    suspend fun setSearch(search: QuerySearch) {
        if (search == _state.value.search && _state.value.updatedAt > 0) return
        _state.value = _state.value.copy(search = search, items = emptyList(), oldest = "", updatedAt = 0)
        reload()
    }

    /** Loads the newest page, replacing what's shown. */
    suspend fun reload() = lock.withLock {
        val active = sessions.active.value ?: return@withLock
        _state.value = _state.value.copy(loading = true)
        try {
            refreshNames()
            val page = fetch(olderThan = null)
            if (active.server.id != sessions.active.value?.server?.id) return@withLock
            _state.value = _state.value.copy(
                items = page.data,
                oldest = if (page.data.size < PAGE) "" else page.oldest,
                loading = false,
                error = null,
                updatedAt = System.currentTimeMillis(),
            )
        } catch (e: AghError) {
            sessions.reportError(active.server.id, e)
            _state.value = _state.value.copy(loading = false, error = e)
        }
    }

    /**
     * Live update: fetches the newest page and puts queries that weren't shown yet on top, keeping
     * what's further down. Returns false when it failed (so polling backs off).
     */
    suspend fun poll(): Boolean = lock.withLock {
        val active = sessions.active.value ?: return@withLock false
        if (_state.value.updatedAt == 0L) return@withLock true
        try {
            val page = fetch(olderThan = null)
            if (active.server.id != sessions.active.value?.server?.id) return@withLock true
            val shown = _state.value.items
            val known = shown.mapTo(HashSet()) { it.key }
            val fresh = page.data.takeWhile { it.key !in known }
            _state.value = _state.value.copy(
                items = if (fresh.size >= PAGE) page.data else fresh + shown,
                // A whole new page means there's a gap: start the list over from it.
                oldest = if (fresh.size >= PAGE) page.oldest else _state.value.oldest,
                error = null,
                updatedAt = System.currentTimeMillis(),
            )
            true
        } catch (e: AghError) {
            _state.value = _state.value.copy(error = e)
            false
        }
    }

    /** Infinite scroll: the next older page. */
    suspend fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.loading || s.endReached) return
        lock.withLock {
            val active = sessions.active.value ?: return@withLock
            _state.value = _state.value.copy(loadingMore = true)
            try {
                val page = fetch(olderThan = _state.value.oldest)
                if (active.server.id != sessions.active.value?.server?.id) return@withLock
                val known = _state.value.items.mapTo(HashSet()) { it.key }
                _state.value = _state.value.copy(
                    items = _state.value.items + page.data.filter { it.key !in known },
                    oldest = if (page.data.size < PAGE) "" else page.oldest,
                    loadingMore = false,
                )
            } catch (e: AghError) {
                _state.value = _state.value.copy(loadingMore = false, error = e)
            }
        }
    }

    /** GET /control/filtering/check_host: would [host] be blocked, and by what? */
    suspend fun checkHost(host: String, client: String?, qtype: String?): CheckHostResult {
        val active = sessions.active.value ?: throw AghError.Unreachable(null)
        refreshNames()
        val version = AghVersion.parse(active.server.version)
        val query = buildMap {
            put("name", host.trim().trimEnd('.'))
            if (version.checkHostClient) {
                client?.takeIf { it.isNotBlank() }?.let { put("client", it.trim()) }
                qtype?.takeIf { it.isNotBlank() }?.let { put("qtype", it) }
            }
        }
        return active.client.get("filtering/check_host", CheckHostResult.serializer(), query)
    }

    private suspend fun fetch(olderThan: String?): QueryLogPage {
        val active = sessions.active.value ?: throw AghError.Unreachable(null)
        val s = _state.value.search
        val version = AghVersion.parse(active.server.version)
        val query = buildList {
            add("limit" to PAGE.toString())
            olderThan?.takeIf { it.isNotBlank() }?.let { add("older_than" to it) }
            // AdGuard Home has one search field for domains and clients; double quotes make it an
            // exact match, which is how "only this client" works.
            val term = when {
                s.client.isNotBlank() -> "\"${s.client.trim()}\""
                else -> s.text.trim()
            }
            if (term.isNotEmpty()) add("search" to term)
            if (s.filter != QueryFilter.ALL) {
                if (version.querylogReason) s.filter.reasons.forEach { add("reason" to it) }
                else s.filter.responseStatus?.let { add("response_status" to it) }
            }
        }
        val page = active.client.getMulti("querylog", QueryLogPage.serializer(), query)
        // With a client filter and search text, the text narrows the client's queries here.
        val text = s.text.trim().lowercase()
        return if (s.client.isNotBlank() && text.isNotEmpty()) page.copy(data = page.data.filter { it.domain.lowercase().contains(text) || it.asciiDomain.contains(text) })
        else page
    }

    /** List names for "blocked by …", refreshed every few minutes. */
    private suspend fun refreshNames() {
        val now = System.currentTimeMillis()
        if (now - namesFetchedAt < NAMES_TTL && _state.value.listNames.isNotEmpty()) return
        val active = sessions.active.value ?: return
        runCatching { active.client.get("filtering/status", FilteringStatus.serializer()) }.getOrNull()?.let { f ->
            _state.value = _state.value.copy(listNames = (f.filters + f.whitelistFilters).associate { it.id to it.name.ifBlank { it.url } })
            namesFetchedAt = now
        }
    }

    private companion object {
        const val PAGE = 50
        const val NAMES_TTL = 5 * 60_000L
    }
}
