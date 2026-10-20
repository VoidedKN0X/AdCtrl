package com.vknxworks.adctrl.data.clients

import com.vknxworks.adctrl.data.api.AccessList
import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghJson
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.ClientFields
import com.vknxworks.adctrl.data.api.ClientFind
import com.vknxworks.adctrl.data.api.ClientsResponse
import com.vknxworks.adctrl.data.api.PersistentClient
import com.vknxworks.adctrl.data.api.QueryLogItem
import com.vknxworks.adctrl.data.api.QueryLogPage
import com.vknxworks.adctrl.data.api.RuntimeClient
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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.InetAddress

/** What a client ID is. Only IPs, CIDRs and ClientIDs can go on AdGuard Home's access list. */
enum class IdKind { IP, CIDR, MAC, CLIENT_ID }

object ClientIds {
    private val MAC = Regex("""^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$""")
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun kind(id: String): IdKind {
        val t = id.trim()
        return when {
            MAC.matches(t) -> IdKind.MAC
            t.contains('/') -> IdKind.CIDR
            IPV4.matches(t) || (t.contains(':') && runCatching { InetAddress.getByName(t) }.isSuccess) -> IdKind.IP
            else -> IdKind.CLIENT_ID
        }
    }

    /** The IDs that can be blocked through the access list. */
    fun blockable(ids: List<String>): List<String> = ids.map { it.trim() }.filter { it.isNotEmpty() && kind(it) != IdKind.MAC }
}

data class ClientsState(
    val serverId: String = "",
    val persistent: List<PersistentClient> = emptyList(),
    val runtime: List<RuntimeClient> = emptyList(),
    val supportedTags: List<String> = emptyList(),
    val access: AccessList? = null,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: AghError? = null,
)

/** A summary of a client's recent queries, worked out from the newest entries in the query log. */
data class RecentSample(
    val total: Int,
    val blocked: Int,
    val topDomains: List<Pair<String, Int>>,
    val topBlocked: List<Pair<String, Int>>,
)

/** Persistent and runtime clients of the active server, the access list, and changes to them. */
class ClientsRepository(private val sessions: Sessions, scope: CoroutineScope) {
    private val _state = MutableStateFlow(ClientsState())
    val state: StateFlow<ClientsState> = _state.asStateFlow()
    private val writeLock = Mutex()

    init {
        scope.launch {
            sessions.active.map { it?.server?.id }.distinctUntilChanged().collect { id ->
                _state.value = ClientsState(serverId = id.orEmpty())
            }
        }
    }

    private fun api(): AghClient = sessions.active.value?.client ?: throw AghError.Unreachable(null)

    suspend fun load() {
        val id = sessions.active.value?.server?.id ?: return
        _state.update { it.copy(loading = true) }
        try {
            val api = api()
            val clients = api.get("clients", ClientsResponse.serializer())
            val access = api.get("access/list", AccessList.serializer())
            if (sessions.active.value?.server?.id != id) return
            _state.update {
                it.copy(
                    persistent = clients.clients.mapNotNull { raw -> readClient(raw) }.sortedBy { c -> c.name.lowercase() },
                    runtime = clients.autoClients.sortedBy { c -> c.name.ifBlank { c.ip }.lowercase() },
                    supportedTags = clients.supportedTags,
                    access = access,
                    loaded = true,
                    loading = false,
                    error = null,
                )
            }
        } catch (e: AghError) {
            sessions.reportError(id, e)
            _state.update { it.copy(loading = false, error = e) }
        } catch (e: kotlinx.serialization.SerializationException) {
            _state.update { it.copy(loading = false, error = AghError.NotAdGuard()) }
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(loading = false, error = AghError.NotAdGuard()) }
        }
    }

    /**
     * Reads one persistent client. A client sent in a shape AdCtrl doesn't expect is still shown by
     * its name and addresses (and never crashes the app); one without a name is skipped.
     */
    private fun readClient(raw: JsonObject): PersistentClient? {
        val fields = runCatching { AghJson.decodeFromJsonElement(ClientFields.serializer(), raw) }.getOrElse {
            ClientFields(
                name = (raw["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                ids = (raw["ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
            )
        }
        return if (fields.name.isBlank()) null else PersistentClient(fields, raw)
    }

    /** What AdGuard Home knows about these IDs: whether they're blocked and why, and whois. */
    suspend fun find(ids: List<String>): Map<String, ClientFind> {
        val api = api()
        val version = AghVersion.parse(sessions.active.value?.server?.version.orEmpty())
        val wanted = ids.filter { it.isNotBlank() }.take(10)
        if (wanted.isEmpty()) return emptyMap()
        val entries: List<Map<String, ClientFind>> = if (version.clientsSearch) {
            val body = buildJsonObject { put("clients", JsonArray(wanted.map { id -> buildJsonObject { put("id", id) } })) }
            AghJson.decodeFromJsonElement(ListSerializer(MapSerializer(String.serializer(), ClientFind.serializer())), api.postRead("clients/search", body))
        } else {
            api.get("clients/find", ListSerializer(MapSerializer(String.serializer(), ClientFind.serializer())), wanted.mapIndexed { i, id -> "ip$i" to id }.toMap())
        }
        return entries.fold(emptyMap()) { acc, m -> acc + m }
    }

    /** The newest queries of a client (by one of its IDs), summarised. Only kept in memory. */
    suspend fun recent(id: String): RecentSample {
        val page = api().getMulti("querylog", QueryLogPage.serializer(), listOf("limit" to SAMPLE.toString(), "search" to "\"$id\""))
        return summarise(page.data)
    }

    private fun summarise(items: List<QueryLogItem>): RecentSample {
        val blocked = items.filter { it.kind.isBlocked }
        fun top(list: List<QueryLogItem>) = list.groupingBy { it.domain }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
        return RecentSample(items.size, blocked.size, top(items), top(blocked))
    }

    // ---------- Changes ----------

    /** Adds a persistent client. */
    suspend fun add(fields: ClientFields) = write {
        it.post("clients/add", AghJson.encodeToJsonElement(ClientFields.serializer(), fields))
    }

    /**
     * Changes a persistent client. [change] gets the current fields; only the keys AdCtrl models are
     * replaced in the server's own JSON for the client, so every other setting stays exactly as it was.
     */
    suspend fun update(client: PersistentClient, change: (ClientFields) -> ClientFields) = write { api ->
        val updated = change(client.fields)
        val patch = AghJson.encodeToJsonElement(ClientFields.serializer(), updated).jsonObject
        val data = JsonObject(client.raw - OUTPUT_ONLY + patch)
        api.post("clients/update", buildJsonObject { put("name", client.name); put("data", data) })
    }

    suspend fun delete(name: String) = write { it.post("clients/delete", buildJsonObject { put("name", name) }) }

    /**
     * Blocks or unblocks [ids] for all DNS through the access list. Normally they're added to or
     * removed from the disallowed clients; in allowlist mode (only listed clients may use AdGuard
     * Home) they're removed from or added to the allowed clients instead. MAC addresses can't be listed.
     */
    suspend fun setBlocked(ids: List<String>, block: Boolean) = write { api ->
        val usable = ClientIds.blockable(ids)
        if (usable.isEmpty()) return@write
        val access = api.get("access/list", AccessList.serializer())
        val updated = if (access.allowlistMode) {
            access.copy(allowedClients = if (block) access.allowedClients - usable.toSet() else (access.allowedClients + usable).distinct())
        } else {
            access.copy(disallowedClients = if (block) (access.disallowedClients + usable).distinct() else access.disallowedClients - usable.toSet())
        }
        api.post("access/set", AghJson.encodeToJsonElement(AccessList.serializer(), updated))
    }

    /** True when every blockable ID of a client is blocked by an exact entry in the access list. */
    fun isBlocked(ids: List<String>, access: AccessList?): Boolean {
        access ?: return false
        val usable = ClientIds.blockable(ids)
        if (usable.isEmpty()) return false
        return if (access.allowlistMode) usable.none { it in access.allowedClients } else usable.all { it in access.disallowedClients }
    }

    /** Sends a change, then reloads (also after a failure, to show what the server really has). */
    private suspend fun write(block: suspend (AghClient) -> Unit) {
        writeLock.withLock {
            try {
                block(api())
            } finally {
                load()
            }
        }
    }

    private companion object {
        const val SAMPLE = 500

        /** Keys the server sends about a client but doesn't take back. */
        val OUTPUT_ONLY = setOf("disallowed", "disallowed_rule", "whois_info")
    }
}
