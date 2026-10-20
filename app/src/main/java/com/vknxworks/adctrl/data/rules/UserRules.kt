package com.vknxworks.adctrl.data.rules

import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.FilteringStatus
import com.vknxworks.adctrl.data.server.Server
import com.vknxworks.adctrl.data.server.Sessions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** How much of a domain a rule covers. */
enum class RuleScope {
    /** Only this exact host: |sub.example.com^ */
    EXACT,

    /** The host and everything under it: ||sub.example.com^ */
    HOST_AND_SUBDOMAINS,

    /** The whole registrable domain and everything under it: ||example.com^ */
    WHOLE_DOMAIN,
}

/**
 * Builds AdGuard Home DNS filtering rules. For DNS requests, AdGuard's urlfilter matches a pattern
 * that doesn't start with "||" or a scheme against the host name itself, so "|host^" matches that
 * exact host only, while "||host^" also matches its subdomains.
 */
object RuleBuilder {

    /** The registrable domain (example.co.uk for a.b.example.co.uk), from the public suffix list bundled with OkHttp. */
    fun registrable(host: String): String =
        "https://$host/".toHttpUrlOrNull()?.topPrivateDomain() ?: host

    /**
     * A blocking rule ("||example.com^") or an allow rule that wins over every blocklist, blocked
     * service, safe browsing and parental control ("@@||example.com^$important"), optionally only
     * for one client ($client=…).
     */
    fun build(block: Boolean, host: String, scope: RuleScope, client: String?): String {
        val h = host.trim().trimEnd('.').lowercase()
        val pattern = when (scope) {
            RuleScope.EXACT -> "|$h^"
            RuleScope.HOST_AND_SUBDOMAINS -> "||$h^"
            RuleScope.WHOLE_DOMAIN -> "||${registrable(h)}^"
        }
        val modifiers = buildList {
            if (!block) add("important")
            if (!client.isNullOrBlank()) add("client=${quoteClient(client)}")
        }
        return (if (block) "" else "@@") + pattern + if (modifiers.isEmpty()) "" else "$" + modifiers.joinToString(",")
    }

    private val PLAIN_CLIENT = Regex("""^[A-Za-z0-9.:_\-/]+$""")

    /** An IP, CIDR or ClientID as is; a name with other characters in quotes, with ' and , escaped. */
    private fun quoteClient(client: String): String =
        if (PLAIN_CLIENT.matches(client)) client
        else "'" + client.replace("\\", "\\\\").replace("'", "\\'").replace(",", "\\,") + "'"
}

/** Result of adding a rule on one or more servers. */
data class RuleResult(val added: List<Server>, val alreadyThere: List<Server>, val failed: List<Pair<Server, AghError>>)

/**
 * Adds rules to AdGuard Home's custom filtering rules (user rules). AdGuard Home replaces the whole
 * list on POST /control/filtering/set_rules, so the current list is read first and kept as it is,
 * with the new rule added at the end.
 */
class UserRules(private val sessions: Sessions) {

    suspend fun add(rule: String, allServers: Boolean): RuleResult {
        val active = sessions.active.value ?: return RuleResult(emptyList(), emptyList(), emptyList())
        val targets = if (allServers) sessions.servers.value.filter { !it.readOnly } else listOf(active.server)
        val added = mutableListOf<Server>()
        val already = mutableListOf<Server>()
        val failed = mutableListOf<Pair<Server, AghError>>()
        targets.forEach { server ->
            val client = if (server.id == active.server.id) active.client else sessions.clientFor(server)
            try {
                if (addTo(client, rule)) added += server else already += server
            } catch (e: AghError) {
                failed += server to e
            }
        }
        return RuleResult(added, already, failed)
    }

    /** True when the rule was added, false when the list already had it. */
    private suspend fun addTo(client: AghClient, rule: String): Boolean {
        val current = client.get("filtering/status", FilteringStatus.serializer()).userRules
        if (current.any { it.trim() == rule }) return false
        val body = buildJsonObject {
            put("rules", JsonArray((current + rule).map { JsonPrimitive(it) }))
        }
        client.post("filtering/set_rules", body)
        return true
    }
}
