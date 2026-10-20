package com.vknxworks.adctrl.ui.filters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.api.FilterList
import com.vknxworks.adctrl.data.api.FilteringStatus
import com.vknxworks.adctrl.data.filters.KnownLists
import com.vknxworks.adctrl.data.overview.Attention
import com.vknxworks.adctrl.ui.components.ConfirmDialog
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupChoice
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.components.LocalBottomClearance
import com.vknxworks.adctrl.ui.components.PillButton
import com.vknxworks.adctrl.ui.components.formatCount
import com.vknxworks.adctrl.ui.components.relativeTime
import com.vknxworks.adctrl.ui.theme.Gutter
import com.vknxworks.adctrl.ui.theme.Health

/** Update intervals AdGuard Home's own settings offer, in hours (0: never). */
private val INTERVALS = listOf(0, 1, 12, 24, 72, 168)

private class EditTarget(val list: FilterList?, val allowlist: Boolean)

/** Blocklists and allowlists: on/off, add, change, remove and update now; plus filtering on/off and the update interval. */
@Composable
fun ListsSection(
    status: FilteringStatus,
    readOnly: Boolean,
    busy: Boolean,
    act: (suspend () -> Unit, success: String?) -> Unit,
    actions: ListActions,
) {
    var edit by remember { mutableStateOf<EditTarget?>(null) }
    var confirmFilteringOff by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Pair<FilterList, Boolean>?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
        item(key = "general") {
            Group {
                if (!readOnly) {
                    GroupSwitch(
                        stringResource(R.string.filtering_enabled),
                        stringResource(R.string.filtering_enabled_subtitle),
                        checked = status.enabled,
                        icon = Icons.Filled.FilterAlt,
                    ) { on -> if (on) act({ actions.setFiltering(true, status.interval) }, null) else confirmFilteringOff = true }
                    GroupChoice(
                        stringResource(R.string.filtering_interval),
                        INTERVALS.map { it to intervalLabel(it) },
                        status.interval,
                        icon = Icons.Filled.Schedule,
                    ) { h -> act({ actions.setFiltering(status.enabled, h) }, null) }
                    GroupRow(
                        stringResource(R.string.lists_refresh),
                        stringResource(R.string.lists_refresh_subtitle),
                        icon = Icons.Filled.Refresh,
                        onClick = if (busy) null else { { act({ actions.refresh() }, null) } },
                    )
                } else {
                    GroupRow(
                        stringResource(if (status.enabled) R.string.filtering_on else R.string.filtering_off),
                        intervalLabel(status.interval),
                        icon = Icons.Filled.FilterAlt,
                    )
                }
            }
        }
        listOf(false to status.filters, true to status.whitelistFilters).forEach { (allow, lists) ->
            item(key = "head-$allow") {
                Row(Modifier.fillMaxWidth().padding(start = Gutter + 4.dp, end = Gutter, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (allow) R.string.allowlists else R.string.blocklists) + " · " + lists.size,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!readOnly) {
                        TextButton(onClick = { edit = EditTarget(null, allow) }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(if (allow) R.string.add_allowlist else R.string.add_blocklist))
                        }
                    }
                }
            }
            if (lists.isEmpty()) {
                item(key = "empty-$allow") {
                    Text(
                        stringResource(if (allow) R.string.allowlists_empty else R.string.blocklists_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                    )
                }
            }
            items(lists, key = { "list-$allow-${it.id}-${it.url}" }) { list ->
                Group {
                    val updated = Attention.lastUpdatedMs(list)
                    val failing = list.enabled && (list.rulesCount == 0L || updated == null)
                    GroupRow(
                        list.name.ifBlank { list.url },
                        listOfNotNull(
                            list.url.takeIf { list.name.isNotBlank() },
                            pluralStringResource(R.plurals.list_rules, list.rulesCount.toInt().coerceAtLeast(0), formatCount(list.rulesCount)) + " · " +
                                (updated?.let { stringResource(R.string.list_updated, relativeTime(it)) } ?: stringResource(R.string.list_never_updated)),
                        ).joinToString("\n"),
                        icon = when {
                            failing -> Icons.Filled.Warning
                            allow -> Icons.Filled.Verified
                            else -> Icons.Filled.Block
                        },
                        iconTint = when {
                            failing -> Health.warn
                            !list.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                            allow -> Health.ok
                            else -> MaterialTheme.colorScheme.primary
                        },
                        onClick = if (readOnly) null else { { edit = EditTarget(list, allow) } },
                        trailing = if (readOnly) {
                            null
                        } else {
                            {
                                Switch(
                                    checked = list.enabled,
                                    enabled = !busy,
                                    onCheckedChange = { on -> act({ actions.setList(list.url, allow, on, list.name, list.url) }, null) },
                                )
                            }
                        },
                    )
                }
            }
        }
        item(key = "note") {
            Text(
                stringResource(R.string.lists_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 12.dp),
            )
        }
    }

    edit?.let { target ->
        ListSheet(
            target = target,
            existingUrls = (status.filters + status.whitelistFilters).map { it.url }.toSet(),
            onDismiss = { edit = null },
            onSave = { name, url, enabled ->
                edit = null
                val list = target.list
                if (list == null) act({ actions.add(name, url, target.allowlist) }, null)
                else act({ actions.setList(list.url, target.allowlist, enabled, name, url) }, null)
            },
            onRemove = { list -> edit = null; removing = list to target.allowlist },
        )
    }
    removing?.let { (list, allow) ->
        ConfirmDialog(
            title = stringResource(R.string.list_remove_title, list.name.ifBlank { list.url }),
            body = stringResource(if (allow) R.string.list_remove_body_allow else R.string.list_remove_body),
            confirmLabel = stringResource(R.string.remove),
            onDismiss = { removing = null },
            onConfirm = { removing = null; act({ actions.remove(list.url, allow) }, null) },
        )
    }
    if (confirmFilteringOff) {
        ConfirmDialog(
            title = stringResource(R.string.filtering_off_title),
            body = stringResource(R.string.filtering_off_body),
            confirmLabel = stringResource(R.string.filtering_off_confirm),
            onDismiss = { confirmFilteringOff = false },
            onConfirm = { confirmFilteringOff = false; act({ actions.setFiltering(false, status.interval) }, null) },
        )
    }
}

/** What the lists section can do; implemented by the Filters screen with the repository. */
interface ListActions {
    suspend fun setFiltering(enabled: Boolean, interval: Int)
    suspend fun refresh()
    suspend fun add(name: String, url: String, allowlist: Boolean)
    suspend fun setList(url: String, allowlist: Boolean, enabled: Boolean, name: String, newUrl: String)
    suspend fun remove(url: String, allowlist: Boolean)
}

@Composable
private fun intervalLabel(h: Int): String = when (h) {
    0 -> stringResource(R.string.interval_never)
    1 -> stringResource(R.string.interval_hour)
    24 -> stringResource(R.string.interval_day)
    72 -> stringResource(R.string.interval_3days)
    168 -> stringResource(R.string.interval_week)
    else -> pluralStringResource(R.plurals.interval_hours, h, h)
}

/** Add a list (from the built-in picks or your own address) or change one. */
@Composable
private fun ListSheet(
    target: EditTarget,
    existingUrls: Set<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, enabled: Boolean) -> Unit,
    onRemove: (FilterList) -> Unit,
) {
    val list = target.list
    var name by remember { mutableStateOf(list?.name.orEmpty()) }
    var url by remember { mutableStateOf(list?.url.orEmpty()) }
    var enabled by remember { mutableStateOf(list?.enabled ?: true) }
    var category by remember { mutableStateOf(KnownLists.Category.GENERAL) }
    val urlOk = url.trim().let { it.startsWith("https://") || it.startsWith("http://") || it.startsWith("/") }
    val duplicate = list == null && url.trim() in existingUrls
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(
                    when {
                        list != null -> R.string.list_edit_title
                        target.allowlist -> R.string.add_allowlist
                        else -> R.string.add_blocklist
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.list_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                url,
                { url = it.trim() },
                label = { Text(stringResource(R.string.list_url)) },
                placeholder = { Text("https://…") },
                singleLine = true,
                isError = url.isNotBlank() && (!urlOk || duplicate),
                supportingText = when {
                    duplicate -> { { Text(stringResource(R.string.list_duplicate)) } }
                    url.isNotBlank() && !urlOk -> { { Text(stringResource(R.string.list_url_invalid)) } }
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            if (list != null) {
                Row(Modifier.fillMaxWidth().clickable { enabled = !enabled }, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.list_enabled), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(enabled, { enabled = it })
                }
            }
            PillButton(
                text = stringResource(if (list == null) R.string.list_add else R.string.save_changes),
                onClick = { onSave(name.trim().ifBlank { url.trim() }, url.trim(), enabled) },
                enabled = urlOk && !duplicate,
                modifier = Modifier.fillMaxWidth(),
            )
            if (list != null) {
                TextButton(onClick = { onRemove(list) }) { Text(stringResource(R.string.list_remove), color = MaterialTheme.colorScheme.error) }
            }
            if (list == null && !target.allowlist) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.known_lists_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.known_lists_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KnownLists.Category.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(stringResource(categoryLabel(c))) })
                    }
                }
                KnownLists.all.filter { it.category == category }.forEach { k ->
                    val added = k.url in existingUrls
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !added) { name = k.name; url = k.url }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(k.name, style = MaterialTheme.typography.bodyLarge, color = if (added) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Text(k.url.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (added) Icon(Icons.Filled.CheckCircle, stringResource(R.string.known_added), tint = Health.ok)
                    }
                }
            }
        }
    }
}

private fun categoryLabel(c: KnownLists.Category): Int = when (c) {
    KnownLists.Category.GENERAL -> R.string.known_general
    KnownLists.Category.SECURITY -> R.string.known_security
    KnownLists.Category.OTHER -> R.string.known_other
}
