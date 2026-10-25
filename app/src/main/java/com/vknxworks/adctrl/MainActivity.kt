package com.vknxworks.adctrl

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.vknxworks.adctrl.ui.lock.AppLockOverlay
import com.vknxworks.adctrl.ui.lock.lockCovering
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.ui.components.ScreenPrivacy
import com.vknxworks.adctrl.ui.nav.AppRoot
import com.vknxworks.adctrl.ui.theme.AdCtrlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blank Recents card and no screenshots until the settings have been read; the switches below lift it.
        setRecentsScreenshotEnabled(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Ctrl / Signal is dark only: edge to edge, light status and navigation bar icons on the dark background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(WINDOW_BG))
        val c = container
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            // "Hide in screenshots": FLAG_SECURE (also blanks the Recents preview). Always on, whatever the
            // switch says, while server setup, the lock screen or a PIN screen is open.
            val secure = settings.hideScreenshots || ScreenPrivacy.forced > 0 || lockCovering(c.vault)
            // "Hide in recent apps": a blank Recents card; screenshots still work. Always with the app lock on,
            // so Recents never shows what's behind the lock.
            val blankRecents = settings.hideRecents || c.vault.enabled
            LaunchedEffect(secure, blankRecents) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                setRecentsScreenshotEnabled(!blankRecents)
            }
            val covered = lockCovering(c.vault)
            AdCtrlTheme {
                Box(Modifier.fillMaxSize()) {
                    // The app stays underneath (so nothing you typed is lost), hidden from TalkBack too.
                    Box(if (covered) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) { AppRoot() }
                    // The app lock: lock screen, PIN screens and "Confirm changes".
                    AppLockOverlay()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** A launcher shortcut: the main screen handles it once AdCtrl is unlocked. */
    private fun handleIntent(intent: Intent?) {
        val action = when (intent?.action) {
            ACTION_PAUSE -> ShortcutAction.PAUSE
            ACTION_QUERIES -> ShortcutAction.QUERIES
            ACTION_SEARCH -> ShortcutAction.SEARCH
            ACTION_BLOCKED -> ShortcutAction.BLOCKED
            else -> null
        } ?: return
        container.pendingShortcut.value = action
    }

    companion object {
        const val ACTION_PAUSE = "com.vknxworks.adctrl.PAUSE"
        const val ACTION_QUERIES = "com.vknxworks.adctrl.QUERIES"
        const val ACTION_SEARCH = "com.vknxworks.adctrl.SEARCH"
        const val ACTION_BLOCKED = "com.vknxworks.adctrl.BLOCKED"

        /** The Ctrl / Signal background (#08080A), also behind the status and navigation bars. */
        private const val WINDOW_BG = 0xFF08080A.toInt()
    }
}
