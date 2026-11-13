package com.vknxworks.adctrl.ui.clients

import com.vknxworks.adctrl.ui.theme.CtrlType
import com.vknxworks.adctrl.ui.theme.JetBrainsMono
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.vknxworks.adctrl.ui.theme.CtrlColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.ClientFields
import com.vknxworks.adctrl.data.api.ClientFind
import com.vknxworks.adctrl.data.api.PersistentClient
import com.vknxworks.adctrl.data.api.SafeSearch
import com.vknxworks.adctrl.data.api.TopEntry
import com.vknxworks.adctrl.data.clients.ClientIds
import com.vknxworks.adctrl.data.clients.RecentSample
import com.vknxworks.adctrl.data.queries.QuerySearch
import com.vknxworks.adctrl.ui.components.ConfirmDialog
import com.vknxworks.adctrl.ui.components.EmptyState
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import com.vknxworks.adctrl.ui.components.formatCount
import com.vknxworks.adctrl.ui.components.percent
import com.vknxworks.adctrl.ui.nav.LocalNavigator
import com.vknxworks.adctrl.ui.nav.QueriesRoute
import com.vknxworks.adctrl.ui.overview.rangeLabel
import com.vknxworks.adctrl.ui.theme.Gutter
import com.vknxworks.adctrl.ui.theme.Health
import kotlinx.coroutines.launch

private enum class Sheet { EDIT, SERVICES, UPSTREAMS, ADD_PERSISTENT }

private enum class Confirm { BLOCK, UNBLOCK, DELETE }

/**
 * One client: who it is, what it did recently, and its settings. [key] is the name of a persistent
 * client, or the IP address of one AdGuard Home found by itself.
 */
@Composable
fun ClientScreen(key: String, persistent: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.clients.state.collectAsStateWithLifecycle()
    val filters by c.filters.state.collectAsStateWithLifecycle()
    val overview by c.overview.state.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly == true
    val host = server?.host.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    var currentKey by remember { mutableStateOf(key) }
    var currentPersistent by remember { mutableStateOf(persistent) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Map<String, ClientFind>>(emptyMap()) }
    var sample by remember { mutableStateOf<RecentSample?>(null) }

    val pc: PersistentClient? = if (currentPersistent) state.persistent.firstOrNull { it.name == currentKey } else null
    val rc = if (!currentPersistent) state.runtime.firstOrNull { it.ip == currentKey } else null
    val name = pc?.name ?: rc?.name?.ifBlank { rc.ip } ?: currentKey
    val ids = pc?.fields?.ids ?: listOfNotNull(rc?.ip ?: currentKey.takeIf { !currentPersistent })
    val sampleId = ClientIds.blockable(ids).firstOrNull()

    LaunchedEffect(Unit) {
        if (!state.loaded) c.clients.load()
        if (filters.services.isEmpty()) c.filters.loadServices()
    }
    // An address (from the Overview or a runtime row) that belongs to a persistent client opens that client.
    LaunchedEffect(state.persistent, currentKey, currentPersistent) {
        if (currentPersistent) return@LaunchedEffect
        state.persistent.firstOrNull { p -> p.fields.ids.any { it.equals(currentKey, ignoreCase = true) } }?.let { owner ->
            currentKey = owner.name
            currentPersistent = true
        }
    }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) return@LaunchedEffect
        info = runCatching { c.clients.find(ids) }.getOrDefault(emptyMap())
        sample = sampleId?.let { id -> runCatching { c.clients.recent(id) }.getOrNull() }
    }

    val blocked = info.values.any { it.disallowed } || c.clients.isBlocked(ids, state.access)
    val blockRule = info.values.firstOrNull { it.disallowed }?.disallowedRule.orEmpty()
    val whois = info.values.map { it.whois }.firstOrNull { it.orgname.isNotBlank() || it.country.isNotBlank() } ?: rc?.whois
    val counts = remember(overview.snapshot.stats) { overview.snapshot.stats?.let { TopEntry.parse(it.topClients).associate { e -> e.key to e.value.toLong() } }.orEmpty() }
    val queries = ids.mapNotNull { counts[it] }.takeIf { it.isNotEmpty() }?.sum()

    fun change(success: String? = null, action: suspend () -> Unit) {
        busy = true
        scope.launch {
            runClientAction(context, snackbar, host, success, action)
            busy = false
        }
    }

    fun update(transform: (ClientFields) -> ClientFields) {
        val client = pc ?: return
        change { c.clients.update(client, transform) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(name)
            // A runtime address AdGuard Home doesn't list (any more) still shows its activity.
            if (pc == null && (currentPersistent || !state.loaded)) {
                if (!state.loaded || state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    EmptyState(Icons.Outlined.Devices, stringResource(R.string.client_gone_title), stringResource(R.string.client_gone_body))
                }
                return@Column
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                // Who it is.
                Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(if (blocked) Health.critical.copy(alpha = 0.16f) else CtrlColors.Accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = if (blocked) Health.critical else CtrlColors.OnAccent)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (pc != null) stringResource(R.string.client_persistent) else listOfNotNull(stringResource(R.string.client_runtime), sourceLabel(context, rc?.source.orEmpty())).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.padding(top = 8.dp)) {
                        ids.forEach { AssistChip(onClick = {}, label = { Text(it, fontFamily = JetBrainsMono) }) }
                        pc?.fields?.tags.orEmpty().forEach { AssistChip(onClick = {}, label = { Text("#$it") }) }
                    }
                    whois?.let { w ->
                        listOf(w.orgname, w.city, w.country).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let {
                            Text(it.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                    if (blocked) {
                        Text(
                            if (blockRule.isNotBlank()) stringResource(R.string.client_blocked_rule, blockRule) else stringResource(R.string.client_blocked_long),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Health.critical,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                // What it did.
                Group(stringResource(R.string.client_activity)) {
                    GroupRow(
                        queries?.let { formatCount(it) } ?: "—",
                        stringResource(R.string.client_queries_in, stringResource(rangeLabel(overview.range))),
                        icon = Icons.Outlined.QueryStats,
                    )
                    sample?.let { s ->
                        GroupRow(
                            pluralStringResource(R.plurals.client_sample_blocked, s.blocked, s.blocked, if (s.total > 0) percent(s.blocked.toDouble() / s.total) else 0),
                            pluralStringResource(R.plurals.client_sample_of, s.total, s.total),
                            icon = Icons.Outlined.Block,
                            iconTint = Health.critical,
                        )
                    }
                    if (sampleId != null) {
                        GroupRow(
                            stringResource(R.string.client_recent_queries),
                            stringResource(R.string.client_recent_queries_subtitle),
                            icon = Icons.AutoMirrored.Outlined.List,
                            showChevron = true,
                            onClick = {
                                c.appScope.launch { c.queries.setSearch(QuerySearch(client = sampleId)) }
                                nav.tab(QueriesRoute)
                            },
                        )
                    }
                }
                sample?.takeIf { it.topDomains.isNotEmpty() }?.let { s ->
                    Group(stringResource(R.string.client_top_domains)) {
                        s.topDomains.forEach { (d, n) -> GroupRow(d, null, trailing = { Text(formatCount(n.toLong()), style = CtrlType.Data, color = CtrlColors.Muted) }) }
                    }
                }
                sample?.takeIf { it.topBlocked.isNotEmpty() }?.let { s ->
                    Group(stringResource(R.string.client_top_blocked)) {
                        s.topBlocked.forEach { (d, n) -> GroupRow(d, null, titleColor = Health.critical, trailing = { Text(formatCount(n.toLong()), style = CtrlType.Data, color = CtrlColors.Muted) }) }
                    }
                }

                // Settings of a persistent client.
                if (pc != null) {
                    val f = pc.fields
                    Group(stringResource(R.string.client_settings)) {
                        if (readOnly) {
                            GroupRow(stringResource(R.string.client_global_settings), stringResource(if (f.useGlobalSettings) R.string.state_on else R.string.state_off), icon = Icons.Outlined.Settings)
                        } else {
                            GroupSwitch(stringResource(R.string.client_global_settings), stringResource(R.string.client_global_settings_subtitle), f.useGlobalSettings, Icons.Outlined.Settings) { on ->
                                update { it.copy(useGlobalSettings = on) }
                            }
                        }
                        if (!f.useGlobalSettings) {
                            val safeSearchOn = f.safeSearch?.enabled == true
                            listOf(
                                Triple(R.string.filtering_enabled, f.filteringEnabled, Icons.Outlined.FilterAlt) to { on: Boolean -> update { it.copy(filteringEnabled = on) } },
                                Triple(R.string.safe_browsing, f.safeBrowsingEnabled, Icons.Outlined.GppBad) to { on: Boolean -> update { it.copy(safeBrowsingEnabled = on) } },
                                Triple(R.string.parental, f.parentalEnabled, Icons.Outlined.ChildCare) to { on: Boolean -> update { it.copy(parentalEnabled = on) } },
                                Triple(R.string.safe_search, safeSearchOn, Icons.Outlined.Search) to { on: Boolean ->
                                    update { it.copy(safeSearch = (it.safeSearch ?: SafeSearch()).copy(enabled = on)) }
                                },
                            ).forEach { (t, set) ->
                                val (label, value, icon) = t
                                if (readOnly) GroupRow(stringResource(label), stringResource(if (value) R.string.state_on else R.string.state_off), icon = icon)
                                else GroupSwitch(stringResource(label), null, value, icon) { on -> set(on) }
                            }
                        }
                    }
                    Group(stringResource(R.string.client_services)) {
                        if (readOnly) {
                            GroupRow(stringResource(R.string.client_global_services), stringResource(if (f.useGlobalBlockedServices) R.string.state_on else R.string.state_off), icon = Icons.Outlined.Apps)
                        } else {
                            GroupSwitch(stringResource(R.string.client_global_services), stringResource(R.string.client_global_services_subtitle), f.useGlobalBlockedServices, Icons.Outlined.Apps) { on ->
                                update { it.copy(useGlobalBlockedServices = on) }
                            }
                        }
                        if (!f.useGlobalBlockedServices) {
                            val names = f.blockedServices.orEmpty().map { id -> filters.services.firstOrNull { it.id == id }?.name ?: id }
                            GroupRow(
                                pluralStringResource(R.plurals.client_services_blocked, names.size, names.size),
                                names.take(6).joinToString(", ").ifBlank { stringResource(R.string.client_services_none) },
                                icon = Icons.Outlined.Block,
                                showChevron = !readOnly,
                                onClick = if (readOnly) null else { { sheet = Sheet.SERVICES } },
                            )
                        }
                    }
                    Group(stringResource(R.string.client_dns)) {
                        GroupRow(
                            stringResource(R.string.client_upstreams),
                            f.upstreams.orEmpty().takeIf { it.isNotEmpty() }?.joinToString("\n") ?: stringResource(R.string.client_upstreams_global),
                            icon = Icons.Outlined.CloudSync,
                            showChevron = !readOnly,
                            onClick = if (readOnly) null else { { sheet = Sheet.UPSTREAMS } },
                        )
                        if (readOnly) {
                            GroupRow(stringResource(R.string.client_ignore_querylog), stringResource(if (f.ignoreQuerylog == true) R.string.yes else R.string.no), icon = Icons.Outlined.VisibilityOff)
                            GroupRow(stringResource(R.string.client_ignore_stats), stringResource(if (f.ignoreStatistics == true) R.string.yes else R.string.no), icon = Icons.Outlined.QueryStats)
                        } else {
                            GroupSwitch(stringResource(R.string.client_ignore_querylog), stringResource(R.string.client_ignore_querylog_subtitle), f.ignoreQuerylog == true, Icons.Outlined.VisibilityOff) { on ->
                                update { it.copy(ignoreQuerylog = on) }
                            }
                            GroupSwitch(stringResource(R.string.client_ignore_stats), stringResource(R.string.client_ignore_stats_subtitle), f.ignoreStatistics == true, Icons.Outlined.QueryStats) { on ->
                                update { it.copy(ignoreStatistics = on) }
                            }
                        }
                    }
                }

                // Changes that affect the whole client.
                if (!readOnly) {
                    Group {
                        if (pc == null) {
                            GroupRow(stringResource(R.string.client_make_persistent), stringResource(R.string.client_make_persistent_subtitle), icon = Icons.Outlined.PersonAdd, showChevron = true, onClick = { sheet = Sheet.ADD_PERSISTENT })
                        } else {
                            GroupRow(stringResource(R.string.client_edit), stringResource(R.string.client_edit_subtitle), icon = Icons.Outlined.Edit, showChevron = true, onClick = { sheet = Sheet.EDIT })
                        }
                        val canBlock = ClientIds.blockable(ids).isNotEmpty()
                        GroupRow(
                            stringResource(if (blocked) R.string.client_unblock else R.string.client_block),
                            stringResource(
                                when {
                                    !canBlock -> R.string.client_block_mac_only
                                    blocked -> R.string.client_unblock_subtitle
                                    else -> R.string.client_block_subtitle
                                },
                            ),
                            icon = if (blocked) Icons.Outlined.Public else Icons.Outlined.Block,
                            iconTint = if (blocked) Health.ok else Health.critical,
                            titleColor = if (blocked) MaterialTheme.colorScheme.onSurface else Health.critical,
                            onClick = if (!canBlock || busy) null else { { confirm = if (blocked) Confirm.UNBLOCK else Confirm.BLOCK } },
                        )
                        if (pc != null) {
                            GroupRow(
                                stringResource(R.string.client_delete),
                                icon = Icons.Outlined.Delete,
                                iconTint = MaterialTheme.colorScheme.error,
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = { confirm = Confirm.DELETE },
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.client_sample_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
    }

    when (sheet) {
        Sheet.EDIT -> pc?.let { client ->
            ClientEditSheet(
                title = stringResource(R.string.client_edit),
                initialName = client.name,
                initialIds = client.fields.ids,
                supportedTags = state.supportedTags,
                initialTags = client.fields.tags.orEmpty(),
                takenNames = state.persistent.map { it.name.lowercase() }.toSet() - client.name.lowercase(),
                onDismiss = { sheet = null },
                onSave = { n, newIds, tags ->
                    sheet = null
                    busy = true
                    scope.launch {
                        val ok = runClientAction(context, snackbar, host) { c.clients.update(client) { it.copy(name = n, ids = newIds, tags = tags) } }
                        // A renamed client is found by its new name.
                        if (ok) currentKey = n
                        busy = false
                    }
                },
            )
        }
        Sheet.ADD_PERSISTENT -> ClientEditSheet(
            title = stringResource(R.string.client_make_persistent),
            initialName = rc?.name.orEmpty(),
            initialIds = ids,
            supportedTags = state.supportedTags,
            initialTags = emptyList(),
            takenNames = state.persistent.map { it.name.lowercase() }.toSet(),
            onDismiss = { sheet = null },
            onSave = { n, newIds, tags ->
                sheet = null
                busy = true
                scope.launch {
                    val ok = runClientAction(context, snackbar, host, context.getString(R.string.client_added, n)) {
                        c.clients.add(ClientFields(name = n, ids = newIds, tags = tags))
                    }
                    if (ok) {
                        currentKey = n
                        currentPersistent = true
                    }
                    busy = false
                }
            },
        )
        Sheet.SERVICES -> pc?.let { client ->
            ServicesPickerSheet(filters.services, client.fields.blockedServices.orEmpty(), onDismiss = { sheet = null }) { picked ->
                sheet = null
                update { it.copy(blockedServices = picked) }
            }
        }
        Sheet.UPSTREAMS -> pc?.let { client ->
            UpstreamsSheet(client.fields.upstreams.orEmpty(), onDismiss = { sheet = null }) { list ->
                sheet = null
                update { it.copy(upstreams = list) }
            }
        }
        null -> Unit
    }

    when (confirm) {
        Confirm.BLOCK -> ConfirmDialog(
            title = stringResource(R.string.client_block_title, name),
            body = stringResource(
                if (state.access?.allowlistMode == true) R.string.client_block_body_allowlist else R.string.client_block_body,
                name,
                ClientIds.blockable(ids).joinToString(", "),
            ),
            confirmLabel = stringResource(R.string.client_block),
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                change(context.getString(R.string.client_blocked_done, name)) {
                    c.clients.setBlocked(ids, true)
                    info = runCatching { c.clients.find(ids) }.getOrDefault(info)
                }
            },
            icon = Icons.Outlined.Block,
        )
        Confirm.UNBLOCK -> ConfirmDialog(
            title = stringResource(R.string.client_unblock_title, name),
            body = stringResource(R.string.client_unblock_body, name),
            confirmLabel = stringResource(R.string.client_unblock),
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                change(context.getString(R.string.client_unblocked_done, name)) {
                    c.clients.setBlocked(ids, false)
                    info = runCatching { c.clients.find(ids) }.getOrDefault(info)
                }
            },
            danger = false,
            icon = Icons.Outlined.Public,
        )
        Confirm.DELETE -> ConfirmDialog(
            title = stringResource(R.string.client_delete_title, name),
            body = stringResource(R.string.client_delete_body),
            confirmLabel = stringResource(R.string.remove),
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                scope.launch {
                    val ok = runClientAction(context, snackbar, host) { c.clients.delete(name) }
                    if (ok) nav.back()
                }
            },
            icon = Icons.Outlined.Delete,
        )
        null -> Unit
    }

}
