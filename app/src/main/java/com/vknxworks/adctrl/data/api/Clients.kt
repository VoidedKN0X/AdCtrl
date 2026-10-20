package com.vknxworks.adctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** GET /control/clients (Clients). Persistent clients are kept as raw JSON too; see [PersistentClient]. */
@Serializable
data class ClientsResponse(
    val clients: List<JsonObject> = emptyList(),
    @SerialName("auto_clients") val autoClients: List<RuntimeClient> = emptyList(),
    @SerialName("supported_tags") val supportedTags: List<String> = emptyList(),
)

/** The fields of a persistent client (Client) that AdCtrl shows and changes. */
@Serializable
data class ClientFields(
    val name: String = "",
    val ids: List<String> = emptyList(),
    @SerialName("use_global_settings") val useGlobalSettings: Boolean = true,
    @SerialName("filtering_enabled") val filteringEnabled: Boolean = false,
    @SerialName("parental_enabled") val parentalEnabled: Boolean = false,
    @SerialName("safebrowsing_enabled") val safeBrowsingEnabled: Boolean = false,
    @SerialName("safe_search") val safeSearch: SafeSearch? = null,
    @SerialName("use_global_blocked_services") val useGlobalBlockedServices: Boolean = true,
    @SerialName("blocked_services") val blockedServices: List<String>? = null,
    val upstreams: List<String>? = null,
    val tags: List<String>? = null,
    @SerialName("ignore_querylog") val ignoreQuerylog: Boolean? = null,
    @SerialName("ignore_statistics") val ignoreStatistics: Boolean? = null,
)

/**
 * A persistent client: [fields] for showing, and [raw] exactly as the server sent it. AdGuard Home
 * rebuilds the whole client from what's sent on update, so changes are made to [raw] (keeping every
 * setting AdCtrl doesn't know about, like the client's schedule or cache) and only the changed keys differ.
 */
data class PersistentClient(val fields: ClientFields, val raw: JsonObject) {
    val name: String get() = fields.name
}

/** A client AdGuard Home found by itself (ClientAuto): from DHCP, ARP, rDNS, /etc/hosts or WHOIS. */
@Serializable
data class RuntimeClient(
    val ip: String = "",
    val name: String = "",
    val source: String = "",
    @SerialName("whois_info") val whois: Whois = Whois(),
)

/** GET /control/access/list and POST /control/access/set (AccessList). */
@Serializable
data class AccessList(
    @SerialName("allowed_clients") val allowedClients: List<String> = emptyList(),
    @SerialName("disallowed_clients") val disallowedClients: List<String> = emptyList(),
    @SerialName("blocked_hosts") val blockedHosts: List<String> = emptyList(),
) {
    /** When any client is listed as allowed, only those can use AdGuard Home and the disallowed list is ignored. */
    val allowlistMode: Boolean get() = allowedClients.isNotEmpty()
}

/** One entry of POST /control/clients/search (ClientFindSubEntry): what AdGuard Home knows about an ID. */
@Serializable
data class ClientFind(
    val name: String = "",
    val ids: List<String> = emptyList(),
    val disallowed: Boolean = false,
    @SerialName("disallowed_rule") val disallowedRule: String = "",
    @SerialName("whois_info") val whois: Whois = Whois(),
)
