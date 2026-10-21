package com.vknxworks.adctrl.data.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghJson
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.ServerStatus
import com.vknxworks.adctrl.data.api.Stats
import com.vknxworks.adctrl.data.api.StatsConfig
import com.vknxworks.adctrl.data.server.Server
import com.vknxworks.adctrl.ui.widgets.ProtectionWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

/** What the widget shows: the active server's protection and its last 24 hours. Kept in no-backup storage. */
@Serializable
data class WidgetSnapshot(
    val serverId: String = "",
    val serverName: String = "",
    val protectionEnabled: Boolean = true,
    /** When a pause ends (ms since epoch); 0 when protection is on or off until turned back on. */
    val pausedUntil: Long = 0,
    /** DNS queries and queries blocked by filters in the last 24 hours; -1 when unknown (statistics off). */
    val queries: Long = -1,
    val blocked: Long = -1,
    val updatedAt: Long = 0,
    /** The last refresh couldn't reach the server; the numbers are from [updatedAt]. */
    val unreachable: Boolean = false,
)

object WidgetStore {
    private fun file(context: Context) = File(context.noBackupFilesDir, "widget.json")

    fun read(context: Context): WidgetSnapshot? =
        runCatching { AghJson.decodeFromString(WidgetSnapshot.serializer(), file(context).readText()) }.getOrNull()

    fun save(context: Context, snapshot: WidgetSnapshot) {
        runCatching { file(context).writeText(AghJson.encodeToString(WidgetSnapshot.serializer(), snapshot)) }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}

/**
 * Keeps the widget current. The app hands over what it just loaded ([fromApp]); otherwise
 * [WidgetRefreshWorker] asks the active server itself (reads only), every 30 minutes while a widget is
 * placed and when a pause runs out. Nothing is fetched while the password is sealed by the app lock.
 */
object WidgetData {
    private const val DAY_MS = 86_400_000L

    /** The app loaded the active server's status (and its last 24 hours, when it had them). */
    fun fromApp(context: Context, server: Server, status: ServerStatus, last24h: Pair<Long, Long>? = null) {
        if (!ProtectionWidget.anyPlaced(context)) return
        val old = WidgetStore.read(context)?.takeIf { it.serverId == server.id }
        val now = System.currentTimeMillis()
        val snap = WidgetSnapshot(
            serverId = server.id,
            serverName = server.name,
            protectionEnabled = status.protectionEnabled,
            pausedUntil = pausedUntil(status, now),
            queries = last24h?.first ?: old?.queries ?: -1L,
            blocked = last24h?.second ?: old?.blocked ?: -1L,
            updatedAt = now,
        )
        WidgetStore.save(context, snap)
        ProtectionWidget.redraw(context)
        scheduleAtPauseEnd(context, snap)
    }

    /** Asks the active server for its protection state and last 24 hours, then redraws. */
    suspend fun refresh(context: Context) {
        val c = context.container
        val server = c.sessions.active.value?.server
        if (server == null) {
            WidgetStore.clear(context)
            ProtectionWidget.redraw(context)
            return
        }
        // Sealed by the app lock while AdCtrl is locked: nothing can be fetched; the widget says it's locked.
        if (c.serverStore.sealedNow(server.id)) {
            ProtectionWidget.redraw(context)
            return
        }
        val client = c.sessions.backgroundClient(server)
        val old = WidgetStore.read(context)?.takeIf { it.serverId == server.id }
        val snap = try {
            val status = client.status()
            val counts = runCatching { last24Hours(client, status) }.getOrNull()
            val now = System.currentTimeMillis()
            WidgetSnapshot(
                serverId = server.id,
                serverName = server.name,
                protectionEnabled = status.protectionEnabled,
                pausedUntil = pausedUntil(status, now),
                queries = counts?.first ?: -1L,
                blocked = counts?.second ?: -1L,
                updatedAt = now,
            )
        } catch (e: AghError) {
            (old ?: WidgetSnapshot(serverId = server.id, serverName = server.name)).copy(serverName = server.name, unreachable = true)
        }
        withContext(Dispatchers.IO) { WidgetStore.save(context, snap) }
        ProtectionWidget.redraw(context)
        scheduleAtPauseEnd(context, snap)
    }

    /**
     * DNS queries and blocked queries in the last 24 hours (GET /control/stats with "recent" from
     * 0.107.72 on; older servers return the whole retention period, so the last 24 hourly values are added up).
     */
    suspend fun last24Hours(client: AghClient, status: ServerStatus): Pair<Long, Long>? {
        val config = client.get("stats/config", StatsConfig.serializer())
        if (!config.enabled) return null
        val recent = AghVersion.parse(status.version).statsRecent && config.interval >= DAY_MS
        val stats = client.get("stats", Stats.serializer(), if (recent) mapOf("recent" to DAY_MS.toString()) else emptyMap())
        if (recent || config.interval in 1..DAY_MS) return stats.numDnsQueries to stats.numBlockedFiltering
        val n = if (stats.timeUnits == "days") 1 else 24
        return stats.dnsQueries.takeLast(n).sum() to stats.blockedFiltering.takeLast(n).sum()
    }

    private fun pausedUntil(status: ServerStatus, now: Long): Long =
        if (!status.protectionEnabled && status.protectionDisabledDuration > 0) now + status.protectionDisabledDuration else 0

    /** Refresh right after a pause ends, so the widget doesn't keep counting down. */
    private fun scheduleAtPauseEnd(context: Context, snap: WidgetSnapshot) {
        if (snap.pausedUntil <= 0) return
        val delay = (snap.pausedUntil - System.currentTimeMillis()).coerceAtLeast(0) + 2_000
        WidgetRefreshWorker.enqueue(context, delay)
    }
}

/** One widget refresh, with a network connection. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (ProtectionWidget.anyPlaced(applicationContext)) WidgetData.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "adctrl-widget"

        fun enqueue(context: Context, delayMs: Long = 0) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
