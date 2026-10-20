package com.vknxworks.adctrl.data.rules

import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.CheckHostResult
import com.vknxworks.adctrl.data.api.FilteringStatus
import com.vknxworks.adctrl.data.server.Server
import com.vknxworks.adctrl.data.server.Sessions
import kotlinx.coroutines.delay
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

    /** An allow rule (exception) rather than a blocking rule. */
    fun isAllowRule(rule: String): Boolean = rule.trimStart().startsWith("@@")
}

/** What was done on one server. */
enum class RuleOutcome {
    /** One of your custom rules caused the opposite and was removed; nothing had to be added. */
    REMOVED,

    /** The new rule was added (nothing of yours was in the way). */
    ADDED,

    /** Your opposing rule was removed, and the new rule was still needed (a blocklist also matched). */
    REMOVED_AND_ADDED,

    /** It was already the way you asked: nothing changed. */
    ALREADY,
}

data class RuleResult(
    val done: List<Pair<Server, RuleOutcome>>,
    val failed: List<Pair<Server, AghError>>,
) {
    /** The single outcome when every server ended the same way, for the message. */
    val outcome: RuleOutcome? get() = done.map { it.second }.distinct().singleOrNull()
}

/**
 * Blocks and unblocks domains through AdGuard Home's custom filtering rules (user rules), keeping
 * them tidy: before adding anything, any of your own custom rules that cause the opposite (a block
 * rule when unblocking, an allow rule when blocking) are removed. A new rule is only added when the
 * domain still isn't the way you asked afterwards, for example because a blocklist also blocks it.
 *
 * AdGuard Home replaces the whole list on POST /control/filtering/set_rules, so the current list is
 * always read first and kept as it is apart from the change. It applies the new rules in the
 * background, so the result is checked again until the change is visible.
 */
class UserRules(private val sessions: Sessions) {

    /**
     * Your custom rules on the active server that currently make [host] the opposite of [block]:
     * shown in the dialog as the lines that will be removed.
     */
    suspend fun opposing(block: Boolean, host: String, client: String?): List<String> {
        val active = sessions.active.value ?: return emptyList()
        return opposing(block, check(active.client, active.server, host, client))
    }

    /** Blocks ([block]) or unblocks [host] on the active server, or on every writable server with [allServers]. */
    suspend fun apply(block: Boolean, host: String, client: String?, newRule: String, allServers: Boolean): RuleResult {
        val active = sessions.active.value ?: return RuleResult(emptyList(), emptyList())
        val targets = if (allServers) sessions.servers.value.filter { !it.readOnly } else listOf(active.server)
        val done = mutableListOf<Pair<Server, RuleOutcome>>()
        val failed = mutableListOf<Pair<Server, AghError>>()
        targets.forEach { server ->
            val api = if (server.id == active.server.id) active.client else sessions.clientFor(server)
            try {
                done += server to applyOn(api, server, block, host, client, newRule)
            } catch (e: AghError) {
                failed += server to e
            }
        }
        return RuleResult(done, failed)
    }

    private suspend fun applyOn(api: AghClient, server: Server, block: Boolean, host: String, client: String?, newRule: String): RuleOutcome {
        val before = check(api, server, host, client)
        val remove = opposing(block, before)
        if (remove.isEmpty()) {
            if (isAsWanted(block, before)) return RuleOutcome.ALREADY
            return if (addRule(api, newRule)) RuleOutcome.ADDED else RuleOutcome.ALREADY
        }

        // Remove your own opposing rules (every copy), keeping everything else as it was.
        val current = rules(api)
        val drop = remove.map { it.trim() }.toSet()
        setRules(api, current.filterNot { it.trim() in drop })

        // AdGuard Home reloads the rules in the background: wait until the removed rules no longer match.
        var after = before
        repeat(RELOAD_CHECKS) {
            delay(RELOAD_WAIT_MS)
            after = check(api, server, host, client)
            if (after.rules.none { it.filterListId == 0L && it.text.trim() in drop }) {
                return if (isAsWanted(block, after)) {
                    RuleOutcome.REMOVED
                } else {
                    addRule(api, newRule)
                    RuleOutcome.REMOVED_AND_ADDED
                }
            }
        }
        // Still reloading after a few seconds: the removal is saved; don't guess about adding.
        return RuleOutcome.REMOVED
    }

    /** Custom rules (list 0) in [result] that work against [block]. */
    private fun opposing(block: Boolean, result: CheckHostResult): List<String> =
        result.rules
            .filter { it.filterListId == 0L && it.text.isNotBlank() }
            .map { it.text.trim() }
            .filter { if (block) RuleBuilder.isAllowRule(it) else !RuleBuilder.isAllowRule(it) }
            .distinct()

    private fun isAsWanted(block: Boolean, r: CheckHostResult): Boolean = if (block) r.kind.isBlocked else !r.kind.isBlocked

    private suspend fun check(api: AghClient, server: Server, host: String, client: String?): CheckHostResult {
        val version = AghVersion.parse(server.version)
        val query = buildMap {
            put("name", host.trim().trimEnd('.'))
            if (version.checkHostClient) client?.takeIf { it.isNotBlank() }?.let { put("client", it.trim()) }
        }
        return api.get("filtering/check_host", CheckHostResult.serializer(), query)
    }

    private suspend fun rules(api: AghClient): List<String> =
        api.get("filtering/status", FilteringStatus.serializer()).userRules

    private suspend fun setRules(api: AghClient, rules: List<String>) {
        api.post("filtering/set_rules", buildJsonObject { put("rules", JsonArray(rules.map { JsonPrimitive(it) })) })
    }

    /** True when the rule was added, false when the list already had it. */
    private suspend fun addRule(api: AghClient, rule: String): Boolean {
        val current = rules(api)
        if (current.any { it.trim() == rule }) return false
        setRules(api, current + rule)
        return true
    }

    private companion object {
        const val RELOAD_CHECKS = 10
        const val RELOAD_WAIT_MS = 400L
    }
}
