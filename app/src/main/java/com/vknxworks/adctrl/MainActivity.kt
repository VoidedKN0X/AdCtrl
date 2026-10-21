package com.vknxworks.adctrl

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.vknxworks.adctrl.ui.lock.AppLockOverlay
import com.vknxworks.adctrl.ui.lock.lockCovering
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.data.settings.ThemeChoice
import com.vknxworks.adctrl.ui.components.ScreenPrivacy
import com.vknxworks.adctrl.ui.nav.AppRoot
import com.vknxworks.adctrl.ui.theme.AdCtrlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recents shows a blank card, never a snapshot of the query log.
        setRecentsScreenshotEnabled(false)
        // Secure from the first frame; the setting below can lift it once it has been read.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            // FLAG_SECURE follows "Hide in screenshots and recent apps" (on by default, and on
            // before the setting has been read), and is always on while server setup, the lock screen
            // or a PIN screen is open.
            val secure = settings.hideContent || ScreenPrivacy.forced > 0 || lockCovering(c.vault)
            // With the app lock on, the recent-apps view always shows a blank card.
            val blankRecents = secure || c.vault.enabled
            LaunchedEffect(secure, blankRecents) {
                if (secure) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                setRecentsScreenshotEnabled(!blankRecents)
            }
            val dark = when (settings.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK, ThemeChoice.BLACK -> true
            }
            // Keep status and navigation bar icons readable when the app theme differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            val covered = lockCovering(c.vault)
            AdCtrlTheme(settings.theme, settings.dynamicColor) {
                Box(Modifier.fillMaxSize()) {
                    // The app stays underneath (so nothing you typed is lost), hidden from TalkBack too.
                    Box(if (covered) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) { AppRoot() }
                    // The app lock: lock screen, PIN screens and "Confirm changes".
                    AppLockOverlay()
                }
            }
        }
    }
}
