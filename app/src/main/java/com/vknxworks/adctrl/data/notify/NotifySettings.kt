package com.vknxworks.adctrl.data.notify

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.settings.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Things AdCtrl can notify about, from its own background checks (AdGuard Home can't push).
 * [defaultOn]: the important ones are on as soon as background checks are, the rest are opt-in.
 */
enum class NotifyEvent(@StringRes val label: Int, @StringRes val summary: Int, val defaultOn: Boolean) {
    PROTECTION_OFF(R.string.ev_protection_off, R.string.ev_protection_off_sub, true),
    PAUSED_LONG(R.string.ev_paused_long, R.string.ev_paused_long_sub, true),
    UNREACHABLE(R.string.ev_unreachable, R.string.ev_unreachable_sub, true),
    LISTS_FAILING(R.string.ev_lists_failing, R.string.ev_lists_failing_sub, false),
    UPDATE(R.string.ev_update, R.string.ev_update_sub, false),
}

data class NotifySettings(
    /** Check the servers in the background with WorkManager. Off until turned on. */
    val polling: Boolean = false,
    val intervalMinutes: Int = 15,
    /**
     * Wake the background check through ntfy (UnifiedPush) whenever something posts to AdCtrl's push
     * address. Only works with [polling] on, which stays the fallback.
     */
    val push: Boolean = false,
    /** The events that are switched on. */
    val events: Set<NotifyEvent> = NotifyEvent.entries.filter { it.defaultOn }.toSet(),
    /** Protection paused or off for longer than this (minutes) counts as "longer than expected". */
    val pauseLimitMinutes: Int = 60,
) {
    fun on(e: NotifyEvent): Boolean = e in events

    companion object {
        val INTERVALS = listOf(15, 30, 60, 120)
        val PAUSE_LIMITS = listOf(15, 30, 60, 120, 240, 480)
    }
}

class NotifySettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val polling = booleanPreferencesKey("notify_polling")
        val interval = intPreferencesKey("notify_interval")
        val push = booleanPreferencesKey("notify_push")
        val events = stringSetPreferencesKey("notify_events")
        val pauseLimit = intPreferencesKey("notify_pause_limit")
    }

    /** A copy of [NotifySettings.polling] that can be read without waiting, for the password sealing. */
    private val mirror = context.getSharedPreferences("notify_mirror", Context.MODE_PRIVATE)

    val settings: StateFlow<NotifySettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, NotifySettings(polling = pollingNow()))

    /** Whether background checks are on, read straight away (it decides whether passwords are sealed). */
    fun pollingNow(): Boolean = mirror.getBoolean("polling", false)

    /** The saved settings, read directly (for the background worker, before the flow has a value). */
    suspend fun current(): NotifySettings = context.appDataStore.data.first().toSettings()

    private fun Preferences.toSettings() = NotifySettings(
        polling = this[K.polling] ?: false,
        intervalMinutes = (this[K.interval] ?: 15).let { if (it in NotifySettings.INTERVALS) it else 15 },
        push = this[K.push] ?: false,
        events = this[K.events]?.mapNotNull { n -> NotifyEvent.entries.firstOrNull { it.name == n } }?.toSet()
            ?: NotifyEvent.entries.filter { it.defaultOn }.toSet(),
        pauseLimitMinutes = (this[K.pauseLimit] ?: 60).let { if (it in NotifySettings.PAUSE_LIMITS) it else 60 },
    )

    suspend fun update(transform: (NotifySettings) -> NotifySettings) {
        var polling = false
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.polling] = s.polling
            p[K.interval] = s.intervalMinutes
            p[K.push] = s.push
            p[K.events] = s.events.map { it.name }.toSet()
            p[K.pauseLimit] = s.pauseLimitMinutes
            polling = s.polling
        }
        mirror.edit().putBoolean("polling", polling).commit()
    }
}
