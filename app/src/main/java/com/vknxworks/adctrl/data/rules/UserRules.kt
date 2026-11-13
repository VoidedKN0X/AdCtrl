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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.IDN
import java.util.Locale

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

    private val LABEL = Regex("^[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?$")

    /** The registrable domain (example.co.uk for a.b.example.co.uk), from the public suffix list bundled with OkHttp. */
    fun registrable(host: String): String {
        val h = normalizeHost(host) ?: return host
        return "https://$h/".toHttpUrlOrNull()?.topPrivateDomain() ?: h
    }

    /** A wildcard name ("*.example.com", as typed or from a wildcard query): it can only be blocked with its subdomains. */
    fun isWildcard(host: String): Boolean = host.trim().startsWith("*.")

    /**
     * [input] as it goes into a rule: lower case, without a trailing dot, international names in
     * punycode (münchen.de → xn--mnchen-3ya.de), and a leading "*." dropped (the rule then covers the
     * domain and its subdomains). Null when it isn't a host name a rule can safely be made from, for
     * example when it contains rule syntax such as | ^ $ / * or spaces anywhere else.
     */
    fun normalizeHost(input: String): String? {
        var h = input.trim().trimEnd('.')
        while (h.startsWith("*.")) h = h.substring(2)
        if (h.isEmpty() || h.length > 253) return null
        val ascii = try {
            IDN.toASCII(h, IDN.ALLOW_UNASSIGNED)
        } catch (_: IllegalArgumentException) {
            return null
        }.lowercase(Locale.ROOT)
        if (ascii.isEmpty() || ascii.length > 253) return null
        if (ascii.split('.').any { !LABEL.matches(it) }) return null
        return ascii
    }

    /**
     * A blocking rule ("||example.com^") or an allow rule that wins over every blocklist, blocked
     * service, safe browsing and parental control ("@@||example.com^$important"), optionally only
     * for one client ($client=…). Null when [host] can't be made into a rule (see [normalizeHost]).
     */
    fun build(block: Boolean, host: String, scope: RuleScope, client: String?): String? {
        val h = normalizeHost(host) ?: return null
        val pattern = when {
            scope == RuleScope.WHOLE_DOMAIN -> "||${registrable(h)}^"
            scope == RuleScope.EXACT && !isWildcard(host) -> "|$h^"
            else -> "||$h^"
        }
        val modifiers = buildList {
            if (!block) add("important")
            client?.trim()?.takeIf { it.isNotEmpty() }?.let { add("client=${quoteClient(it)}") }
        }
        return (if (block) "" else "@@") + pattern + if (modifiers.isEmpty()) "" else "$" + modifiers.joinToString(",")
    }

    private val PLAIN_CLIENT = Regex("""^[A-Za-z0-9.:_\-/]+$""")

    /** An IP, CIDR or ClientID as is; a name with other characters in quotes, with \ ' and , escaped. */
    fun quoteClient(client: String): String =
        if (PLAIN_CLIENT.matches(client)) client
        else "'" + client.replace("\\", "\\\\").replace("'", "\\'").replace(",", "\\,") + "'"

    /** An allow rule (exception) rather than a blocking rule. */
    fun isAllowRule(rule: String): Boolean = rule.trimStart().startsWith("@@")
}

/** The request bodies AdCtrl sends for custom rules, kept here so they can be unit-tested. */
object RulesApi {
    /** POST /control/filtering/set_rules: the whole list, as AdGuard Home replaces it. */
    fun setRulesBody(rules: List<String>): JsonObject =
        buildJsonObject { put("rules", JsonArray(rules.map { JsonPrimitive(it) })) }
}

/** What was done on one server. */
enum class RuleOutcome {
    /** One of your custom rules caused the opposite and was removed; nothing had to be added. */
    REMOVED,

    /** The new rule was added (nothing of yours was in the way, or you kept it). */
    ADDED,

    /** Your opposing rule was removed, and the new rule was still needed (a blocklist also matched). */
    REMOVED_AND_ADDED,

    /** It was already the way you asked: nothing changed. */
    ALREADY,
}

data class RuleResult(
    val done: List<Pair<Server, RuleOutcome>>,
    val failed: List<Pair<Server, AghError>>,
    /** What changed on each server (by id), for Undo. */
    val changes: Map<String, RuleChange> = emptyMap(),
) {
    /** The single outcome when every server ended the same way, for the message. */
    val outcome: RuleOutcome? get() = done.map { it.second }.distinct().singleOrNull()

    /** Something changed that Undo can put back. */
    val canUndo: Boolean get() = changes.values.any { !it.isEmpty }
}

/**
 * Blocks and allows domains through AdGuard Home's custom filtering rules (user rules), keeping
 * them tidy: before adding anything, your own custom rules that cause the opposite (a block rule
 * when allowing, an allow rule when blocking) can be removed. A new rule is then only added when the
 * domain still isn't the way you asked, for example because a blocklist also blocks it.
 *
 * AdGuard Home replaces the whole list on POST /control/filtering/set_rules, so the current list is
 * always read first and kept as it is apart from the change. It applies the new rules in the
 * background, so the result is checked again until the change is visible.
 */
class UserRules(private val sessions: Sessions) {

    /**
     * Your custom rules on the active server that currently make [host] the opposite of [block]
     * (what AdGuard Home's check reports, plus exact opposites of [newRule] in the list): shown in
     * the dialog as the lines that can be removed.
     */
    suspend fun opposing(block: Boolean, host: String, client: String?, newRule: String?): List<String> {
        val active = sessions.active.value ?: return emptyList()
        return opposingOn(active.client, active.server, block, host, client, newRule, check(active.client, active.server, host, client))
    }

    /**
     * Blocks ([block]) or allows [host] on the active server, or on every writable server with
     * [allServers]. With [removeOpposing], your opposing custom rules are removed first.
     */
    suspend fun apply(block: Boolean, host: String, client: String?, newRule: String, allServers: Boolean, removeOpposing: Boolean = true): RuleResult {
        val active = sessions.active.value ?: return RuleResult(emptyList(), emptyList())
        val targets = if (allServers) sessions.servers.value.filter { !it.readOnly } else listOf(active.server)
        val done = mutableListOf<Pair<Server, RuleOutcome>>()
        val failed = mutableListOf<Pair<Server, AghError>>()
        val changes = mutableMapOf<String, RuleChange>()
        targets.forEach { server ->
            val api = if (server.id == active.server.id) active.client else sessions.clientFor(server)
            try {
                val (outcome, change) = applyOn(api, server, block, host, client, newRule, removeOpposing)
                done += server to outcome
                changes[server.id] = change
            } catch (e: AghError) {
                failed += server to e
            }
        }
        return RuleResult(done, failed, changes)
    }

    /** Puts back what [result] changed, on each server it changed. Returns the servers where that failed. */
    suspend fun undo(result: RuleResult): List<Pair<Server, AghError>> {
        val active = sessions.active.value
        val failed = mutableListOf<Pair<Server, AghError>>()
        result.done.forEach { (server, _) ->
            val change = result.changes[server.id]?.takeIf { !it.isEmpty } ?: return@forEach
            val api = if (server.id == active?.server?.id) active.client else sessions.clientFor(server)
            try {
                val current = rules(api)
                val restored = RuleEdit.undo(current, change)
                if (restored != current) setRules(api, restored)
            } catch (e: AghError) {
                failed += server to e
            }
        }
        return failed
    }

    private suspend fun applyOn(
        api: AghClient,
        server: Server,
        block: Boolean,
        host: String,
        client: String?,
        newRule: String,
        removeOpposing: Boolean,
    ): Pair<RuleOutcome, RuleChange> {
        val before = check(api, server, host, client)
        val remove = if (removeOpposing) opposingOn(api, server, block, host, client, newRule, before) else emptyList()
        if (remove.isEmpty()) {
            if (removeOpposing && isAsWanted(block, before)) return RuleOutcome.ALREADY to RuleChange()
            return addRule(api, newRule)?.let { RuleOutcome.ADDED to it } ?: (RuleOutcome.ALREADY to RuleChange())
        }

        // Remove your own opposing rules (every copy), keeping everything else as it was.
        val (kept, removed) = RuleEdit.remove(rules(api), remove)
        setRules(api, kept)
        val drop = remove.map { it.trim() }.toSet()

        // AdGuard Home reloads the rules in the background: wait until the removed rules no longer match.
        repeat(RELOAD_CHECKS) {
            delay(RELOAD_WAIT_MS)
            val after = check(api, server, host, client)
            if (after.rules.none { it.filterListId == 0L && it.text.trim() in drop }) {
                return if (isAsWanted(block, after)) {
                    RuleOutcome.REMOVED to RuleChange(removed = removed)
                } else {
                    val added = addRule(api, newRule)
                    RuleOutcome.REMOVED_AND_ADDED to RuleChange(added = added?.added.orEmpty(), removed = removed)
                }
            }
        }
        // Still reloading after a few seconds: the removal is saved; don't guess about adding.
        return RuleOutcome.REMOVED to RuleChange(removed = removed)
    }

    /** Your custom rules working against [block]: those AdGuard Home reports for [result], and exact opposites of [newRule]. */
    private suspend fun opposingOn(
        api: AghClient,
        server: Server,
        block: Boolean,
        host: String,
        client: String?,
        newRule: String?,
        result: CheckHostResult,
    ): List<String> {
        val reported = result.rules
            .filter { it.filterListId == 0L && it.text.isNotBlank() }
            .map { it.text.trim() }
            .filter { if (block) RuleBuilder.isAllowRule(it) else !RuleBuilder.isAllowRule(it) }
        val textual = if (newRule != null) RuleEdit.opposites(rules(api), newRule) else emptyList()
        return (reported + textual).distinct()
    }

    private fun isAsWanted(block: Boolean, r: CheckHostResult): Boolean = if (block) r.kind.isBlocked else !r.kind.isBlocked

    private suspend fun check(api: AghClient, server: Server, host: String, client: String?): CheckHostResult {
        val version = AghVersion.parse(server.version)
        val name = RuleBuilder.normalizeHost(host) ?: host.trim().trimEnd('.')
        val query = buildMap {
            put("name", name)
            if (version.checkHostClient) client?.takeIf { it.isNotBlank() }?.let { put("client", it.trim()) }
        }
        return api.get("filtering/check_host", CheckHostResult.serializer(), query)
    }

    private suspend fun rules(api: AghClient): List<String> =
        api.get("filtering/status", FilteringStatus.serializer()).userRules

    private suspend fun setRules(api: AghClient, rules: List<String>) {
        api.post("filtering/set_rules", RulesApi.setRulesBody(rules))
    }

    /** The change when the rule was appended, null when the list already had it. */
    private suspend fun addRule(api: AghClient, rule: String): RuleChange? {
        val updated = RuleEdit.append(rules(api), rule) ?: return null
        setRules(api, updated)
        return RuleChange(added = listOf(rule.trim()))
    }

    private companion object {
        const val RELOAD_CHECKS = 10
        const val RELOAD_WAIT_MS = 400L
    }
}
