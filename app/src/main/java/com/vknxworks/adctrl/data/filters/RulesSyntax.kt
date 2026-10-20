package com.vknxworks.adctrl.data.filters

/** What a custom-rules line is, for highlighting. */
enum class LineKind { BLANK, COMMENT, BLOCK, ALLOW, HOSTS, REGEX }

/** A likely mistake on one line (1-based [line]). */
data class RuleHint(val line: Int, val kind: HintKind, val text: String)

enum class HintKind {
    /** "https://example.com": DNS rules see host names only. */
    HAS_SCHEME,

    /** "example.com/ads": paths can't be matched by DNS filtering. */
    HAS_PATH,

    /** "*.example.com": write ||example.com^ to cover subdomains. */
    LEADING_WILDCARD,

    /** "||example.com" without "^" also matches example.com.evil.net. */
    MISSING_SEPARATOR,

    /** "example.com##.ad": cosmetic rules only work in browser blockers. */
    COSMETIC,

    /** "@@" alone, or a rule that's only modifiers. */
    EMPTY,

    /** The same line appears more than once. */
    DUPLICATE,

    /** Spaces inside an adblock-style rule. */
    SPACES,
}

/** Small, conservative checks for the mistakes people commonly make in AdGuard Home's custom rules. */
object RulesSyntax {

    private val HOSTS_LINE = Regex("""^\s*(\d{1,3}(\.\d{1,3}){3}|[0-9a-fA-F:]+:[0-9a-fA-F:.]*)\s+\S""")

    fun kind(line: String): LineKind {
        val t = line.trim()
        return when {
            t.isEmpty() -> LineKind.BLANK
            t.startsWith("#") || t.startsWith("!") -> LineKind.COMMENT
            t.startsWith("@@") -> LineKind.ALLOW
            t.startsWith("/") && t.length > 1 && t.substringBefore('$').endsWith("/") -> LineKind.REGEX
            HOSTS_LINE.containsMatchIn(t) -> LineKind.HOSTS
            else -> LineKind.BLOCK
        }
    }

    /** Hints for [lines], at most one per line (the most useful), plus duplicates. */
    fun hints(lines: List<String>): List<RuleHint> {
        val out = mutableListOf<RuleHint>()
        val seen = HashMap<String, Int>()
        lines.forEachIndexed { i, raw ->
            val n = i + 1
            val t = raw.trim()
            val kind = kind(raw)
            if (kind == LineKind.BLANK || kind == LineKind.COMMENT) return@forEachIndexed
            seen[t]?.let { first -> out += RuleHint(n, HintKind.DUPLICATE, first.toString()) } ?: run { seen[t] = n }
            if (kind == LineKind.HOSTS || kind == LineKind.REGEX) return@forEachIndexed
            val body = t.removePrefix("@@")
            val pattern = body.substringBefore('$')
            val hint = when {
                body.isBlank() || pattern.isBlank() && !body.startsWith("$") -> HintKind.EMPTY
                pattern.contains("##") || pattern.contains("#@#") || pattern.contains("#$#") || pattern.contains("#?#") -> HintKind.COSMETIC
                pattern.startsWith("http://") || pattern.startsWith("https://") || pattern.contains("://") -> HintKind.HAS_SCHEME
                pattern.startsWith("*.") -> HintKind.LEADING_WILDCARD
                pattern.contains(' ') -> HintKind.SPACES
                hasPath(pattern) -> HintKind.HAS_PATH
                pattern.startsWith("||") && !pattern.endsWith("^") && !pattern.endsWith("|") && !pattern.endsWith("*") -> HintKind.MISSING_SEPARATOR
                else -> null
            }
            if (hint != null) out += RuleHint(n, hint, pattern)
        }
        return out.sortedBy { it.line }
    }

    /** A "/" after the host part, other than a trailing "^/"-style separator. */
    private fun hasPath(pattern: String): Boolean {
        val p = pattern.trimStart('|')
        val slash = p.indexOf('/')
        return slash in 1 until p.length - 1
    }

    /** A suggested fix for a hint, when there's an obvious one. */
    fun suggestion(h: RuleHint): String? {
        val p = h.text
        fun host(s: String) = s.substringAfter("://").trimStart('|').substringBefore('/').substringBefore('^').removePrefix("*.").trim('.')
        return when (h.kind) {
            HintKind.HAS_SCHEME, HintKind.HAS_PATH, HintKind.LEADING_WILDCARD -> host(p).takeIf { it.isNotBlank() }?.let { "||$it^" }
            HintKind.MISSING_SEPARATOR -> "$p^"
            else -> null
        }
    }

    /** Lines removed and added between [old] and [new], in order (a longest-common-subsequence diff). */
    fun diff(old: List<String>, new: List<String>): List<DiffLine> {
        val n = old.size
        val m = new.size
        // Rule lists can be long; fall back to a simple multiset diff past a size that keeps the table small.
        if (n.toLong() * m > 4_000_000L) {
            val left = new.groupingBy { it }.eachCount().toMutableMap()
            val removed = old.filter { l -> (left[l] ?: 0).let { c -> if (c > 0) { left[l] = c - 1; false } else true } }
            val right = old.groupingBy { it }.eachCount().toMutableMap()
            val added = new.filter { l -> (right[l] ?: 0).let { c -> if (c > 0) { right[l] = c - 1; false } else true } }
            return removed.map { DiffLine(false, it) } + added.map { DiffLine(true, it) }
        }
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            lcs[i][j] = if (old[i] == new[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val out = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                old[i] == new[j] -> { i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> out += DiffLine(false, old[i++])
                else -> out += DiffLine(true, new[j++])
            }
        }
        while (i < n) out += DiffLine(false, old[i++])
        while (j < m) out += DiffLine(true, new[j++])
        return out
    }

    /** The text as AdGuard Home stores it: one rule per line, trailing blank lines dropped. */
    fun lines(text: String): List<String> = text.replace("\r\n", "\n").split('\n').dropLastWhile { it.isBlank() }
}

data class DiffLine(val added: Boolean, val text: String)
