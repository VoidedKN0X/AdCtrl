package com.vknxworks.adctrl.ui.widgets

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vknxworks.adctrl.MainActivity
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.ServerStatus
import com.vknxworks.adctrl.data.overview.PauseDuration
import com.vknxworks.adctrl.data.settings.TilePause
import com.vknxworks.adctrl.ui.components.formatCountdown
import com.vknxworks.adctrl.ui.overview.pauseLabel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile for the active server's protection.
 *
 * - Lit when protection is on; dimmed while it's paused (with a countdown in the subtitle) or off.
 * - While the panel is open the tile follows the state live: it collects the same state the app
 *   shows (so a pause or resume in AdCtrl, the widget's server or another tap shows up at once), asks
 *   the server again every [REFRESH_MS] and when a pause runs out, and counts down every second.
 *   (requestListeningState alone doesn't redraw a tile that is already listening.)
 * - Tap while on: asks how long to pause (1 minute, 10 minutes, 1 hour or until turned back on), or
 *   pauses for the duration set under Settings › Quick Settings tile. Tap while paused or off: turns
 *   protection back on. Every change needs the phone unlocked first (unlockAndRun), so nobody can
 *   pause protection from the lock screen. With "Pause all servers" on, every server that isn't
 *   read-only is changed.
 * - Long-press: opens AdCtrl (MainActivity handles QS_TILE_PREFERENCES).
 * - A read-only server shows its state but a tap only opens the app.
 * - App lock: the tile keeps working while AdCtrl is locked, as long as the saved password isn't
 *   sealed with the app PIN (it isn't while background checks are on); the phone must still be
 *   unlocked for any change, and the server's name isn't shown. When the password is sealed, nothing
 *   can sign in without the PIN, so the tile says AdCtrl is locked and a tap opens it. With "Confirm
 *   changes" on, a tap opens AdCtrl (with the pause menu), since the tile can't ask for the
 *   fingerprint or PIN itself.
 */
class ProtectionTileService : TileService() {

    /**
     * The tile's work runs on the main thread. A failure (a setting that can't be read, an unexpected
     * answer) must never take System UI's tile down with a crash, so it only ends that job.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })
    private var listening: Job? = null

    /** Replaces the subtitle for a moment (changing, failed, unreachable); null shows the state. */
    private val note = MutableStateFlow<String?>(null)

    /** The active server's password is sealed with the app lock and AdCtrl is locked: nothing can sign in. */
    @Volatile
    private var sealed = false
    private var busy = false

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch { listen() }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** While the panel is open: redraw on every change and every second, and ask the server regularly. */
    private suspend fun listen() = coroutineScope {
        val c = container
        // Away for longer than the lock timeout: AdCtrl counts as locked now, not only once it's opened again.
        c.lockIfExpired()
        sealed = sealedNow()
        launch {
            val ticks = flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(1_000)
                }
            }
            combine(c.overview.state, c.locked, c.sessions.active, note, ticks) { _, _, _, _, _ -> }.collect { render() }
        }
        while (isActive) {
            refresh()
            val until = c.overview.state.value.pausedUntil
            val now = System.currentTimeMillis()
            // Ask again shortly after a pause ends, so the tile lights up when protection comes back.
            delay(if (until > now) minOf(REFRESH_MS, until - now + 750) else REFRESH_MS)
        }
    }

    /** Whether the active server's password is sealed right now (reads storage, so off the main thread). */
    private suspend fun sealedNow(): Boolean {
        val c = container
        val id = c.sessions.active.value?.server?.id ?: return false
        if (!c.locked.value) return false
        return withContext(Dispatchers.IO) { runCatching { c.sessions.sealedNow(id) }.getOrDefault(true) }
    }

    /** The client to use now: the app's own while unlocked, a direct one while AdCtrl is locked (see [listen]). */
    private fun clientNow(): AghClient? {
        val c = container
        val active = c.sessions.active.value ?: return null
        return if (c.locked.value) c.sessions.directClient(active.server) else active.client
    }

    private suspend fun refresh() {
        sealed = sealedNow()
        if (sealed) return
        val client = clientNow() ?: return
        val ok = container.overview.refreshStatus(using = client)
        if (!busy) note.value = if (ok) null else getString(R.string.tile_unreachable)
    }

    /** The active server's last known status, or null when there's none yet for it. */
    private fun currentStatus(): ServerStatus? {
        val c = container
        val id = c.sessions.active.value?.server?.id ?: return null
        val state = c.overview.state.value
        return state.snapshot.status.takeIf { state.snapshot.serverId == id }
    }

    override fun onClick() {
        super.onClick()
        val c = container
        c.lockIfExpired()
        val server = c.sessions.active.value?.server
        // Read here as well: the panel may have opened before AdCtrl locked.
        sealed = c.locked.value && server != null && runCatching { c.sessions.sealedNow(server.id) }.getOrDefault(true)
        if (server == null || server.readOnly || sealed) {
            openApp(null)
            return
        }
        val st = currentStatus()
        if (c.vault.enabled && c.settings.settings.value.lockChanges) {
            // "Confirm changes": the tile can't ask for the fingerprint or PIN, so AdCtrl opens (with the pause menu).
            openApp(if (st?.protectionEnabled != false) MainActivity.ACTION_PAUSE else null)
            return
        }
        if (st == null) {
            // State unknown (server unreachable so far): just try again.
            scope.launch { refresh() }
            return
        }
        val action = Runnable { if (st.protectionEnabled) askPause() else change(null) }
        // Never change protection from the lock screen: unlock the phone first.
        if (isLocked) unlockAndRun(action) else action.run()
    }

    /** Pauses for the tile's set duration, or asks how long. */
    private fun askPause() {
        scope.launch {
            val c = container
            val settings = c.settings.current()
            settings.tilePause.pause?.let {
                change(it)
                return@launch
            }
            val choices = TilePause.CHOICES
            val allServers = settings.tileAllServers && c.sessions.servers.value.count { !it.readOnly } > 1
            val dialog = AlertDialog.Builder(this@ProtectionTileService, R.style.Theme_AdCtrl_TileDialog)
                .setTitle(if (allServers) R.string.tile_pause_title_all else R.string.pause_title)
                .setItems(choices.map { getString(pauseLabel(it)) }.toTypedArray()) { _, which -> change(choices[which]) }
                .setNegativeButton(R.string.cancel, null)
                .create()
            showDialog(dialog)
        }
    }

    /** Pauses for [pause], or turns protection back on (null). */
    private fun change(pause: PauseDuration?) {
        if (busy) return
        busy = true
        note.value = getString(R.string.tile_working)
        scope.launch {
            val c = container
            val result = try {
                val all = c.settings.current().tileAllServers
                // While AdCtrl is locked its own client waits for the PIN; the phone was unlocked instead.
                val direct = c.locked.value
                c.overview.setProtection(pause, all, if (direct) c.sessions::directClient else null)
            } finally {
                busy = false
            }
            if (result.failed.isEmpty()) {
                note.value = null
            } else {
                val unsupported = result.failed.any { it.second is AghError.TimedPauseUnsupported }
                note.value = getString(if (unsupported) R.string.tile_timed_unsupported else R.string.tile_failed)
                delay(2_500)
                note.value = null
            }
        }
    }

    /** Draws the tile from the shared state; [note] replaces the subtitle (working, failed, unreachable). */
    private fun render() {
        val tile = qsTile ?: return
        val c = container
        val server = c.sessions.active.value?.server
        val st = currentStatus()
        val pausedUntil = c.overview.state.value.pausedUntil
        val now = System.currentTimeMillis()
        val override = note.value
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_shield)
        // While AdCtrl is locked the tile never names the server.
        val appLocked = c.locked.value
        tile.label = server?.name?.takeIf { !appLocked && c.sessions.servers.value.size > 1 } ?: getString(R.string.tile_label)
        when {
            server == null -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = getString(R.string.tile_no_server)
            }
            sealed -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_locked)
            }
            st == null -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = override ?: getString(R.string.tile_unknown)
            }
            st.protectionEnabled -> {
                tile.state = Tile.STATE_ACTIVE
                val fixed = c.settings.settings.value.tilePause.pause
                tile.subtitle = override ?: when {
                    server.readOnly -> getString(R.string.tile_on)
                    fixed == null -> getString(R.string.tile_on_ask)
                    else -> getString(R.string.tile_on_tap, getString(pauseLabel(fixed)))
                }
            }
            pausedUntil > now -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = override ?: getString(R.string.tile_paused, formatCountdown(pausedUntil - now))
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = override ?: getString(R.string.tile_off)
            }
        }
        tile.contentDescription = listOfNotNull(tile.label, tile.subtitle).joinToString(", ")
        tile.updateTile()
    }

    /** Opens AdCtrl, with [action] (such as the pause menu) for the main screen to handle after the lock. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(action: String?) {
        val intent = if (action != null) {
            Intent(this, MainActivity::class.java).setAction(action)
        } else {
            packageManager.getLaunchIntentForPackage(packageName) ?: return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val run = Runnable {
            val pi = PendingIntent.getActivity(this, if (action != null) 1 else 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        }
        if (isLocked) unlockAndRun(run) else run.run()
    }

    private companion object {
        /** How often the open panel asks the server again. */
        const val REFRESH_MS = 15_000L
    }
}
