package com.vknxworks.adctrl

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.data.settings.ThemeChoice
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
            // FLAG_SECURE follows "Hide content in screenshots & recents" (on by default, and on
            // before the setting has been read).
            LaunchedEffect(settings.hideContent) {
                if (settings.hideContent) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                setRecentsScreenshotEnabled(!settings.hideContent)
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
            AdCtrlTheme(settings.theme, settings.dynamicColor) {
                AppRoot()
            }
        }
    }
}
