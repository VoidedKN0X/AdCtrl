package com.vknxworks.adctrl.data.dns

import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.DnsSettings
import com.vknxworks.adctrl.data.api.RetentionConfig
import com.vknxworks.adctrl.data.api.UpstreamMode
import com.vknxworks.adctrl.data.api.UpstreamSyntax
import com.vknxworks.adctrl.data.api.UpstreamTest
import com.vknxworks.adctrl.data.server.Sessions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The DNS settings AdCtrl edits, as typed: one server per line, comments kept. */
data class DnsEdit(
    val upstreams: List<String>,
    val bootstrap: List<String>,
    val fallback: List<String>,
    val privateRdns: List<String>,
    val usePrivateRdns: Boolean,
    val mode: UpstreamMode,
    val cacheEnabled: Boolean,
    val cacheSize: Long,
    val cacheTtlMin: Long,
    val cacheTtlMax: Long,
    val cacheOptimistic: Boolean,
) {
    companion object {
        fun from(d: DnsSettings) = DnsEdit(
            upstreams = d.upstreamDns,
            bootstrap = d.bootstrapDns,
            fallback = d.fallbackDns,
            privateRdns = d.localPtrUpstreams,
            usePrivateRdns = d.usePrivatePtrResolvers,
            mode = UpstreamMode.from(d.upstreamMode),
            cacheEnabled = d.cacheEnabled ?: (d.cacheSize > 0),
            cacheSize = d.cacheSize,
            cacheTtlMin = d.cacheTtlMin,
            cacheTtlMax = d.cacheTtlMax,
            cacheOptimistic = d.cacheOptimistic,
        )
    }
}

/**
 * Upstream DNS, cache, query log and statistics settings of the active server, and clearing the query log
 * and statistics. Every change goes through the active server's client, so read-only mode and "Confirm
 * changes" apply as for any other change.
 */
class DnsRepository(private val sessions: Sessions) {

    private fun client() = sessions.active.value?.client ?: throw AghError.Unreachable(null)

    private fun version() = AghVersion.parse(sessions.active.value?.server?.version.orEmpty())

    suspend fun load(): DnsSettings = client().get("dns_info", DnsSettings.serializer())

    /** POST /control/dns_config with only the fields AdCtrl edits; everything else stays as it is. */
    suspend fun apply(e: DnsEdit) {
        val v = version()
        val body = buildJsonObject {
            // An empty list is only possible when the server reads its upstreams from a file: leave it as it is.
            if (e.upstreams.isNotEmpty()) putJsonArray("upstream_dns") { e.upstreams.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("bootstrap_dns") { e.bootstrap.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("fallback_dns") { e.fallback.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("local_ptr_upstreams") { e.privateRdns.forEach { add(JsonPrimitive(it)) } }
            put("use_private_ptr_resolvers", e.usePrivateRdns)
            put("upstream_mode", if (e.mode == UpstreamMode.LOAD_BALANCE && !v.loadBalanceMode) "" else e.mode.api)
            if (v.cacheToggle) {
                put("cache_enabled", e.cacheEnabled)
                put("cache_size", e.cacheSize)
            } else {
                // Before 0.107.64 a size of 0 is what turns the cache off.
                put("cache_size", if (e.cacheEnabled) e.cacheSize else 0L)
            }
            put("cache_ttl_min", e.cacheTtlMin)
            put("cache_ttl_max", e.cacheTtlMax)
            put("cache_optimistic", e.cacheOptimistic)
        }
        client().post("dns_config", body)
    }

    /**
     * POST /control/test_upstream_dns with the settings as typed (not yet saved). It only tests, so it
     * works in read-only mode too. Returns each server's result.
     */
    suspend fun test(e: DnsEdit): List<UpstreamTest> {
        val body = buildJsonObject {
            putJsonArray("upstream_dns") { UpstreamSyntax.entries(e.upstreams).forEach { add(JsonPrimitive(it)) } }
            putJsonArray("bootstrap_dns") { UpstreamSyntax.entries(e.bootstrap).forEach { add(JsonPrimitive(it)) } }
            putJsonArray("fallback_dns") { UpstreamSyntax.entries(e.fallback).forEach { add(JsonPrimitive(it)) } }
            if (e.usePrivateRdns) {
                putJsonArray("private_upstream") { UpstreamSyntax.entries(e.privateRdns).forEach { add(JsonPrimitive(it)) } }
            }
        }
        val result = client().postRead("test_upstream_dns", body)
        return (result as? JsonObject)?.let(UpstreamTest::parse).orEmpty()
    }

    suspend fun queryLogConfig(): RetentionConfig = client().get("querylog/config", RetentionConfig.serializer())

    suspend fun statsConfig(): RetentionConfig = client().get("stats/config", RetentionConfig.serializer())

    /**
     * Changes the query log's switch, retention or IP anonymising. The whole configuration is read first
     * and sent back with only these fields changed, so the ignored domains stay exactly as they are.
     */
    suspend fun setQueryLog(enabled: Boolean, intervalMs: Long, anonymize: Boolean) {
        val c = client()
        val current = c.get("querylog/config") as? JsonObject ?: JsonObject(emptyMap())
        c.put("querylog/config/update", merged(current, enabled, intervalMs, anonymize))
    }

    /** Changes the statistics' switch or retention, keeping the ignored domains as they are. */
    suspend fun setStats(enabled: Boolean, intervalMs: Long) {
        val c = client()
        val current = c.get("stats/config") as? JsonObject ?: JsonObject(emptyMap())
        c.put("stats/config/update", merged(current, enabled, intervalMs, null))
    }

    private fun merged(current: JsonObject, enabled: Boolean, intervalMs: Long, anonymize: Boolean?): JsonObject {
        val m = current.toMutableMap()
        m["enabled"] = JsonPrimitive(enabled)
        m["interval"] = JsonPrimitive(intervalMs)
        if (anonymize != null) m["anonymize_client_ip"] = JsonPrimitive(anonymize)
        if (m["ignored"] !is JsonArray) m["ignored"] = JsonArray(emptyList())
        return JsonObject(m)
    }

    /** POST /control/querylog_clear: removes every query log entry on the server. */
    suspend fun clearQueryLog() {
        client().post("querylog_clear")
    }

    /** POST /control/stats_reset: sets every statistic on the server back to zero. */
    suspend fun resetStats() {
        client().post("stats_reset")
    }

    companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS

        /** The retention choices of AdGuard Home's own web interface. */
        val QUERY_LOG_RETENTION = listOf(6 * HOUR_MS, DAY_MS, 7 * DAY_MS, 30 * DAY_MS, 90 * DAY_MS)
        val STATS_RETENTION = listOf(DAY_MS, 7 * DAY_MS, 30 * DAY_MS, 90 * DAY_MS)
    }
}
