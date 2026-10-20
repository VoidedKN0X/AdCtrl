package com.vknxworks.adctrl.data.overview

import android.content.Context
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghJson
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.DhcpStatus
import com.vknxworks.adctrl.data.api.DnsInfo
import com.vknxworks.adctrl.data.api.FilteringStatus
import com.vknxworks.adctrl.data.api.ServerStatus
import com.vknxworks.adctrl.data.api.Stats
import com.vknxworks.adctrl.data.api.StatsConfig
import com.vknxworks.adctrl.data.api.VersionInfo
import com.vknxworks.adctrl.data.server.Server
import com.vknxworks.adctrl.data.server.Sessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** The ranges the Overview offers; only those within the server's statistics retention are shown. */
enum class StatsRange(val ms: Long) {
    DAY(24L * 3_600_000),
    WEEK(7L * 24 * 3_600_000),
    MONTH(30L * 24 * 3_600_000),
    QUARTER(90L * 24 * 3_600_000),
}

/** Durations offered when pausing protection. Null means "until turned back on". */
enum class PauseDuration(val ms: Long?) {
    S30(30_000),
    M1(60_000),
    M5(300_000),
    M10(600_000),
    H1(3_600_000),
    H8(8 * 3_600_000),
    UNTIL_ON(null),
}

/**
 * Everything the Overview shows, as last fetched. Saved per server so the last known state can be
 * shown (marked as out of date) when the server can't be reached.
 */
@Serializable
data class OverviewSnapshot(
    val serverId: String = "",
    val status: ServerStatus? = null,
    val stats: Stats? = null,
    /** The range [stats] covers, in ms; 0 when it's the whole retention period. */
    val statsRangeMs: Long = 0,
    val statsConfig: StatsConfig? = null,
    val dnsInfo: DnsInfo? = null,
    val dhcp: DhcpStatus? = null,
    val versionInfo: VersionInfo? = null,
    val filtering: FilteringStatus? = null,
    /** When this snapshot was fetched (ms since epoch). */
    val updatedAt: Long = 0,
)

data class OverviewState(
    val snapshot: OverviewSnapshot = OverviewSnapshot(),
    /** When a protection pause ends (ms since epoch, phone clock); 0 when not paused for a set time. */
    val pausedUntil: Long = 0,
    val range: StatsRange = StatsRange.DAY,
    val loading: Boolean = false,
    /** The last refresh failed; [snapshot] is the last known state. */
    val error: AghError? = null,
)

/** Result of changing protection on one or more servers. */
data class ProtectionResult(val failed: List<Pair<Server, AghError>>)

/**
 * Loads the Overview for the active server: status, statistics for the chosen range, DNS and DHCP
 * settings, filter lists and available updates. Also changes protection (pause, resume, turn off),
 * on the active server or on every server that isn't read-only.
 */
class OverviewRepository(
    private val context: Context,
    private val sessions: Sessions,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()
    private val lock = Mutex()

    /** The server's version check is only asked again after this long (or when asked explicitly). */
    private var versionCheckedAt = 0L

    init {
        // Switching servers shows that server's last known state right away, then refreshes.
        scope.launch {
            sessions.active.map { it?.server?.id }.distinctUntilChanged().collect { id ->
                versionCheckedAt = 0
                val cached = id?.let { load(it) }
                _state.value = OverviewState(
                    snapshot = cached ?: OverviewSnapshot(serverId = id.orEmpty()),
                    range = _state.value.range,
                )
            }
        }
    }

    fun setRange(range: StatsRange) {
        if (range == _state.value.range) return
        _state.value = _state.value.copy(range = range)
        scope.launch { refresh(force = false) }
    }

    /** Reloads everything. [force] also asks the server to check for a new AdGuard Home version now. */
    suspend fun refresh(force: Boolean = false) = lock.withLock {
        val active = sessions.active.value ?: return@withLock
        val server = active.server
        val client = active.client
        _state.value = _state.value.copy(loading = true)
        try {
            val snap = coroutineScope {
                val status = async { client.status() }
                val statsConfig = async { client.get("stats/config", StatsConfig.serializer()) }
                val dnsInfo = async { runCatching { client.get("dns_info", DnsInfo.serializer()) }.getOrNull() }
                val dhcp = async { runCatching { client.get("dhcp/status", DhcpStatus.serializer()) }.getOrNull() }
                val filtering = async { runCatching { client.get("filtering/status", FilteringStatus.serializer()) }.getOrNull() }
                val st = status.await()
                val version = AghVersion.parse(st.version)
                val config = statsConfig.await()
                val rangeMs = statsRangeMs(config, version)
                val stats = client.get("stats", Stats.serializer(), if (rangeMs > 0) mapOf("recent" to rangeMs.toString()) else emptyMap())
                val now = System.currentTimeMillis()
                val checkVersion = force || now - versionCheckedAt > VERSION_CHECK_MS || _state.value.snapshot.versionInfo == null
                val versionInfo = if (checkVersion) {
                    runCatching {
                        AghJson.decodeFromJsonElement(
                            VersionInfo.serializer(),
                            client.postRead("version.json", buildJsonObject { put("recheck_now", force) }),
                        )
                    }.getOrNull()?.also { versionCheckedAt = now }
                } else {
                    _state.value.snapshot.versionInfo
                }
                OverviewSnapshot(
                    serverId = server.id,
                    status = st,
                    stats = stats,
                    statsRangeMs = rangeMs,
                    statsConfig = config,
                    dnsInfo = dnsInfo.await(),
                    dhcp = dhcp.await(),
                    versionInfo = versionInfo ?: _state.value.snapshot.versionInfo,
                    filtering = filtering.await(),
                    updatedAt = now,
                )
            }
            if (sessions.active.value?.server?.id != server.id) return@withLock
            snap.status?.let { sessions.reportStatus(server.id, it) }
            _state.value = _state.value.copy(
                snapshot = snap,
                pausedUntil = pausedUntil(snap.status, snap.updatedAt),
                loading = false,
                error = null,
            )
            save(snap)
        } catch (e: AghError) {
            if (sessions.active.value?.server?.id != server.id) return@withLock
            sessions.reportError(server.id, e)
            _state.value = _state.value.copy(loading = false, error = e)
        }
    }

    /** Status only: quick, for the countdown ending and after changing protection. */
    suspend fun refreshStatus() {
        val active = sessions.active.value ?: return
        try {
            val st = active.client.status()
            if (sessions.active.value?.server?.id != active.server.id) return
            sessions.reportStatus(active.server.id, st)
            val now = System.currentTimeMillis()
            _state.value = _state.value.copy(
                snapshot = _state.value.snapshot.copy(status = st),
                pausedUntil = pausedUntil(st, now),
            )
        } catch (e: AghError) {
            sessions.reportError(active.server.id, e)
        }
    }

    /**
     * Pauses protection for [pause] (or until turned back on), or turns it back on ([pause] null),
     * on the active server, or on every saved server that isn't read-only when [allServers] is set.
     */
    suspend fun setProtection(pause: PauseDuration?, allServers: Boolean): ProtectionResult {
        val activeServer = sessions.active.value?.server ?: return ProtectionResult(emptyList())
        val targets = if (allServers) sessions.servers.value.filter { !it.readOnly } else listOf(activeServer)
        val body = buildJsonObject {
            put("enabled", pause == null)
            pause?.ms?.let { put("duration", it) }
        }
        val failed = mutableListOf<Pair<Server, AghError>>()
        targets.forEach { server ->
            val client = if (server.id == activeServer.id) sessions.active.value?.client ?: sessions.clientFor(server) else sessions.clientFor(server)
            try {
                client.post("protection", body)
            } catch (e: AghError) {
                failed += server to e
            }
        }
        refreshStatus()
        return ProtectionResult(failed)
    }

    /** POST /control/cache_clear on the active server. */
    suspend fun clearCache() {
        val client = sessions.active.value?.client ?: return
        client.post("cache_clear")
    }

    /** POST /control/update: AdGuard Home downloads the new version and restarts. */
    suspend fun startUpdate() {
        val client = sessions.active.value?.client ?: return
        client.post("update")
    }

    /** The stats range to request: the chosen one when the server allows it, else the whole retention (0). */
    private fun statsRangeMs(config: StatsConfig, version: AghVersion): Long {
        if (!version.statsRecent) return 0
        val range = _state.value.range.ms
        return if (config.interval in 1 until range) 0 else range
    }

    private fun pausedUntil(status: ServerStatus?, fetchedAt: Long): Long {
        if (status == null || status.protectionEnabled || status.protectionDisabledDuration <= 0) return 0
        return fetchedAt + status.protectionDisabledDuration
    }

    // --- Last known state, per server, in the app's private storage (never backed up) ---

    private fun file(id: String) = File(File(context.filesDir, "overview").apply { mkdirs() }, "$id.json")

    private suspend fun save(snap: OverviewSnapshot) = withContext(Dispatchers.IO) {
        runCatching { file(snap.serverId).writeText(AghJson.encodeToString(OverviewSnapshot.serializer(), snap)) }
    }

    private suspend fun load(id: String): OverviewSnapshot? = withContext(Dispatchers.IO) {
        runCatching { AghJson.decodeFromString(OverviewSnapshot.serializer(), file(id).readText()) }.getOrNull()
    }

    /** Deletes a removed server's saved state. */
    fun forget(id: String) {
        runCatching { file(id).delete() }
    }

    private companion object {
        const val VERSION_CHECK_MS = 6 * 3_600_000L
    }
}
