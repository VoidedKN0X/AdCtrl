package com.vknxworks.adctrl.data.notify

import android.content.Context
import android.text.format.DateFormat
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghJson
import com.vknxworks.adctrl.data.api.FilteringStatus
import com.vknxworks.adctrl.data.api.ServerStatus
import com.vknxworks.adctrl.data.api.VersionInfo
import com.vknxworks.adctrl.data.overview.Attention
import com.vknxworks.adctrl.data.overview.Severity
import com.vknxworks.adctrl.data.server.Server
import com.vknxworks.adctrl.ui.components.errorMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Date

/** What a server looked like at the last check, so only changes are notified. Kept per server. */
@Serializable
data class CheckState(
    /** Conditions that were true last time (and have been notified). */
    val active: Set<String> = emptySet(),
    /** When a check first saw protection paused or off (ms); 0 while it's on. */
    val offSince: Long = 0,
    /** Checks in a row that couldn't reach the server. */
    val unreachableCount: Int = 0,
    /** The newest AdGuard Home version already notified. */
    val notifiedVersion: String = "",
    val lastCheckAt: Long = 0,
    /** "ok" or "failed". */
    val lastResult: String = "",
)

/** Result of one check: notifications to show, keys whose notifications to remove, the new state and the status. */
class CheckResult(val post: List<Alert>, val cancel: Set<String>, val state: CheckState, val status: ServerStatus?)

/**
 * One background check of one server, with reads only: GET /control/status, GET /control/filtering/status
 * and POST /control/version.json (which only reads AdGuard Home's last update check). Conditions
 * (protection off, paused too long, lists failing, unreachable) are notified when they start and
 * removed when they end; a new AdGuard Home version is notified once.
 */
class Checker(private val context: Context) {

    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    /** "Server: title" when more than one server is saved, else just the title. */
    private fun title(server: Server, many: Boolean, text: String) = if (many) s(R.string.nt_title_server, server.name, text) else text

    suspend fun check(server: Server, client: AghClient, settings: NotifySettings, prev: CheckState, many: Boolean): CheckResult {
        val now = System.currentTimeMillis()
        val status = try {
            client.status()
        } catch (e: AghError) {
            return unreachable(server, settings, prev, e, now, many)
        }

        val conditions = LinkedHashMap<String, Alert>()
        val events = mutableListOf<Alert>()
        var notifiedVersion = prev.notifiedVersion

        // --- Protection off or paused ---
        val off = !status.protectionEnabled
        val offSince = if (off) prev.offSince.takeIf { it > 0 } ?: now else 0L
        val pausedUntil = if (off && status.protectionDisabledDuration > 0) now + status.protectionDisabledDuration else 0L
        if (off) {
            val limitMs = settings.pauseLimitMinutes * 60_000L
            val untilOn = pausedUntil == 0L
            if (untilOn && settings.on(NotifyEvent.PROTECTION_OFF)) {
                val key = "${server.id}:protection-off"
                conditions[key] = Alert(
                    key, NotifyEvent.PROTECTION_OFF, Severity.CRITICAL,
                    title(server, many, s(R.string.att_protection_off)),
                    s(R.string.nt_protection_off_text),
                )
            } else if (settings.on(NotifyEvent.PAUSED_LONG) && (now - offSince >= limitMs || (!untilOn && pausedUntil - offSince > limitMs))) {
                val key = "${server.id}:paused-long"
                val limit = durationLabel(context, settings.pauseLimitMinutes)
                conditions[key] = Alert(
                    key, NotifyEvent.PAUSED_LONG, Severity.WARNING,
                    title(server, many, s(if (untilOn) R.string.nt_off_long_title else R.string.nt_paused_long_title, limit)),
                    if (untilOn) s(R.string.nt_off_long_text) else s(R.string.nt_paused_long_text, DateFormat.getTimeFormat(context).format(Date(pausedUntil))),
                )
            }
        }

        // --- Filtering switched off, filter lists that stopped updating ---
        if (settings.on(NotifyEvent.PROTECTION_OFF) || settings.on(NotifyEvent.LISTS_FAILING)) {
            val filtering = runCatching { client.get("filtering/status", FilteringStatus.serializer()) }.getOrNull()
            if (filtering != null) {
                if (!filtering.enabled && settings.on(NotifyEvent.PROTECTION_OFF)) {
                    val key = "${server.id}:filtering-off"
                    conditions[key] = Alert(
                        key, NotifyEvent.PROTECTION_OFF, Severity.CRITICAL,
                        title(server, many, s(R.string.att_filtering_off)),
                        s(R.string.att_filtering_off_detail),
                    )
                }
                if (settings.on(NotifyEvent.LISTS_FAILING)) {
                    val failing = Attention.failingLists(filtering, now)
                    if (failing.isNotEmpty()) {
                        val key = "${server.id}:lists-failing"
                        conditions[key] = Alert(
                            key, NotifyEvent.LISTS_FAILING, Severity.WARNING,
                            title(server, many, context.resources.getQuantityString(R.plurals.att_lists_failing, failing.size, failing.size)),
                            s(R.string.att_lists_failing_detail, failing.joinToString(", ") { it.name.ifBlank { it.url } }),
                        )
                    }
                }
            }
        }

        // --- A new AdGuard Home version (once per version) ---
        if (settings.on(NotifyEvent.UPDATE)) {
            val info = runCatching {
                AghJson.decodeFromJsonElement(VersionInfo.serializer(), client.postRead("version.json", buildJsonObject { put("recheck_now", false) }))
            }.getOrNull()
            val newVersion = info?.newVersion.orEmpty()
            if (info != null && !info.disabled && newVersion.isNotBlank() && newVersion != status.version && newVersion != prev.notifiedVersion) {
                events += Alert(
                    "${server.id}:update", NotifyEvent.UPDATE, Severity.INFO,
                    title(server, many, s(R.string.att_update, newVersion)),
                    s(if (info.canAutoupdate) R.string.nt_update_text else R.string.att_update_manual),
                )
                notifiedVersion = newVersion
            }
        }

        // Conditions are notified once when they start and removed when they end.
        val post = conditions.filterKeys { it !in prev.active }.values.toList() + events
        val cancel = prev.active - conditions.keys
        val state = CheckState(
            active = conditions.keys,
            offSince = offSince,
            unreachableCount = 0,
            notifiedVersion = notifiedVersion,
            lastCheckAt = now,
            lastResult = "ok",
        )
        return CheckResult(post, cancel, state, status)
    }

    /** The server didn't answer (or refused the sign-in): notify after two failed checks in a row. */
    private fun unreachable(server: Server, settings: NotifySettings, prev: CheckState, e: AghError, now: Long, many: Boolean): CheckResult {
        val count = prev.unreachableCount + 1
        val key = "${server.id}:unreachable"
        val keep = prev.active - key
        val post = mutableListOf<Alert>()
        var active = keep
        if (settings.on(NotifyEvent.UNREACHABLE) && count >= 2) {
            active = keep + key
            if (key !in prev.active) {
                val signIn = e is AghError.Unauthorized
                post += Alert(
                    key, NotifyEvent.UNREACHABLE, Severity.WARNING,
                    s(if (signIn) R.string.nt_signin_title else R.string.nt_unreachable_title, server.name),
                    errorMessage(context, e, server.host),
                )
            }
        }
        val cancel = if (key in prev.active && key !in active) setOf(key) else emptySet()
        return CheckResult(post, cancel, prev.copy(active = active, unreachableCount = count, lastCheckAt = now, lastResult = "failed"), null)
    }

    companion object {
        /** "30 minutes", "2 hours". */
        fun durationLabel(context: Context, minutes: Int): String =
            if (minutes % 60 == 0) {
                context.resources.getQuantityString(R.plurals.duration_hours, minutes / 60, minutes / 60)
            } else {
                context.resources.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
            }
    }
}
