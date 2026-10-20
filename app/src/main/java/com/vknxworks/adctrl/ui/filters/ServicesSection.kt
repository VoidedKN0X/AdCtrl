package com.vknxworks.adctrl.ui.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.api.BlockedService
import com.vknxworks.adctrl.data.api.BlockedServicesConfig
import com.vknxworks.adctrl.data.api.DayRange
import com.vknxworks.adctrl.data.api.PauseSchedule
import com.vknxworks.adctrl.data.api.Weekday
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.LocalBottomClearance
import com.vknxworks.adctrl.ui.components.PillButton
import com.vknxworks.adctrl.ui.theme.Gutter
import com.vknxworks.adctrl.ui.theme.Health
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val DAY_MS = 86_400_000L

/** A grid of services to block, with search, a "blocked only" view and the pause schedule. */
@Composable
fun ServicesSection(
    services: List<BlockedService>,
    config: BlockedServicesConfig,
    readOnly: Boolean,
    onToggle: (id: String, blocked: Boolean) -> Unit,
    onSchedule: (PauseSchedule) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var blockedOnly by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }
    val blocked = config.ids.toSet()
    val shown = services.filter { s ->
        (search.isBlank() || s.name.contains(search, ignoreCase = true) || s.id.contains(search, ignoreCase = true)) &&
            (!blockedOnly || s.id in blocked)
    }
    val groups = shown.groupBy { it.groupId }.toSortedMap(compareBy { if (it.isBlank()) "~" else it })

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = LocalBottomClearance.current + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "schedule") {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                GroupRow(
                    stringResource(R.string.services_schedule),
                    if (config.schedule.isEmpty) stringResource(R.string.services_schedule_none) else scheduleSummary(config.schedule),
                    icon = Icons.Filled.EventBusy,
                    showChevron = true,
                    onClick = { scheduleOpen = true },
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.services_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = if (search.isNotEmpty()) {
                        { IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear)) } }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(selected = !blockedOnly, onClick = { blockedOnly = false }, label = { Text(stringResource(R.string.services_all, services.size)) })
                    FilterChip(selected = blockedOnly, onClick = { blockedOnly = true }, label = { Text(stringResource(R.string.services_blocked, blocked.size)) })
                }
            }
        }
        groups.forEach { (group, list) ->
            if (groups.size > 1 || group.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "group-$group") {
                    Text(
                        groupLabel(group),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
            }
            items(list, key = { it.id }) { s ->
                val on = s.id in blocked
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (on) Health.critical.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !readOnly) { onToggle(s.id, !on) },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(if (on) Health.critical.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(s.name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = if (on) Health.critical else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (!readOnly) Switch(checked = on, onCheckedChange = { onToggle(s.id, it) })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                Text(stringResource(R.string.services_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
        }
    }

    if (scheduleOpen) {
        ScheduleSheet(config.schedule, readOnly, onDismiss = { scheduleOpen = false }) {
            scheduleOpen = false
            onSchedule(it)
        }
    }
}

/** "social_network" → "Social network". */
@Composable
private fun groupLabel(id: String): String =
    if (id.isBlank()) stringResource(R.string.services_group_other) else id.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** Weekday is Monday first, like java.time.DayOfWeek (MONDAY = 1). */
private fun dayName(d: Weekday, style: TextStyle = TextStyle.FULL): String =
    DayOfWeek.of(d.ordinal + 1).getDisplayName(style, Locale.getDefault())

private fun clock(ms: Long): String {
    val m = (ms / 60_000).toInt()
    return String.format(Locale.getDefault(), "%02d:%02d", m / 60, m % 60)
}

@Composable
private fun scheduleSummary(s: PauseSchedule): String =
    s.days().filter { (_, r) -> r != null && !r.isEmpty }
        .joinToString(", ") { (d, r) -> "${dayName(d, TextStyle.SHORT)} ${clock(r!!.start)}–${clock(r.end)}" }

/**
 * When blocked services are allowed (paused), per day. Times are in the server's time zone
 * ("Local" means AdGuard Home's own), as AdGuard Home applies them.
 */
@Composable
private fun ScheduleSheet(initial: PauseSchedule, readOnly: Boolean, onDismiss: () -> Unit, onSave: (PauseSchedule) -> Unit) {
    var schedule by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<Pair<Weekday, Boolean>?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.services_schedule), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.schedule_body, schedule.timeZone.ifBlank { "Local" }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            schedule.days().forEach { (day, range) ->
                val on = range != null
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayName(day), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (on) {
                        TextButton(onClick = { picking = day to true }, enabled = !readOnly) { Text(clock(range!!.start)) }
                        Text("–")
                        TextButton(onClick = { picking = day to false }, enabled = !readOnly) { Text(clock(range!!.end)) }
                    }
                    Switch(
                        checked = on,
                        enabled = !readOnly,
                        onCheckedChange = { schedule = schedule.with(day, if (it) DayRange(0, DAY_MS) else null) },
                    )
                }
            }
            // AdGuard Home refuses a day that ends before it starts.
            val invalid = schedule.days().any { (_, r) -> r != null && r.end <= r.start }
            if (invalid) Text(stringResource(R.string.schedule_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (!readOnly) {
                OutlinedButton(onClick = { schedule = PauseSchedule(timeZone = schedule.timeZone) }) { Text(stringResource(R.string.schedule_clear)) }
                PillButton(stringResource(R.string.save_changes), onClick = { onSave(schedule) }, enabled = schedule != initial && !invalid, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    picking?.let { (day, isStart) ->
        val range = schedule.days().first { it.first == day }.second ?: DayRange(0, DAY_MS)
        val ms = if (isStart) range.start else range.end
        val state = rememberTimePickerState(initialHour = ((ms / 3_600_000) % 24).toInt(), initialMinute = ((ms / 60_000) % 60).toInt(), is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(stringResource(if (isStart) R.string.schedule_from else R.string.schedule_until)) },
            text = {
                Column {
                    TimePicker(state)
                    if (!isStart) Text(stringResource(R.string.schedule_midnight_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    var picked = state.hour * 3_600_000L + state.minute * 60_000L
                    // 00:00 as an end time means the end of the day.
                    if (!isStart && picked == 0L) picked = DAY_MS
                    val updated = if (isStart) range.copy(start = picked) else range.copy(end = picked)
                    schedule = schedule.with(day, updated)
                    picking = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
