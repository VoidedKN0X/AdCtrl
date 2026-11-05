package com.vknxworks.adctrl.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Screens that always get FLAG_SECURE, whatever the "Hide content" setting says: server setup,
 * where the password is typed. A count rather than a flag, so two such screens overlapping during
 * a transition can't clear it early.
 */
object ScreenPrivacy {
    var forced by mutableIntStateOf(0)
        private set

    internal fun acquire() { forced++ }
    internal fun release() { forced = (forced - 1).coerceAtLeast(0) }
}

/** Keeps FLAG_SECURE on for as long as the calling screen is shown. */
@Composable
fun SecureScreen() {
    DisposableEffect(Unit) {
        ScreenPrivacy.acquire()
        onDispose { ScreenPrivacy.release() }
    }
}

/**
 * Shields a window while a PIN, lock, secret or confirmation screen shows on it: other apps' floating windows
 * (overlays such as chat heads) are hidden over it, so they can't cover or imitate it, and on Android 14+ its
 * content is hidden from apps that misuse accessibility access. Real accessibility tools such as TalkBack keep
 * working. [on] = false lifts it again, so overlays keep working everywhere else in the app.
 */
fun Window.shield(on: Boolean) {
    runCatching { setHideOverlayWindows(on) }
    if (Build.VERSION.SDK_INT >= 34) {
        runCatching {
            decorView.setAccessibilityDataSensitive(
                if (on) View.ACCESSIBILITY_DATA_SENSITIVE_YES else View.ACCESSIBILITY_DATA_SENSITIVE_AUTO,
            )
        }
    }
}

/** Shields the dialog it's called from (a confirmation such as "Forgot PIN?") for as long as it shows. */
@Composable
fun ShieldDialogWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.shield(true)
        onDispose { window?.shield(false) }
    }
}

/**
 * Copies [text] marked as sensitive, so Android hides it from the clipboard preview and keyboards
 * don't keep it in their clipboard history.
 */
fun copySensitive(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text).apply {
        description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}
