package com.vknxworks.adctrl.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.WriteGuard
import com.vknxworks.adctrl.data.timed.ScheduledPause
import com.vknxworks.adctrl.data.timed.ScheduledPauses
import com.vknxworks.adctrl.ui.components.CtrlAlertDialog
import com.vknxworks.adctrl.ui.components.CtrlFilterChip
import com.vknxworks.adctrl.ui.components.CtrlSwitch
import com.vknxworks.adctrl.ui.components.CtrlTextButton
import com.vknxworks.adctrl.ui.components.CtrlTextField
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.theme.CtrlColors
import com.vknxworks.adctrl.ui.theme.Health
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val SCHEDULE_DURATIONS = listOf(
    5 * 60_000L to R.string.pause_5m,
    10 * 60_000L to R.string.pause_10m,
    30 * 60_000L to R.string.pause_30m,
    60 * 60_000L to R.string.pause_1h,
    2 * 60 * 60_000L to R.string.pause_2h,
    8 * 60 * 60_000L to R.string.pause_8h,
)

private val TIME = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""")

/** "21:05" → 1265 minutes since midnight, or null when it isn't a time of day. */
internal fun parseMinuteOfDay(text: String): Int? {
    val m = TIME.matchEntire(text.trim()) ?: return null
    return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
}

internal fun formatMinuteOfDay(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

@Composable
private fun durationLabel(ms: Long): String =
    SCHEDULE_DURATIONS.firstOrNull { it.first == ms }?.let { stringResource(it.second) } ?: stringResource(R.string.dhcp_minutes, ms / 60_000L)

/**
 * A protection pause at a set time of day, once or every day, for the server with [serverId]. AdCtrl
 * sends the pause in the background at that time (AdGuard Home has no schedule for it), and AdGuard
 * Home switches protection back on by itself when the duration is over.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduledPauseCard(serverId: String, serverName: String) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var sched by remember(serverId) { mutableStateOf(ScheduledPauses.get(context, serverId)) }
    var editing by remember { mutableStateOf(false) }

    // The job changes the plan (next day, or done): look again now and then while this is on screen.
    LaunchedEffect(serverId) {
        while (true) {
            delay(30_000)
            sched = ScheduledPauses.get(context, serverId)
        }
    }

    val s = sched
    Group {
        GroupRow(
            if (s == null) stringResource(R.string.sched_pause) else stringResource(R.string.sched_pause_at, formatMinuteOfDay(s.minuteOfDay), durationLabel(s.durationMs)),
            if (s == null) {
                stringResource(R.string.sched_pause_subtitle)
            } else {
                listOfNotNull(
                    stringResource(if (s.daily) R.string.sched_daily else R.string.sched_once),
                    when (s.lastResult) {
                        "missed" -> stringResource(R.string.sched_result_missed)
                        "sealed" -> stringResource(R.string.sched_result_sealed)
                        "failed" -> stringResource(R.string.sched_result_failed)
                        else -> null
                    },
                ).joinToString(" · ")
            },
            icon = Icons.Outlined.Schedule,
            iconTint = if (s != null && s.lastResult.isNotEmpty()) Health.warn else CtrlColors.Muted,
            showChevron = true,
            onClick = { editing = true },
        )
    }

    if (editing) {
        var time by remember { mutableStateOf(s?.let { formatMinuteOfDay(it.minuteOfDay) } ?: "21:00") }
        var duration by remember { mutableStateOf(s?.durationMs?.takeIf { d -> SCHEDULE_DURATIONS.any { it.first == d } } ?: 60 * 60_000L) }
        var daily by remember { mutableStateOf(s?.daily ?: false) }
        val minute = parseMinuteOfDay(time)
        // With the app lock on and background checks off, the password is sealed while AdCtrl is locked.
        val sealing = c.sealing()
        CtrlAlertDialog(
            onDismissRequest = { editing = false },
            icon = { Icon(Icons.Outlined.Schedule, null) },
            title = { Text(stringResource(R.string.sched_title, serverName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.sched_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CtrlTextField(
                        value = time,
                        onValueChange = { time = it.take(5) },
                        label = { Text(stringResource(R.string.sched_time)) },
                        isError = minute == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SCHEDULE_DURATIONS.forEach { (ms, label) ->
                            CtrlFilterChip(selected = duration == ms, onClick = { duration = ms }, label = { Text(stringResource(label)) })
                        }
                    }
                    Row(Modifier.fillMaxWidth().clickable { daily = !daily }, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sched_daily), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        CtrlSwitch(checked = daily, onCheckedChange = { daily = it })
                    }
                    if (sealing) Text(stringResource(R.string.sched_sealed_warning), color = Health.warn, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                CtrlTextButton(
                    onClick = {
                        val at = minute
                        if (at != null) {
                            editing = false
                            scope.launch {
                                // "Confirm changes" is asked now: the pause itself is sent later with nobody there to ask.
                                if (WriteGuard.check()) {
                                    ScheduledPauses.schedule(context, serverId, at, duration, daily)
                                    sched = ScheduledPauses.get(context, serverId)
                                }
                            }
                        }
                    },
                    enabled = minute != null && !sealing,
                ) { Text(stringResource(R.string.sched_save)) }
            },
            dismissButton = {
                Row {
                    if (s != null) {
                        CtrlTextButton(onClick = {
                            editing = false
                            ScheduledPauses.cancel(context, serverId)
                            sched = null
                        }) { Text(stringResource(R.string.sched_remove), color = MaterialTheme.colorScheme.error) }
                    }
                    CtrlTextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}
