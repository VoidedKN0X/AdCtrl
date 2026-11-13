package com.vknxworks.adctrl.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /control/blocked_services/all (BlockedServicesAll). */
@Serializable
data class BlockedServicesAll(
    @SerialName("blocked_services") val services: List<BlockedService> = emptyList(),
    val groups: List<ServiceGroup> = emptyList(),
)

/** A service AdGuard Home can block (its icon is left out: AdCtrl shows a letter instead of rendering SVG). */
@Serializable
data class BlockedService(
    val id: String = "",
    val name: String = "",
    @SerialName("group_id") val groupId: String = "",
)

@Serializable
data class ServiceGroup(val id: String = "")

/**
 * GET /control/blocked_services/get and PUT /control/blocked_services/update (BlockedServicesSchedule).
 * [schedule] holds the hours when blocking these services is paused.
 */
@Serializable
data class BlockedServicesConfig(
    val ids: List<String> = emptyList(),
    val schedule: PauseSchedule = PauseSchedule(),
)

/**
 * When blocked services are not blocked, per weekday (Schedule). Times are milliseconds since
 * midnight in [timeZone] ("Local" is the server's own time zone). A missing day has no pause.
 */
@Serializable
data class PauseSchedule(
    @SerialName("time_zone") val timeZone: String = "Local",
    val sun: DayRange? = null,
    val mon: DayRange? = null,
    val tue: DayRange? = null,
    val wed: DayRange? = null,
    val thu: DayRange? = null,
    val fri: DayRange? = null,
    val sat: DayRange? = null,
) {
    /** Monday first, as the days are shown. */
    fun days(): List<Pair<Weekday, DayRange?>> = listOf(
        Weekday.MON to mon, Weekday.TUE to tue, Weekday.WED to wed, Weekday.THU to thu,
        Weekday.FRI to fri, Weekday.SAT to sat, Weekday.SUN to sun,
    )

    fun with(day: Weekday, range: DayRange?): PauseSchedule = when (day) {
        Weekday.MON -> copy(mon = range)
        Weekday.TUE -> copy(tue = range)
        Weekday.WED -> copy(wed = range)
        Weekday.THU -> copy(thu = range)
        Weekday.FRI -> copy(fri = range)
        Weekday.SAT -> copy(sat = range)
        Weekday.SUN -> copy(sun = range)
    }

    val isEmpty: Boolean get() = days().all { (_, r) -> r == null || r.isEmpty }
}

enum class Weekday { MON, TUE, WED, THU, FRI, SAT, SUN }

/** Milliseconds since midnight; end may be 24 h (86 400 000). */
@Serializable
data class DayRange(val start: Long = 0, val end: Long = 0) {
    val isEmpty: Boolean get() = end <= start
}

/** A DNS rewrite (RewriteEntry): [domain] (wildcards like *.example.com allowed) answered with [answer]. */
@Serializable
data class Rewrite(
    val domain: String = "",
    /** An IPv4 or IPv6 address, a domain (CNAME), or "A" / "AAAA" to keep the upstream's answer. */
    val answer: String = "",
    /** 0.107.68+; older servers don't send it, and every rewrite is on. */
    val enabled: Boolean = true,
) {
    val key: String get() = "$domain\u0000$answer"
}

/** GET /control/rewrite/settings (0.107.68+). */
@Serializable
data class RewriteSettings(val enabled: Boolean = true)

/** GET /control/safebrowsing/status and /control/parental/status. */
@Serializable
data class EnabledStatus(val enabled: Boolean = false)

/** GET /control/safesearch/status and PUT /control/safesearch/settings (SafeSearchConfig). */
@Serializable
data class SafeSearch(
    val enabled: Boolean = false,
    val bing: Boolean = true,
    val duckduckgo: Boolean = true,
    val ecosia: Boolean = true,
    val google: Boolean = true,
    val pixabay: Boolean = true,
    val yandex: Boolean = true,
    val youtube: Boolean = true,
)

/** POST /control/filtering/refresh. */
@Serializable
data class RefreshResult(val updated: Int = 0)
