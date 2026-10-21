package com.vknxworks.adctrl.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.notify.CheckStore
import com.vknxworks.adctrl.data.notify.Checker
import com.vknxworks.adctrl.data.notify.NotifyEvent
import com.vknxworks.adctrl.data.notify.NotifySettings
import com.vknxworks.adctrl.data.notify.NotifyWorker
import com.vknxworks.adctrl.data.notify.Notifier
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupChoice
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import com.vknxworks.adctrl.ui.components.relativeTime
import com.vknxworks.adctrl.ui.lock.AppLock
import com.vknxworks.adctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/**
 * Background checks (AdGuard Home can't push notifications, so AdCtrl checks your servers itself),
 * what to notify about, and battery advice.
 */
@Composable
fun NotificationsScreen() {
    val context = LocalContext.current
    val c = context.container
    val settings by c.notifySettings.settings.collectAsStateWithLifecycle()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val lastCheck by CheckStore.lastCheck.collectAsStateWithLifecycle()
    var permissionVersion by remember { mutableIntStateOf(0) }
    val canPost = remember(permissionVersion) { Notifier.canPost(context) }
    var batteryVersion by remember { mutableIntStateOf(0) }
    val unrestricted = remember(batteryVersion) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    // Re-check permission and battery state when coming back from system settings.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            permissionVersion++
            batteryVersion++
            servers.forEach { CheckStore.read(context, it.id) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionVersion++ }
    fun ensurePermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            AppLock.expectReturn()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun update(transform: (NotifySettings) -> NotifySettings) {
        c.appScope.launch { c.notifySettings.update(transform) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.notifications))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.notif_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
            )

            if (!canPost && settings.polling) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.notif_permission_off), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                ensurePermission()
                            } else {
                                AppLock.expectReturn()
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text(stringResource(R.string.notif_permission_allow)) }
                    }
                }
            }

            // --- Background checks ---
            Group(stringResource(R.string.group_polling), footer = stringResource(R.string.polling_footer)) {
                GroupSwitch(
                    stringResource(R.string.polling_title),
                    stringResource(R.string.polling_subtitle),
                    checked = settings.polling,
                    icon = Icons.Filled.Schedule,
                ) { on ->
                    if (on) ensurePermission()
                    update { it.copy(polling = on) }
                }
                if (settings.polling) {
                    GroupChoice(
                        stringResource(R.string.polling_interval),
                        NotifySettings.INTERVALS.map { it to pluralStringResource(R.plurals.polling_interval_value, it, it) },
                        settings.intervalMinutes,
                        icon = Icons.Filled.Timer,
                    ) { m -> update { it.copy(intervalMinutes = m) } }
                }
                servers.forEach { server ->
                    val st = lastCheck[server.id]
                    val subtitle = when {
                        st == null || st.lastCheckAt == 0L -> stringResource(R.string.last_check_never)
                        st.lastResult == "ok" -> stringResource(R.string.last_check, relativeTime(st.lastCheckAt))
                        else -> stringResource(R.string.last_check_failed, relativeTime(st.lastCheckAt))
                    }
                    GroupRow(server.name, subtitle)
                }
                GroupRow(
                    stringResource(R.string.check_now),
                    icon = Icons.Filled.Refresh,
                    onClick = {
                        ensurePermission()
                        NotifyWorker.runNow(context)
                        Toast.makeText(context, context.getString(R.string.check_started), Toast.LENGTH_SHORT).show()
                    },
                )
            }

            // --- What to notify about ---
            Group(stringResource(R.string.group_events), footer = stringResource(R.string.events_footer)) {
                NotifyEvent.entries.forEach { e ->
                    GroupSwitch(
                        stringResource(e.label),
                        stringResource(e.summary),
                        checked = settings.on(e),
                    ) { on -> update { s -> s.copy(events = if (on) s.events + e else s.events - e) } }
                    if (e == NotifyEvent.PAUSED_LONG && settings.on(e)) {
                        GroupChoice(
                            stringResource(R.string.pause_limit),
                            NotifySettings.PAUSE_LIMITS.map { it to Checker.durationLabel(context, it) },
                            settings.pauseLimitMinutes,
                            icon = Icons.Filled.HourglassBottom,
                        ) { m -> update { it.copy(pauseLimitMinutes = m) } }
                    }
                }
            }

            // --- Battery ---
            Group(stringResource(R.string.group_battery), footer = stringResource(R.string.battery_footer)) {
                GroupRow(
                    stringResource(if (unrestricted) R.string.battery_unrestricted else R.string.battery_restricted),
                    icon = if (unrestricted) Icons.Filled.BatteryFull else Icons.Filled.BatteryAlert,
                    iconTint = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                GroupRow(
                    stringResource(R.string.battery_open),
                    showChevron = true,
                    onClick = {
                        // App info is where ColorOS keeps "Allow background activity"; fall back to the battery list.
                        val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        AppLock.expectReturn()
                        runCatching { context.startActivity(appInfo) }.onFailure {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    },
                )
            }
        }
    }
}
