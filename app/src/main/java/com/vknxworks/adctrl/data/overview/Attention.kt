package com.vknxworks.adctrl.data.overview

import com.vknxworks.adctrl.data.api.FilterList
import java.time.OffsetDateTime

enum class Severity { CRITICAL, WARNING, INFO }

/** Something on the Overview that needs a look, most important first. */
sealed class AttentionItem(val severity: Severity) {
    /** Protection is off until someone turns it back on. */
    data object ProtectionOff : AttentionItem(Severity.CRITICAL)

    /** Protection is paused and comes back on by itself at [until]. */
    data class Paused(val until: Long) : AttentionItem(Severity.WARNING)

    /** Filtering (blocklists and custom rules) is switched off in AdGuard Home's settings. */
    data object FilteringOff : AttentionItem(Severity.CRITICAL)

    /**
     * Enabled lists that look like their updates fail: never downloaded, no rules, or not updated
     * for over twice the update interval (at least a day). AdGuard Home only records successful
     * updates, so a list that keeps failing just stops moving forward.
     */
    data class ListsNotUpdating(val lists: List<FilterList>) : AttentionItem(Severity.WARNING)

    /** A newer AdGuard Home is available. */
    data class UpdateAvailable(val version: String, val canAutoupdate: Boolean) : AttentionItem(Severity.INFO)

    /** Statistics are switched off, so the Overview's numbers stay empty. */
    data object StatsOff : AttentionItem(Severity.INFO)
}

object Attention {

    fun compute(snap: OverviewSnapshot, pausedUntil: Long, now: Long = System.currentTimeMillis()): List<AttentionItem> {
        val items = mutableListOf<AttentionItem>()
        val status = snap.status
        if (status != null && !status.protectionEnabled) {
            items += if (pausedUntil > now) AttentionItem.Paused(pausedUntil) else AttentionItem.ProtectionOff
        }
        snap.filtering?.let { f ->
            if (!f.enabled) items += AttentionItem.FilteringOff
            val failing = (f.filters + f.whitelistFilters).filter { it.enabled && isFailing(it, f.interval, now) }
            if (failing.isNotEmpty()) items += AttentionItem.ListsNotUpdating(failing)
        }
        snap.versionInfo?.let { v ->
            if (!v.disabled && v.newVersion.isNotBlank() && v.newVersion != status?.version) {
                items += AttentionItem.UpdateAvailable(v.newVersion, v.canAutoupdate)
            }
        }
        if (snap.statsConfig?.enabled == false) items += AttentionItem.StatsOff
        return items.sortedBy { it.severity.ordinal }
    }

    private fun isFailing(list: FilterList, intervalHours: Int, now: Long): Boolean {
        if (list.rulesCount == 0L) return true
        val updated = lastUpdatedMs(list) ?: return true
        if (intervalHours <= 0) return false
        val allowed = maxOf(2L * intervalHours * 3_600_000, 24L * 3_600_000)
        return now - updated > allowed
    }

    fun lastUpdatedMs(list: FilterList): Long? =
        list.lastUpdated.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
}
