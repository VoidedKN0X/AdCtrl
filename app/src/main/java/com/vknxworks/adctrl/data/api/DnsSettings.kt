package com.vknxworks.adctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The upstream and cache parts of GET /control/dns_info (DNSConfig in openapi.yaml) that AdCtrl can edit.
 * Lists keep comment lines ("#…"), as AdGuard Home does.
 */
@Serializable
data class DnsSettings(
    @SerialName("upstream_dns") val upstreamDns: List<String> = emptyList(),
    /** A file on the server that replaces [upstreamDns] when set. */
    @SerialName("upstream_dns_file") val upstreamDnsFile: String = "",
    @SerialName("bootstrap_dns") val bootstrapDns: List<String> = emptyList(),
    @SerialName("fallback_dns") val fallbackDns: List<String> = emptyList(),
    @SerialName("local_ptr_upstreams") val localPtrUpstreams: List<String> = emptyList(),
    /** What AdGuard Home uses for private reverse DNS when [localPtrUpstreams] is empty (the router, usually). */
    @SerialName("default_local_ptr_upstreams") val defaultLocalPtrUpstreams: List<String> = emptyList(),
    /** "" (load balancing, the old spelling), "load_balance", "parallel" or "fastest_addr". */
    @SerialName("upstream_mode") val upstreamMode: String = "",
    @SerialName("use_private_ptr_resolvers") val usePrivatePtrResolvers: Boolean = false,
    /** Bytes. */
    @SerialName("cache_size") val cacheSize: Long = 0,
    /** Seconds; 0 means AdGuard Home doesn't change the TTL. */
    @SerialName("cache_ttl_min") val cacheTtlMin: Long = 0,
    @SerialName("cache_ttl_max") val cacheTtlMax: Long = 0,
    @SerialName("cache_optimistic") val cacheOptimistic: Boolean = false,
    /** Only reported by 0.107.64 and newer; older versions turn the cache on with a size above 0. */
    @SerialName("cache_enabled") val cacheEnabled: Boolean? = null,
)

/** How AdGuard Home picks among several upstream servers. [api] is what dns_config takes. */
enum class UpstreamMode(val api: String) {
    LOAD_BALANCE("load_balance"),
    PARALLEL("parallel"),
    FASTEST("fastest_addr");

    companion object {
        fun from(api: String): UpstreamMode = entries.firstOrNull { it.api == api } ?: LOAD_BALANCE
    }
}

/** One server's answer from POST /control/test_upstream_dns: "OK" or AdGuard Home's error text. */
data class UpstreamTest(val server: String, val ok: Boolean, val message: String) {
    companion object {
        fun parse(o: JsonObject): List<UpstreamTest> = o.entries.map { (k, v) ->
            val text = (v as? JsonPrimitive)?.contentOrNull.orEmpty()
            UpstreamTest(k, text.equals("OK", ignoreCase = true), text.take(300))
        }
    }
}

/**
 * Checks the address lines AdCtrl sends as upstream, fallback, bootstrap or private reverse DNS servers,
 * before AdGuard Home does its own (stricter) check. Catches what's clearly wrong: web addresses, unknown
 * schemes, stray characters. Returns the 1-based number of the first bad line, or null.
 */
object UpstreamSyntax {
    private val SCHEMES = setOf("udp", "tcp", "tls", "https", "quic", "h3", "sdns")
    private val PLAIN = Regex("""^(\[[0-9A-Fa-f:.%a-zA-Z0-9]+]|[A-Za-z0-9_.\-:%]+)(:\d{1,5})?$""")
    private val URLISH = Regex("""^[A-Za-z0-9_.\-:%\[\]/@?=&+~!$'()*,;]+$""")

    /** The lines that count: no blank lines, no comments. */
    fun entries(lines: List<String>): List<String> = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    fun lines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * [domains]: "[/example.lan/]…" lines that send some domains to their own server are allowed.
     * [comments]: "#…" lines are allowed (bootstrap servers only take them from 0.107.79).
     */
    fun firstBadLine(text: String, domains: Boolean, comments: Boolean = true): Int? {
        lines(text).forEachIndexed { i, line ->
            if (line.startsWith("#") && !comments) return i + 1
            if (!lineOk(line, domains)) return i + 1
        }
        return null
    }

    private fun lineOk(line: String, domains: Boolean): Boolean {
        if (line.startsWith("#")) return true
        var rest = line
        if (rest.startsWith("[/")) {
            if (!domains) return false
            val end = rest.indexOf("/]")
            if (end < 2) return false
            val names = rest.substring(2, end)
            if (names.isBlank() || names.any { it.isWhitespace() }) return false
            rest = rest.substring(end + 2).trim()
            // "[/example.lan/]#" means "use the default upstreams for these domains".
            if (rest == "#") return true
            if (rest.isEmpty()) return false
        }
        val tokens = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        // Only domain-specific lines may name several servers.
        if (tokens.size > 1 && !line.startsWith("[/")) return false
        return tokens.all(::tokenOk)
    }

    private fun tokenOk(t: String): Boolean {
        val sep = t.indexOf("://")
        if (sep >= 0) {
            val scheme = t.substring(0, sep).lowercase()
            if (scheme !in SCHEMES) return false
            val body = t.substring(sep + 3)
            return body.isNotEmpty() && URLISH.matches(body)
        }
        return PLAIN.matches(t)
    }
}

/** GET /control/stats/config and /control/querylog/config: the parts AdCtrl shows. [interval] is in ms. */
@Serializable
data class RetentionConfig(
    val enabled: Boolean = true,
    val interval: Long = 0,
    @SerialName("anonymize_client_ip") val anonymizeClientIp: Boolean = false,
)
