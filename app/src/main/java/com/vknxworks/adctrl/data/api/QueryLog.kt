package com.vknxworks.adctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /control/querylog (QueryLog). [oldest] is the time to pass as older_than for the next page. */
@Serializable
data class QueryLogPage(
    val oldest: String = "",
    val data: List<QueryLogItem> = emptyList(),
)

/** One DNS query (QueryLogItem). Kept in memory only, never written to the phone's storage. */
@Serializable
data class QueryLogItem(
    val answer: List<DnsAnswer> = emptyList(),
    @SerialName("original_answer") val originalAnswer: List<DnsAnswer> = emptyList(),
    val cached: Boolean = false,
    val upstream: String = "",
    @SerialName("answer_dnssec") val answerDnssec: Boolean = false,
    /** The client's IP address (may be anonymised). */
    val client: String = "",
    @SerialName("client_id") val clientId: String = "",
    @SerialName("client_info") val clientInfo: QueryClientInfo? = null,
    @SerialName("client_proto") val clientProto: String = "",
    val ecs: String = "",
    /** Milliseconds, as a decimal string. */
    val elapsedMs: String = "",
    val question: DnsQuestion = DnsQuestion(),
    /** A FilteringReason such as FilteredBlackList or NotFilteredNotFound. */
    val reason: String = "",
    val rules: List<ResultRule> = emptyList(),
    @SerialName("service_name") val serviceName: String = "",
    /** The response code, like NOERROR or NXDOMAIN. */
    val status: String = "",
    /** RFC 3339 with nanoseconds. */
    val time: String = "",
) {
    /** The domain as people read it (Unicode for internationalised names). */
    val domain: String get() = question.unicodeName.ifBlank { question.name }.trimEnd('.')

    /** The ASCII (punycode) form, which is what filtering rules match. */
    val asciiDomain: String get() = question.name.ifBlank { question.unicodeName }.trimEnd('.').lowercase()

    /** The client's name when AdGuard Home knows one, else its IP or ClientID. */
    val clientLabel: String get() = clientInfo?.name?.takeIf { it.isNotBlank() } ?: client.ifBlank { clientId }

    val elapsed: Double? get() = elapsedMs.toDoubleOrNull()

    /** Stable enough to key a list row: time to the nanosecond plus what was asked by whom. */
    val key: String get() = "$time|$client|${question.name}|${question.type}"

    val kind: QueryKind get() = QueryKind.of(reason)
}

@Serializable
data class DnsAnswer(val type: String = "", val value: String = "", val ttl: Long = 0)

@Serializable
data class DnsQuestion(
    @SerialName("class") val klass: String = "",
    val name: String = "",
    @SerialName("unicode_name") val unicodeName: String = "",
    val type: String = "",
)

@Serializable
data class QueryClientInfo(
    val name: String = "",
    val disallowed: Boolean = false,
    @SerialName("disallowed_rule") val disallowedRule: String = "",
    val whois: Whois = Whois(),
)

@Serializable
data class Whois(val city: String = "", val country: String = "", val orgname: String = "")

/** A rule that matched, and the list it came from (0: custom rules; see [FilterNames]). */
@Serializable
data class ResultRule(
    @SerialName("filter_list_id") val filterListId: Long = 0,
    val text: String = "",
)

/** GET /control/filtering/check_host (FilterCheckHostResponse). */
@Serializable
data class CheckHostResult(
    val reason: String = "",
    val rules: List<ResultRule> = emptyList(),
    @SerialName("service_name") val serviceName: String = "",
    val cname: String = "",
    @SerialName("ip_addrs") val ipAddrs: List<String> = emptyList(),
) {
    val kind: QueryKind get() = QueryKind.of(reason)
}

/** What happened to a query, grouped the way the app shows it (from FilteringReason). */
enum class QueryKind {
    BLOCKED,
    BLOCKED_SERVICE,
    SAFE_BROWSING,
    PARENTAL,
    SAFE_SEARCH,
    ALLOWED,
    REWRITTEN,
    PROCESSED,
    ERROR,
    ;

    /** Blocked in a way an allow rule (@@…$important) undoes. */
    val isBlocked: Boolean get() = this == BLOCKED || this == BLOCKED_SERVICE || this == SAFE_BROWSING || this == PARENTAL

    companion object {
        fun of(reason: String): QueryKind = when (reason) {
            "FilteredBlackList", "FilteredInvalid" -> BLOCKED
            "FilteredBlockedService" -> BLOCKED_SERVICE
            "FilteredSafeBrowsing" -> SAFE_BROWSING
            "FilteredParental" -> PARENTAL
            "FilteredSafeSearch" -> SAFE_SEARCH
            "NotFilteredWhiteList", "NotFilteredAllowList" -> ALLOWED
            "Rewrite", "RewriteEtcHosts", "RewriteRule", "Rewritten", "RewrittenAutoHosts", "RewrittenRule" -> REWRITTEN
            "NotFilteredError" -> ERROR
            else -> PROCESSED
        }
    }
}

/** The status filter of the Queries tab, with the "reason" values (0.107.77+) and the older "response_status". */
enum class QueryFilter(val reasons: List<String>, val responseStatus: String?) {
    ALL(emptyList(), null),
    BLOCKED(listOf("FilteredBlackList", "FilteredBlockedService", "FilteredInvalid"), "blocked"),
    ALLOWED(listOf("NotFilteredWhiteList"), "whitelisted"),
    PROCESSED(listOf("NotFilteredNotFound"), "processed"),
    REWRITTEN(listOf("Rewrite", "RewriteEtcHosts", "RewriteRule"), "rewritten"),
    SAFE_BROWSING(listOf("FilteredSafeBrowsing"), "blocked_safebrowsing"),
    PARENTAL(listOf("FilteredParental"), "blocked_parental"),
}
