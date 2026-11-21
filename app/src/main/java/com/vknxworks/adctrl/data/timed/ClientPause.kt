package com.vknxworks.adctrl.data.timed

/**
 * A temporary pause for one client, kept on the server itself as two lines at the end of the custom
 * rules: a comment with the end time, and the rule from AdGuard Home's documentation that unblocks
 * everything for one client (`@@||*^$client=…`, here with `$important` like AdCtrl's other allow rules):
 *
 *     ! AdCtrl pause until 1763751600000 (21 Nov 21:00), removed automatically
 *     @@||*^$important,client='Kids tablet'
 *
 * Nothing about it is stored on the phone: whether a client is paused, and until when, is read from
 * the rules, and ending a pause means deleting those two lines. Any phone (or the web interface) can
 * see and remove them.
 */
object ClientPause {
    private const val MARK = "! AdCtrl pause until "
    private const val RULE = "@@||*^\$important,client="
    private val PLAIN_CLIENT = Regex("""^[A-Za-z0-9.:_\-/]+$""")

    /** A pause found in the rules: the client as the rule writes it, when it ends, and where its comment line is. */
    data class Entry(val client: String, val untilMs: Long, val index: Int)

    /** An IP, CIDR or ClientID as is; a name with other characters in quotes, with \ ' and , escaped (as RuleBuilder does). */
    fun quote(client: String): String =
        if (PLAIN_CLIENT.matches(client)) client
        else "'" + client.replace("\\", "\\\\").replace("'", "\\'").replace(",", "\\,") + "'"

    fun ruleFor(client: String): String = RULE + quote(client)

    fun markerFor(untilMs: Long, label: String): String = "$MARK$untilMs ($label), removed automatically"

    /** Every pause in [rules]: a comment line with the end time, directly followed by its rule. */
    fun find(rules: List<String>): List<Entry> {
        val out = mutableListOf<Entry>()
        for (i in 0 until rules.size - 1) {
            val line = rules[i].trim()
            if (!line.startsWith(MARK)) continue
            val until = line.removePrefix(MARK).takeWhile { it.isDigit() }.toLongOrNull() ?: continue
            val next = rules[i + 1].trim()
            if (next.startsWith(RULE)) out += Entry(next.removePrefix(RULE), until, i)
        }
        return out
    }

    /** When the pause of [client] ends, or null when it has none. */
    fun untilFor(rules: List<String>, client: String): Long? = find(rules).firstOrNull { it.client == quote(client) }?.untilMs

    private fun without(rules: List<String>, entries: List<Entry>): List<String> {
        if (entries.isEmpty()) return rules
        val drop = entries.flatMap { listOf(it.index, it.index + 1) }.toSet()
        return rules.filterIndexed { i, _ -> i !in drop }
    }

    /** [rules] with a pause for [client] until [untilMs] at the end, replacing an earlier pause of the same client. */
    fun add(rules: List<String>, client: String, untilMs: Long, label: String): List<String> =
        remove(rules, client) + markerFor(untilMs, label) + ruleFor(client)

    /** [rules] without the pause of [client]; the same list when there is none. */
    fun remove(rules: List<String>, client: String): List<String> = without(rules, find(rules).filter { it.client == quote(client) })

    /** [rules] without the pauses that ended at or before [nowMs]; the same list when none did. */
    fun removeExpired(rules: List<String>, nowMs: Long): List<String> = without(rules, find(rules).filter { it.untilMs <= nowMs })

    /** When the next pause in [rules] ends, or null when there is none. */
    fun nextEnd(rules: List<String>): Long? = find(rules).minOfOrNull { it.untilMs }
}

/** The times of a scheduled protection pause, worked out without a clock so they can be tested. */
object PauseTimes {
    const val DAY_MS = 86_400_000L

    /**
     * The next moment at or after [nowMs] when the local time is [minuteOfDay]. [midnightMs] is the
     * start of today in local time (the caller knows the time zone).
     */
    fun nextStart(minuteOfDay: Int, nowMs: Long, midnightMs: Long): Long {
        val today = midnightMs + minuteOfDay * 60_000L
        return if (today >= nowMs) today else today + DAY_MS
    }

    /**
     * How long to pause when the job runs at [nowMs] for a pause planned at [startMs] for [durationMs]:
     * the time that is left of it, or 0 when the planned pause is already over (it is skipped then).
     * A job that runs early (it never should) pauses for the whole duration.
     */
    fun remaining(startMs: Long, durationMs: Long, nowMs: Long): Long = when {
        nowMs <= startMs -> durationMs
        nowMs >= startMs + durationMs -> 0
        else -> startMs + durationMs - nowMs
    }
}
