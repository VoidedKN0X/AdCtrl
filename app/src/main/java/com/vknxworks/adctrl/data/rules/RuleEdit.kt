package com.vknxworks.adctrl.data.rules

/** One custom rule taken apart, enough to tell whether two rules are each other's opposite. */
data class ParsedRule(
    /** An allow rule (exception, "@@…"). */
    val allow: Boolean,
    /** The pattern without "@@" and modifiers, lower case ("||example.com^"). */
    val pattern: String,
    val important: Boolean,
    /** The $client value as written (quotes included), or null for every client. */
    val client: String?,
    /** Any other modifiers ($dnstype, $denyallow, …): such rules are never treated as simple opposites. */
    val otherModifiers: List<String>,
)

/** What one block or allow changed in a server's custom rules, so it can be undone. */
data class RuleChange(
    /** Lines that were appended. */
    val added: List<String> = emptyList(),
    /** Lines that were removed, with where they were in the list. */
    val removed: List<IndexedValue<String>> = emptyList(),
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()
}

/**
 * Pure edits on AdGuard Home's custom rules list (the "user_rules" of GET /control/filtering/status,
 * written back whole with POST /control/filtering/set_rules). Kept free of Android and network code so
 * it can be unit-tested.
 */
object RuleEdit {

    /**
     * Takes a rule apart. Comments, empty lines, regular-expression rules and /etc/hosts-style lines
     * give null: they're never matched as opposites.
     */
    fun parse(rule: String): ParsedRule? {
        val t = rule.trim()
        if (t.isEmpty() || t.startsWith("!") || t.startsWith("#")) return null
        val allow = t.startsWith("@@")
        val body = if (allow) t.substring(2) else t
        if (body.isEmpty() || body.startsWith("/")) return null
        // A domain pattern can't contain '$', so the first one starts the modifiers (a quoted client name may hold more).
        val dollar = body.indexOf('$')
        val pattern = (if (dollar < 0) body else body.substring(0, dollar)).lowercase()
        // Empty, or an /etc/hosts line ("0.0.0.0 example.com"): not a pattern rule.
        if (pattern.isEmpty() || pattern.any { it.isWhitespace() }) return null
        var important = false
        var client: String? = null
        val other = mutableListOf<String>()
        if (dollar >= 0) {
            splitModifiers(body.substring(dollar + 1)).forEach { m ->
                when {
                    m == "important" -> important = true
                    m.startsWith("client=") -> client = m.removePrefix("client=")
                    m.isNotEmpty() -> other += m
                }
            }
        }
        return ParsedRule(allow, pattern, important, client, other)
    }

    /** Splits "important,client='a\, b'" at the commas that aren't escaped or inside quotes. */
    internal fun splitModifiers(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == '\\' && i + 1 < s.length -> {
                    cur.append(ch).append(s[i + 1])
                    i++
                }
                quote != null -> {
                    cur.append(ch)
                    if (ch == quote) quote = null
                }
                ch == '\'' || ch == '"' -> {
                    cur.append(ch)
                    quote = ch
                }
                ch == ',' -> {
                    out += cur.toString().trim()
                    cur.clear()
                }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString().trim()
        return out.filter { it.isNotEmpty() }
    }

    /**
     * Your custom rules that are the exact opposite of [newRule]: the same pattern for the same client
     * (or both for every client), one blocking and the other allowing. Rules with other modifiers aren't
     * counted, since they don't simply undo each other.
     */
    fun opposites(rules: List<String>, newRule: String): List<String> {
        val n = parse(newRule) ?: return emptyList()
        return rules.filter { r ->
            val p = parse(r) ?: return@filter false
            p.allow != n.allow && p.pattern == n.pattern && p.client == n.client && p.otherModifiers.isEmpty()
        }.map { it.trim() }.distinct()
    }

    /** True when [rules] already has [rule] (ignoring surrounding spaces). */
    fun contains(rules: List<String>, rule: String): Boolean = rules.any { it.trim() == rule.trim() }

    /** [current] with [rule] appended, or null when it's already there. */
    fun append(current: List<String>, rule: String): List<String>? =
        if (contains(current, rule)) null else current + rule.trim()

    /** [current] without every copy of [drop] (compared trimmed), and what was removed from where. */
    fun remove(current: List<String>, drop: Collection<String>): Pair<List<String>, List<IndexedValue<String>>> {
        val set = drop.map { it.trim() }.toSet()
        val removed = current.withIndex().filter { it.value.trim() in set }
        return current.filterNot { it.trim() in set } to removed
    }

    /**
     * Undoes [change] on the list as it is now (other edits made since are kept): each added line is
     * taken out again (its last copy), and each removed line goes back where it was, unless it's
     * already there.
     */
    fun undo(current: List<String>, change: RuleChange): List<String> {
        val list = current.toMutableList()
        change.added.forEach { a ->
            val i = list.indexOfLast { it.trim() == a.trim() }
            if (i >= 0) list.removeAt(i)
        }
        change.removed.sortedBy { it.index }.forEach { (index, line) ->
            if (list.none { it.trim() == line.trim() }) list.add(index.coerceIn(0, list.size), line)
        }
        return list
    }
}
