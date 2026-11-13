package com.vknxworks.adctrl.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.overview.PauseDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-wide preferences (not per server). Server settings and passwords live in ServerStore, encrypted. */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "adctrl")

enum class ThemeChoice(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BLACK(R.string.theme_black),
}

/** How long AdCtrl can be in the background before the app lock asks again (-1: only after a restart). */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1m, 60_000L),
    MINUTES_5(R.string.lock_again_5m, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15m, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1h, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

/**
 * What tapping the Quick Settings tile does while protection is on. [ASK] shows a choice of 1 minute,
 * 10 minutes, 1 hour or until turned back on ([TilePause.CHOICES]).
 */
enum class TilePause(val pause: PauseDuration?) {
    ASK(null),
    M1(PauseDuration.M1),
    M10(PauseDuration.M10),
    H1(PauseDuration.H1),
    UNTIL_ON(PauseDuration.UNTIL_ON),
    ;

    companion object {
        /** The durations the tile offers. */
        val CHOICES = listOf(PauseDuration.M1, PauseDuration.M10, PauseDuration.H1, PauseDuration.UNTIL_ON)
    }
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    /** "Hide in screenshots": FLAG_SECURE on the app's windows (screenshots, recordings and the Recents preview). */
    val hideScreenshots: Boolean = false,
    /** "Hide in recent apps": the Recents view shows a blank card. Screenshots still work. */
    val hideRecents: Boolean = false,
    /** What a tap on the Quick Settings tile does while protection is on: ask how long, or pause for a set time. */
    val tilePause: TilePause = TilePause.ASK,
    /** The tile pauses and resumes every server that isn't read-only, not only the active one. */
    val tileAllServers: Boolean = false,
    /** The Queries tab adds new queries by itself while it's on screen. */
    val queriesLive: Boolean = true,
    /** App lock: when it asks again after AdCtrl was in the background. */
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    /** App lock: ask for the fingerprint or app PIN before every change to a server ("Confirm changes"). */
    val lockChanges: Boolean = false,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
        /** The earlier single "hide in screenshots and recent apps" switch; an "on" turns on both new ones. */
        val legacyHide = booleanPreferencesKey("hide_content")
        /** Replaces "tile_pause" (a fixed duration only), so everyone starts with the new "Ask each time". */
        val tilePause = stringPreferencesKey("tile_pause_choice")
        val tileAll = booleanPreferencesKey("tile_all_servers")
        val queriesLive = booleanPreferencesKey("queries_live")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val lockChanges = booleanPreferencesKey("lock_changes")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** The stored settings, read now. For places that start without the UI, like the Quick Settings tile. */
    suspend fun current(): AppSettings = context.appDataStore.data.first().toSettings()

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        hideScreenshots = this[K.hideScreenshots] ?: this[K.legacyHide] ?: false,
        hideRecents = this[K.hideRecents] ?: this[K.legacyHide] ?: false,
        tilePause = enumOr(this[K.tilePause], TilePause.ASK),
        tileAllServers = this[K.tileAll] ?: false,
        queriesLive = this[K.queriesLive] ?: true,
        lockTimeout = enumOr(this[K.lockTimeout], LockTimeout.IMMEDIATELY),
        lockChanges = this[K.lockChanges] ?: false,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.hideScreenshots] = s.hideScreenshots
            p[K.hideRecents] = s.hideRecents
            p.remove(K.legacyHide)
            p[K.tilePause] = s.tilePause.name
            p[K.tileAll] = s.tileAllServers
            p[K.queriesLive] = s.queriesLive
            p[K.lockTimeout] = s.lockTimeout.name
            p[K.lockChanges] = s.lockChanges
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
