package com.vknxworks.adctrl.data.timed

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.FilteringStatus
import com.vknxworks.adctrl.data.rules.RulesApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** A protection pause planned for a time of day on one server. Nothing personal: kept as plain preferences. */
data class ScheduledPause(
    /** Local time of day, in minutes since midnight. */
    val minuteOfDay: Int,
    val durationMs: Long,
    /** Every day at that time, or only the next time. */
    val daily: Boolean,
    /** The next start, ms since the epoch. */
    val nextAtMs: Long,
    /** How the last run went: "" (not run yet or fine), "missed", "sealed", "failed". */
    val lastResult: String = "",
)

/** The scheduled pauses, one per server at most. */
object ScheduledPauses {
    private fun prefs(context: Context) = context.getSharedPreferences("scheduled_pause", Context.MODE_PRIVATE)

    fun get(context: Context, serverId: String): ScheduledPause? {
        val p = prefs(context)
        val minute = p.getInt("$serverId.minute", -1)
        if (minute !in 0 until 1440) return null
        return ScheduledPause(minute, p.getLong("$serverId.duration", 0), p.getBoolean("$serverId.daily", false), p.getLong("$serverId.next", 0), p.getString("$serverId.result", "").orEmpty())
    }

    fun put(context: Context, serverId: String, s: ScheduledPause) {
        prefs(context).edit()
            .putInt("$serverId.minute", s.minuteOfDay)
            .putLong("$serverId.duration", s.durationMs)
            .putBoolean("$serverId.daily", s.daily)
            .putLong("$serverId.next", s.nextAtMs)
            .putString("$serverId.result", s.lastResult)
            .apply()
    }

    fun clear(context: Context, serverId: String) {
        prefs(context).edit()
            .remove("$serverId.minute").remove("$serverId.duration").remove("$serverId.daily").remove("$serverId.next").remove("$serverId.result")
            .apply()
    }

    private fun midnight(nowMs: Long): Long =
        LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMs), ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Plans a pause at [minuteOfDay] for [durationMs] on [serverId] and queues the job for it. */
    fun schedule(context: Context, serverId: String, minuteOfDay: Int, durationMs: Long, daily: Boolean) {
        val now = System.currentTimeMillis()
        val next = PauseTimes.nextStart(minuteOfDay, now, midnight(now))
        put(context, serverId, ScheduledPause(minuteOfDay, durationMs, daily, next))
        TimedWorker.enqueuePause(context, serverId, next - now)
    }

    /** After a daily pause ran (or was missed): the same time tomorrow. */
    fun scheduleNextDay(context: Context, serverId: String, s: ScheduledPause, result: String) {
        val now = System.currentTimeMillis()
        // A minute past now, so a job that ran on the dot plans tomorrow's pause, not today's again.
        val next = PauseTimes.nextStart(s.minuteOfDay, now + 60_000L, midnight(now))
        put(context, serverId, s.copy(nextAtMs = next, lastResult = result))
        TimedWorker.enqueuePause(context, serverId, next - now)
    }

    fun cancel(context: Context, serverId: String) {
        clear(context, serverId)
        TimedWorker.cancelPause(context, serverId)
    }
}

/**
 * The two timed jobs, both set up by the user on the phone and both running once, at a set time, with
 * a network connection:
 *
 *  - "pause": pause protection on one server for the planned duration (see [ScheduledPauses]);
 *  - "resume": delete the client pauses that have ended from one server's custom rules (see [ClientPause]).
 *
 * Unlike the background checks these change something on the server, through [com.vknxworks.adctrl.data.server.Sessions.timedClient].
 * A server in read-only mode is left alone. While the password is sealed by the app lock the job can't
 * sign in: it tries again later, and AdCtrl also removes ended client pauses whenever it is opened.
 * Android may run the job some minutes late while the phone sleeps.
 */
class TimedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val serverId = inputData.getString(KEY_SERVER) ?: return Result.success()
        val kind = inputData.getString(KEY_KIND) ?: return Result.success()
        val server = c.sessions.servers.value.firstOrNull { it.id == serverId }
        if (server == null) {
            ScheduledPauses.clear(applicationContext, serverId)
            return Result.success()
        }
        val sealed = c.serverStore.sealedNow(serverId)
        return try {
            when (kind) {
                KIND_PAUSE -> pause(serverId, if (sealed) null else c.sessions.timedClient(server))
                else -> if (sealed) retryOrGiveUp() else resume(serverId, c.sessions.timedClient(server))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AghError.Unreachable) {
            if (kind == KIND_PAUSE) pauseFailed(serverId, "failed", retry = true) else retryOrGiveUp()
        } catch (e: Exception) {
            if (kind == KIND_PAUSE) pauseFailed(serverId, "failed", retry = false) else Result.failure()
        }
    }

    private fun retryOrGiveUp(): Result = if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    /** The pause couldn't be sent: try again while it would still make sense, else note it and plan the next day. */
    private fun pauseFailed(serverId: String, result: String, retry: Boolean): Result {
        val s = ScheduledPauses.get(applicationContext, serverId) ?: return Result.success()
        val left = PauseTimes.remaining(s.nextAtMs, s.durationMs, System.currentTimeMillis())
        if (retry && left > 0 && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        if (s.daily) ScheduledPauses.scheduleNextDay(applicationContext, serverId, s, result) else ScheduledPauses.put(applicationContext, serverId, s.copy(lastResult = result))
        return Result.success()
    }

    private suspend fun pause(serverId: String, api: AghClient?): Result {
        val s = ScheduledPauses.get(applicationContext, serverId) ?: return Result.success()
        val now = System.currentTimeMillis()
        // Queued for an older plan (the time was changed since): the job for the new plan does it.
        if (now < s.nextAtMs - EARLY_MS) return Result.success()
        val left = PauseTimes.remaining(s.nextAtMs, s.durationMs, now)
        if (left <= 0) return pauseFailed(serverId, "missed", retry = false)
        if (api == null) return pauseFailed(serverId, "sealed", retry = true)
        // POST /control/protection: off for a set time; AdGuard Home switches it back on by itself.
        api.post("protection", buildJsonObject { put("enabled", false); put("duration", left) })
        if (s.daily) ScheduledPauses.scheduleNextDay(applicationContext, serverId, s, "") else ScheduledPauses.clear(applicationContext, serverId)
        return Result.success()
    }

    private suspend fun resume(serverId: String, api: AghClient): Result {
        val next = endClientPauses(api)
        if (next != null) enqueueResume(applicationContext, serverId, next - System.currentTimeMillis())
        return Result.success()
    }

    companion object {
        const val KEY_SERVER = "server"
        const val KEY_KIND = "kind"
        const val KIND_PAUSE = "pause"
        const val KIND_RESUME = "resume"
        private const val MAX_ATTEMPTS = 12
        private const val EARLY_MS = 60_000L

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        private fun enqueue(context: Context, name: String, kind: String, serverId: String, delayMs: Long) {
            val request = OneTimeWorkRequestBuilder<TimedWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 2, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_SERVER to serverId, KEY_KIND to kind))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        }

        fun enqueuePause(context: Context, serverId: String, delayMs: Long) = enqueue(context, "adctrl-pause-$serverId", KIND_PAUSE, serverId, delayMs)

        fun cancelPause(context: Context, serverId: String) {
            WorkManager.getInstance(context).cancelUniqueWork("adctrl-pause-$serverId")
        }

        /** Queues the job that ends client pauses on [serverId], [delayMs] from now (a little after the pause ends). */
        fun enqueueResume(context: Context, serverId: String, delayMs: Long) =
            enqueue(context, "adctrl-resume-$serverId", KIND_RESUME, serverId, delayMs + 5_000L)

        /**
         * Deletes the client pauses that have ended from the server's custom rules (one read, and one
         * write only when there is something to delete). Returns when the next pause ends, or null.
         */
        suspend fun endClientPauses(api: AghClient): Long? {
            val rules = api.get("filtering/status", FilteringStatus.serializer()).userRules
            val cleaned = ClientPause.removeExpired(rules, System.currentTimeMillis())
            if (cleaned !== rules) api.post("filtering/set_rules", RulesApi.setRulesBody(cleaned))
            return ClientPause.nextEnd(cleaned)
        }
    }
}
