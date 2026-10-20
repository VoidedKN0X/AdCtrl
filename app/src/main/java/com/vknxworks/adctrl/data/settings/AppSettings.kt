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

/** App-wide preferences (not per server). Server settings and passwords will live elsewhere, encrypted. */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "adctrl")

enum class ThemeChoice(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BLACK(R.string.theme_black),
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    /** FLAG_SECURE on the whole app: blank screenshots, screen recordings and Recents card. On by default. */
    val hideContent: Boolean = true,
    /** How long a tap on the Quick Settings tile pauses protection. */
    val tilePause: PauseDuration = PauseDuration.M5,
    /** The tile pauses and resumes every server that isn't read-only, not only the active one. */
    val tileAllServers: Boolean = false,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val hideContent = booleanPreferencesKey("hide_content")
        val tilePause = stringPreferencesKey("tile_pause")
        val tileAll = booleanPreferencesKey("tile_all_servers")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** The stored settings, read now. For places that start without the UI, like the Quick Settings tile. */
    suspend fun current(): AppSettings = context.appDataStore.data.first().toSettings()

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        hideContent = this[K.hideContent] ?: true,
        tilePause = enumOr(this[K.tilePause], PauseDuration.M5),
        tileAllServers = this[K.tileAll] ?: false,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.hideContent] = s.hideContent
            p[K.tilePause] = s.tilePause.name
            p[K.tileAll] = s.tileAllServers
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
