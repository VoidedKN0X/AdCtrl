package com.vknxworks.adctrl.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.overview.PauseDuration
import com.vknxworks.adctrl.ui.components.formatCountdown
import com.vknxworks.adctrl.ui.theme.Gutter
import com.vknxworks.adctrl.ui.theme.Health

/** What protection is doing right now, from the last status. */
sealed class ProtectionState {
    data object On : ProtectionState()
    data class Paused(val leftMs: Long) : ProtectionState()
    data object Off : ProtectionState()
    data object Unknown : ProtectionState()
}

/**
 * The big protection switch. Switching it off opens the pause menu (nothing is changed until a
 * duration is picked); switching it on, or "Resume now", turns protection back on at once.
 * In read-only mode the state is shown without any controls.
 */
@Composable
fun ProtectionCard(
    state: ProtectionState,
    serverName: String,
    readOnly: Boolean,
    busy: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val on = state is ProtectionState.On
    val (icon, tint) = when (state) {
        ProtectionState.On -> Icons.Filled.Shield to Health.ok
        is ProtectionState.Paused -> Icons.Filled.GppMaybe to Health.warn
        ProtectionState.Off -> Icons.Filled.GppBad to Health.critical
        ProtectionState.Unknown -> Icons.Filled.Shield to Health.idle
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = tint.copy(alpha = 0.16f), modifier = Modifier.size(56.dp)) {
                    Icon(icon, null, Modifier.padding(14.dp), tint = tint)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            when (state) {
                                ProtectionState.On -> R.string.protection_on
                                is ProtectionState.Paused -> R.string.protection_paused
                                ProtectionState.Off -> R.string.protection_off
                                ProtectionState.Unknown -> R.string.protection_unknown
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        when (state) {
                            ProtectionState.On -> stringResource(R.string.protection_on_body, serverName)
                            is ProtectionState.Paused -> stringResource(R.string.protection_paused_body, formatCountdown(state.leftMs))
                            ProtectionState.Off -> stringResource(R.string.protection_off_body)
                            ProtectionState.Unknown -> stringResource(R.string.protection_unknown_body)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!readOnly && state != ProtectionState.Unknown) {
                    Switch(
                        checked = on,
                        enabled = !busy,
                        onCheckedChange = { if (it) onResume() else onPause() },
                    )
                }
            }
            if (!readOnly && (state is ProtectionState.Paused || state == ProtectionState.Off)) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onResume, enabled = !busy) { Text(stringResource(R.string.resume_now)) }
                    if (state is ProtectionState.Paused) {
                        TextButton(onClick = onPause, enabled = !busy) { Text(stringResource(R.string.pause_change)) }
                    }
                }
            }
        }
    }
}

/**
 * The pause menu: how long protection stays off. With more than one writable server, "All servers"
 * pauses the primary and secondary together.
 */
@Composable
fun PauseSheet(
    writableServers: Int,
    onDismiss: () -> Unit,
    onPick: (PauseDuration, allServers: Boolean) -> Unit,
) {
    var all by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.pause_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(
                stringResource(R.string.pause_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            PauseDuration.entries.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(d, all) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Timer, null, tint = if (d == PauseDuration.UNTIL_ON) Health.critical else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(pauseLabel(d)), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (writableServers > 1) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable { all = !all }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.apply_all_servers), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.apply_all_servers_body, writableServers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = all, onCheckedChange = { all = it })
                }
            }
        }
    }
}

fun pauseLabel(d: PauseDuration): Int = when (d) {
    PauseDuration.S30 -> R.string.pause_30s
    PauseDuration.M1 -> R.string.pause_1m
    PauseDuration.M5 -> R.string.pause_5m
    PauseDuration.M10 -> R.string.pause_10m
    PauseDuration.H1 -> R.string.pause_1h
    PauseDuration.H8 -> R.string.pause_8h
    PauseDuration.UNTIL_ON -> R.string.pause_until_on
}
