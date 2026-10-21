package com.vknxworks.adctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.VisibilityOff
import android.widget.Toast
import androidx.compose.runtime.remember
import com.vknxworks.adctrl.data.lock.LockVault
import com.vknxworks.adctrl.data.settings.AppSettings
import com.vknxworks.adctrl.data.settings.LockTimeout
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.lock.AppLock
import com.vknxworks.adctrl.ui.lock.PinRequest
import com.vknxworks.adctrl.ui.lock.findActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.overview.PauseDuration
import com.vknxworks.adctrl.data.settings.ThemeChoice
import com.vknxworks.adctrl.ui.overview.pauseLabel
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Notifications
import com.vknxworks.adctrl.ui.nav.LocalNavigator
import com.vknxworks.adctrl.ui.nav.NotificationsRoute
import androidx.compose.material.icons.filled.Timer
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupChoice
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import kotlinx.coroutines.launch

/** App settings, in the Ctrl series order: appearance, privacy and security (app lock), then the Quick Settings tile. */
@Composable
fun SettingsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val themeOptions = ThemeChoice.entries.map { it to stringResource(it.label) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group(stringResource(R.string.group_appearance)) {
                GroupChoice(
                    stringResource(R.string.theme),
                    themeOptions,
                    settings.theme,
                    icon = Icons.Filled.DarkMode,
                ) { choice -> c.appScope.launch { c.settings.update { it.copy(theme = choice) } } }
                GroupSwitch(
                    stringResource(R.string.dynamic_colour),
                    stringResource(R.string.dynamic_colour_subtitle),
                    checked = settings.dynamicColor,
                    icon = Icons.Filled.Palette,
                ) { on -> c.appScope.launch { c.settings.update { it.copy(dynamicColor = on) } } }
            }
            Group(stringResource(R.string.group_notifications)) {
                GroupRow(
                    stringResource(R.string.notifications),
                    stringResource(R.string.notifications_subtitle),
                    icon = Icons.Filled.Notifications,
                    showChevron = true,
                    onClick = { nav.navigate(NotificationsRoute) },
                )
            }
            PrivacyAndSecurityGroup(settings)
            Group(stringResource(R.string.tile_settings_group), footer = stringResource(R.string.tile_settings_footer)) {
                GroupChoice(
                    stringResource(R.string.tile_pause_setting),
                    PauseDuration.entries.map { it to stringResource(pauseLabel(it)) },
                    settings.tilePause,
                    icon = Icons.Filled.Timer,
                ) { d -> c.appScope.launch { c.settings.update { it.copy(tilePause = d) } } }
                GroupSwitch(
                    stringResource(R.string.tile_all_setting),
                    stringResource(R.string.tile_all_setting_subtitle),
                    checked = settings.tileAllServers,
                    icon = Icons.Filled.Dns,
                ) { on -> c.appScope.launch { c.settings.update { it.copy(tileAllServers = on) } } }
            }
        }
    }
}

/** Privacy and security: the shared Ctrl series app lock, then hiding AdCtrl in screenshots. */
@Composable
private fun PrivacyAndSecurityGroup(settings: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    val bioAvailable = remember { AppLock.biometricAvailable(context) }
    val lockOn = c.vault.enabled
    val notify by c.notifySettings.settings.collectAsStateWithLifecycle()
    Group(
        stringResource(R.string.group_privacy),
        footer = stringResource(if (lockOn && notify.polling) R.string.app_lock_footer_background else R.string.app_lock_footer),
    ) {
        GroupSwitch(
            stringResource(R.string.app_lock),
            stringResource(R.string.app_lock_subtitle),
            checked = lockOn,
            icon = Icons.Outlined.Lock,
        ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
        if (lockOn) {
            GroupRow(
                stringResource(R.string.lock_change_pin),
                stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                icon = Icons.Outlined.Password,
                showChevron = true,
                onClick = { AppLock.request = PinRequest.CHANGE },
            )
            GroupSwitch(
                stringResource(R.string.lock_fingerprint),
                stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                checked = c.vault.biometricEnabled,
                icon = Icons.Outlined.Fingerprint,
            ) { on ->
                if (!on) {
                    c.vault.disableBiometric()
                } else if (bioAvailable) {
                    val activity = context.findActivity()
                    val cipher = c.vault.biometricEnrolCipher()
                    if (activity == null || cipher == null) {
                        Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        AppLock.biometricPrompt(
                            activity,
                            context.getString(R.string.lock_fingerprint_enable_title),
                            context.getString(R.string.lock_fingerprint_enable_subtitle),
                            cipher,
                        ) { unlocked ->
                            if (!c.vault.finishBiometricEnrol(unlocked)) {
                                Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            GroupChoice(
                stringResource(R.string.lock_again),
                LockTimeout.entries.map { it to stringResource(it.label) },
                settings.lockTimeout,
                icon = Icons.Outlined.Timer,
            ) { t -> c.appScope.launch { c.settings.update { it.copy(lockTimeout = t) } } }
            GroupSwitch(
                stringResource(R.string.lock_changes),
                stringResource(R.string.lock_changes_subtitle),
                checked = settings.lockChanges,
                icon = Icons.Outlined.Key,
            ) { on -> c.appScope.launch { c.settings.update { it.copy(lockChanges = on) } } }
        }
        GroupSwitch(
            stringResource(R.string.hide_screenshots),
            stringResource(R.string.hide_screenshots_subtitle),
            checked = settings.hideScreenshots,
            icon = Icons.Outlined.VisibilityOff,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideScreenshots = on) } } }
        GroupSwitch(
            stringResource(R.string.hide_recents),
            stringResource(R.string.hide_recents_subtitle),
            checked = settings.hideRecents,
            icon = Icons.Outlined.Layers,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideRecents = on) } } }
    }
}
