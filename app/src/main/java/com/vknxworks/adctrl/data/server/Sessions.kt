package com.vknxworks.adctrl.data.server

import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.ServerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID

/** The active server and its API client. */
class ActiveServer(val server: Server, val client: AghClient)

/** The active server's last status: what it runs, and whether it can be reached right now. */
data class Connection(
    val status: ServerStatus? = null,
    val version: AghVersion? = null,
    val loading: Boolean = true,
    val error: AghError? = null,
)

/**
 * Keeps the list of servers, which one is active, an API client for it and its status.
 * Switching servers rebuilds the client; any change to a server (address, certificate, read-only) does too.
 */
class Sessions(
    private val store: ServerStore,
    private val baseHttp: OkHttpClient,
    private val scope: CoroutineScope,
) {
    val servers: StateFlow<List<Server>> = store.servers

    /** Bumped when the password changes without other server settings changing, so the client is rebuilt. */
    private val revision = MutableStateFlow(0)

    val active: StateFlow<ActiveServer?> = combine(store.servers, store.activeId, revision) { list, id, rev -> list.firstOrNull { it.id == id } to rev }
        .distinctUntilChanged()
        .map { (server, _) -> server?.let { ActiveServer(it, clientFor(it)) } }
        .stateIn(scope, SharingStarted.Eagerly, store.servers.value.firstOrNull { it.id == store.activeId.value }?.let { ActiveServer(it, clientFor(it)) })

    private val _connection = MutableStateFlow(Connection())
    val connection: StateFlow<Connection> = _connection.asStateFlow()
    private var statusJob: Job? = null

    init {
        scope.launch {
            active.map { it?.server?.let { s -> Triple(s.id, s.url, s.certPin) } to revision.value }
                .distinctUntilChanged()
                .collect { refreshStatus() }
        }
    }

    fun clientFor(server: Server): AghClient = AghClient(
        server,
        baseHttp,
        store.secrets(server.id),
        // The saved setting, not this copy of the server: a client made before read-only was
        // switched on must not keep writing.
        isReadOnly = { store.servers.value.firstOrNull { it.id == server.id }?.readOnly ?: server.readOnly },
    )

    /** Reloads the active server's status and version. */
    fun refreshStatus() {
        statusJob?.cancel()
        val current = active.value
        if (current == null) {
            _connection.value = Connection(loading = false)
            return
        }
        _connection.value = _connection.value.copy(loading = true)
        statusJob = scope.launch {
            try {
                val status = current.client.status()
                val version = AghVersion.parse(status.version)
                if (status.version != current.server.version) {
                    store.update(current.server.id) { it.copy(version = status.version) }
                }
                _connection.value = Connection(
                    status = status,
                    version = version,
                    loading = false,
                    error = if (version.supported) null else AghError.TooOld(status.version),
                )
            } catch (e: AghError) {
                _connection.value = _connection.value.copy(loading = false, error = e)
            }
        }
    }

    /** A screen fetched the active server's status itself: share it, so the connection card stays current. */
    fun reportStatus(serverId: String, status: ServerStatus) {
        if (active.value?.server?.id != serverId) return
        val version = AghVersion.parse(status.version)
        _connection.value = Connection(status, version, loading = false, error = if (version.supported) null else AghError.TooOld(status.version))
    }

    /** A screen's request to the active server failed in a way that affects the whole connection. */
    fun reportError(serverId: String, e: AghError) {
        if (active.value?.server?.id != serverId) return
        if (e is AghError.ReadOnly || e is AghError.Cancelled || e is AghError.Server || e is AghError.Forbidden) return
        _connection.value = _connection.value.copy(loading = false, error = e)
    }

    fun add(server: Server, secrets: ServerSecrets): Server {
        val saved = if (server.id.isBlank()) server.copy(id = UUID.randomUUID().toString()) else server
        store.saveSecrets(saved.id, secrets)
        store.save(saved)
        store.setActive(saved.id)
        // Editing a server can leave its settings unchanged but its password new: rebuild the client either way.
        revision.value++
        return saved
    }

    fun switchTo(id: String) = store.setActive(id)

    fun remove(id: String) = store.remove(id)

    fun setReadOnly(id: String, readOnly: Boolean) = store.update(id) { it.copy(readOnly = readOnly) }

    fun secrets(id: String): ServerSecrets = store.secrets(id)
}
