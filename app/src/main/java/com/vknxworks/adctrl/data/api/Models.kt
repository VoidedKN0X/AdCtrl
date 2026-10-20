package com.vknxworks.adctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** GET /control/status (ServerStatus in openapi.yaml). Every field has a default so missing ones never crash. */
@Serializable
data class ServerStatus(
    val version: String = "",
    @SerialName("dns_addresses") val dnsAddresses: List<String> = emptyList(),
    @SerialName("dns_port") val dnsPort: Int = 0,
    @SerialName("http_port") val httpPort: Int = 0,
    @SerialName("protection_enabled") val protectionEnabled: Boolean = true,
    /** Milliseconds left in a protection pause; 0 when not paused for a set time. */
    @SerialName("protection_disabled_duration") val protectionDisabledDuration: Long = 0,
    @SerialName("dhcp_available") val dhcpAvailable: Boolean = false,
    val running: Boolean = false,
    val language: String = "",
    /** When the web API server started, Unix time in milliseconds (0.107.70+). */
    @SerialName("start_time") val startTime: Double = 0.0,
)

/**
 * GET /control/stats (Stats in openapi.yaml). The top lists are arrays of one-key objects
 * ({"example.com": 123}); see [TopEntry.parse]. Arrays run oldest first, one value per [timeUnits].
 */
@Serializable
data class Stats(
    @SerialName("time_units") val timeUnits: String = "hours",
    @SerialName("num_dns_queries") val numDnsQueries: Long = 0,
    @SerialName("num_blocked_filtering") val numBlockedFiltering: Long = 0,
    @SerialName("num_replaced_safebrowsing") val numReplacedSafebrowsing: Long = 0,
    @SerialName("num_replaced_safesearch") val numReplacedSafesearch: Long = 0,
    @SerialName("num_replaced_parental") val numReplacedParental: Long = 0,
    /** Seconds. */
    @SerialName("avg_processing_time") val avgProcessingTime: Double = 0.0,
    @SerialName("top_queried_domains") val topQueriedDomains: List<JsonObject> = emptyList(),
    @SerialName("top_clients") val topClients: List<JsonObject> = emptyList(),
    @SerialName("top_blocked_domains") val topBlockedDomains: List<JsonObject> = emptyList(),
    @SerialName("top_upstreams_responses") val topUpstreamsResponses: List<JsonObject> = emptyList(),
    /** Average response time per upstream, in seconds. */
    @SerialName("top_upstreams_avg_time") val topUpstreamsAvgTime: List<JsonObject> = emptyList(),
    @SerialName("dns_queries") val dnsQueries: List<Long> = emptyList(),
    @SerialName("blocked_filtering") val blockedFiltering: List<Long> = emptyList(),
    @SerialName("replaced_safebrowsing") val replacedSafebrowsing: List<Long> = emptyList(),
    @SerialName("replaced_parental") val replacedParental: List<Long> = emptyList(),
) {
    val unitSeconds: Long get() = if (timeUnits == "days") 86_400L else 3_600L
}

/** One row of a top list: a domain, client or upstream and its number. */
data class TopEntry(val key: String, val value: Double) {
    companion object {
        fun parse(list: List<JsonObject>): List<TopEntry> = list.mapNotNull { obj ->
            val (k, v) = obj.entries.firstOrNull() ?: return@mapNotNull null
            val n = (v as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            TopEntry(k, n)
        }
    }
}

/** GET /control/stats/config. [interval] is the retention period in milliseconds. */
@Serializable
data class StatsConfig(
    val enabled: Boolean = true,
    val interval: Long = 0,
)

/** The parts of GET /control/dns_info the Overview shows. */
@Serializable
data class DnsInfo(
    @SerialName("upstream_dns") val upstreamDns: List<String> = emptyList(),
    @SerialName("upstream_mode") val upstreamMode: String = "",
    @SerialName("cache_enabled") val cacheEnabled: Boolean? = null,
    @SerialName("cache_size") val cacheSize: Long = 0,
) {
    /** Upstream servers without comment lines and blank lines. */
    val upstreams: List<String> get() = upstreamDns.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
}

/** The part of GET /control/dhcp/status the Overview shows. */
@Serializable
data class DhcpStatus(val enabled: Boolean = false)

/** POST /control/version.json (VersionInfo). An empty [newVersion] means there's no update to show. */
@Serializable
data class VersionInfo(
    val disabled: Boolean = false,
    @SerialName("new_version") val newVersion: String = "",
    val announcement: String = "",
    @SerialName("announcement_url") val announcementUrl: String = "",
    @SerialName("can_autoupdate") val canAutoupdate: Boolean = false,
)

/** A blocklist or allowlist (Filter in openapi.yaml). */
@Serializable
data class FilterList(
    val id: Long = 0,
    val enabled: Boolean = false,
    val name: String = "",
    val url: String = "",
    @SerialName("rules_count") val rulesCount: Long = 0,
    /** RFC 3339 time of the last successful update; empty if it never downloaded. */
    @SerialName("last_updated") val lastUpdated: String = "",
)

/** GET /control/filtering/status (FilterStatus). [interval] is the update interval in hours (0: never). */
@Serializable
data class FilteringStatus(
    val enabled: Boolean = true,
    val interval: Int = 24,
    val filters: List<FilterList> = emptyList(),
    @SerialName("whitelist_filters") val whitelistFilters: List<FilterList> = emptyList(),
    @SerialName("user_rules") val userRules: List<String> = emptyList(),
)
