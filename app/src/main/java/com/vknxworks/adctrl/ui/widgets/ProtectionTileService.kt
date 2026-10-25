package com.vknxworks.adctrl.ui.widgets

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.ServerStatus
import com.vknxworks.adctrl.ui.components.formatCountdown
import com.vknxworks.adctrl.ui.overview.pauseLabel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for the active server's protection.
 *
 * - Lit when protection is on; dimmed while it's paused (with the time left) or off.
 * - Tap: pauses protection for the duration set under Settings › Quick Settings tile, or turns it
 *   back on when it's paused or off. On a locked phone the phone is unlocked first, so nobody can
 *   pause protection from the lock screen. With "All servers" on, every server that isn't
 *   read-only is changed.
 * - Long-press: opens AdCtrl (MainActivity handles QS_TILE_PREFERENCES).
 * - A read-only server shows its state but a tap only opens the app.
 * - App lock: while AdCtrl is locked the saved password is sealed, so the tile says it's locked and a
 *   tap opens AdCtrl (to its lock screen). With "Confirm changes" on, a tap opens AdCtrl too, since
 *   the tile can't ask for the fingerprint or PIN itself.
 */
class ProtectionTileService : TileService() {

    /**
     * The tile's work runs on the main thread. A failure (a setting that can't be read, an unexpected
     * answer) must never take System UI's tile down with a crash, so it only ends that job.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })
    private var listening: Job? = null

    /** Last known protection state, so a tap knows whether to pause or resume. */
    private var status: ServerStatus? = null
    private var pausedUntil = 0L
    private var busy = false

    override fun onStartListening() {
        super.onStartListening()
        watch()
    }

    /** Shows what's known right away, asks the server, then counts down a running pause while the panel is open. */
    private fun watch() {
        listening?.cancel()
        listening = scope.launch {
            if (container.locked.value) {
                render(getString(R.string.tile_locked))
                return@launch
            }
            showCached()
            refresh()
            while (isActive && pausedUntil > 0) {
                val left = pausedUntil - System.currentTimeMillis()
                if (left <= 0) {
                    refresh()
                    if (pausedUntil <= 0) break
                } else {
                    render(null)
                }
                delay(1_000)
            }
        }
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

    override fun onClick() {
        super.onClick()
        val server = container.sessions.active.value?.server
        val c = container
        if (server == null || server.readOnly || c.locked.value || (c.vault.enabled && c.settings.settings.value.lockChanges)) {
            openApp()
            return
        }
        // Never change protection from the lock screen: unlock first.
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        if (busy) return
        val st = status ?: run {
            // State unknown (server unreachable so far): just try to refresh.
            scope.launch { refresh() }
            return
        }
        busy = true
        render(getString(R.string.tile_working))
        scope.launch {
            val c = container
            val result = try {
                val settings = c.settings.current()
                val pause = if (st.protectionEnabled) settings.tilePause else null
                c.overview.setProtection(pause, settings.tileAllServers)
            } finally {
                busy = false
            }
            if (result.failed.isNotEmpty()) {
                render(getString(R.string.tile_failed))
                delay(2_000)
            }
            // Count down a new pause (also refreshes the state).
            watch()
        }
    }

    private fun showCached() {
        val c = container
        val serverId = c.sessions.active.value?.server?.id ?: return render(null)
        val state = c.overview.state.value
        if (state.snapshot.serverId == serverId) {
            status = state.snapshot.status
            pausedUntil = state.pausedUntil
        }
        render(null)
    }

    private suspend fun refresh() {
        val active = container.sessions.active.value
        if (active == null) {
            status = null
            render(null)
            return
        }
        try {
            val st = active.client.status()
            status = st
            pausedUntil = if (!st.protectionEnabled && st.protectionDisabledDuration > 0) System.currentTimeMillis() + st.protectionDisabledDuration else 0
            container.sessions.reportStatus(active.server.id, st)
            render(null)
        } catch (e: AghError) {
            render(getString(R.string.tile_unreachable))
        }
    }

    /** Draws the tile from [status]; [override] replaces the subtitle (working, failed, unreachable). */
    private fun render(override: String?) {
        val tile = qsTile ?: return
        val server = container.sessions.active.value?.server
        val st = status
        val now = System.currentTimeMillis()
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_shield)
        tile.label = server?.name?.takeIf { container.sessions.servers.value.size > 1 } ?: getString(R.string.tile_label)
        when {
            server == null -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = getString(R.string.tile_no_server)
            }
            st == null -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = override ?: getString(R.string.tile_unknown)
            }
            st.protectionEnabled -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = override ?: if (server.readOnly) getString(R.string.tile_on) else getString(R.string.tile_on_tap, getString(pauseLabel(currentPauseLabel())))
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

    /** The tile's pause duration for the subtitle; the stored setting once it has been read. */
    private fun currentPauseLabel() = container.settings.settings.value.tilePause

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val run = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun { run() } else run()
    }
}
