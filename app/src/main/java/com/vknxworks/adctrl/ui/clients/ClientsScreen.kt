package com.vknxworks.adctrl.ui.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.ClientFields
import com.vknxworks.adctrl.data.api.TopEntry
import com.vknxworks.adctrl.ui.components.ConnectionBanner
import com.vknxworks.adctrl.ui.components.EmptyState
import com.vknxworks.adctrl.ui.components.LocalBottomClearance
import com.vknxworks.adctrl.ui.components.TabTitle
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.ui.components.formatCount
import com.vknxworks.adctrl.ui.nav.ClientRoute
import com.vknxworks.adctrl.ui.nav.LocalNavigator
import com.vknxworks.adctrl.ui.overview.rangeLabel
import com.vknxworks.adctrl.ui.theme.Gutter
import com.vknxworks.adctrl.ui.theme.Health
import kotlinx.coroutines.launch

private enum class ClientFilter { ALL, PERSISTENT, RUNTIME, BLOCKED }

/** One row of the list, persistent or found by AdGuard Home. */
private data class ClientItem(
    val key: String,
    val persistent: Boolean,
    val name: String,
    val ids: List<String>,
    val detail: String,
    val tags: List<String>,
    val queries: Long?,
    val blocked: Boolean,
)

/** Every device on the network: the clients you added, and the ones AdGuard Home found by itself. */
@Composable
fun ClientsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.clients.state.collectAsStateWithLifecycle()
    val overview by c.overview.state.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val connection by c.sessions.connection.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly == true
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ClientFilter.ALL) }
    var refreshing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(server?.id) {
        if (server == null) return@LaunchedEffect
        c.clients.load()
        // Query counts come from the Overview's statistics.
        if (overview.snapshot.serverId != server.id || overview.snapshot.stats == null) c.overview.refresh()
    }

    val counts = remember(overview.snapshot.stats) {
        overview.snapshot.stats?.let { TopEntry.parse(it.topClients).associate { e -> e.key to e.value.toLong() } }.orEmpty()
    }
    val persistentIds = state.persistent.flatMap { it.fields.ids }.toSet()
    val rows = remember(state, counts) {
        val p = state.persistent.map { pc ->
            ClientItem(
                key = "p:" + pc.name,
                persistent = true,
                name = pc.name,
                ids = pc.fields.ids,
                detail = pc.fields.ids.joinToString(", "),
                tags = pc.fields.tags.orEmpty(),
                queries = pc.fields.ids.mapNotNull { counts[it] }.takeIf { it.isNotEmpty() }?.sum(),
                blocked = c.clients.isBlocked(pc.fields.ids, state.access),
            )
        }
        // A runtime client whose IP is already part of a persistent client is shown under that one.
        val r = state.runtime.filter { it.ip !in persistentIds }.map { rc ->
            ClientItem(
                key = "r:" + rc.ip,
                persistent = false,
                name = rc.name.ifBlank { rc.ip },
                ids = listOf(rc.ip),
                detail = listOf(rc.ip.takeIf { rc.name.isNotBlank() }, sourceLabel(context, rc.source)).filterNotNull().joinToString(" · "),
                tags = emptyList(),
                queries = counts[rc.ip],
                blocked = c.clients.isBlocked(listOf(rc.ip), state.access),
            )
        }
        p + r
    }
    val shown = rows.filter { row ->
        (search.isBlank() || row.name.contains(search, true) || row.ids.any { it.contains(search, true) } || row.tags.any { it.contains(search, true) }) &&
            when (filter) {
                ClientFilter.ALL -> true
                ClientFilter.PERSISTENT -> row.persistent
                ClientFilter.RUNTIME -> !row.persistent
                ClientFilter.BLOCKED -> row.blocked
            }
    }.sortedWith(compareByDescending<ClientItem> { it.queries ?: -1 }.thenBy { it.name.lowercase() })
    val range = stringResource(rangeLabel(overview.range))

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_clients), trailing = {
            if (!readOnly) IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, stringResource(R.string.client_add)) }
        })
        if (connection.error != null) ConnectionBanner()
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(stringResource(R.string.clients_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (search.isNotEmpty()) {
                { IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear)) } }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClientFilter.entries.forEach { f ->
                val n = when (f) {
                    ClientFilter.ALL -> rows.size
                    ClientFilter.PERSISTENT -> rows.count { it.persistent }
                    ClientFilter.RUNTIME -> rows.count { !it.persistent }
                    ClientFilter.BLOCKED -> rows.count { it.blocked }
                }
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(filterLabel(f), n)) })
            }
        }
        if (state.access?.allowlistMode == true) {
            Text(
                stringResource(R.string.clients_allowlist_mode),
                style = MaterialTheme.typography.bodySmall,
                color = Health.warn,
                modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
            )
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    c.clients.load()
                    c.overview.refresh()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            when {
                !state.loaded && state.error != null -> EmptyState(
                    icon = Icons.Outlined.Devices,
                    title = stringResource(R.string.clients_error_title),
                    body = errorMessage(context, state.error!!, server?.host.orEmpty()),
                    modifier = Modifier.padding(bottom = LocalBottomClearance.current),
                )
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                shown.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Devices,
                    title = stringResource(R.string.clients_empty_title),
                    body = stringResource(if (rows.isEmpty()) R.string.clients_empty_body else R.string.clients_none_match),
                    modifier = Modifier.padding(bottom = LocalBottomClearance.current),
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
                    item(key = "range") {
                        Text(
                            stringResource(R.string.clients_counts_note, range),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
                        )
                    }
                    items(shown, key = { it.key }) { row ->
                        ClientRowView(row) { nav.navigate(ClientRoute(key = if (row.persistent) row.name else row.ids.first(), persistent = row.persistent)) }
                        HorizontalDivider(Modifier.padding(start = Gutter + 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current + 8.dp))
    }

    if (adding) {
        ClientEditSheet(
            title = stringResource(R.string.client_add),
            initialName = "",
            initialIds = emptyList(),
            supportedTags = state.supportedTags,
            initialTags = emptyList(),
            takenNames = state.persistent.map { it.name.lowercase() }.toSet(),
            onDismiss = { adding = false },
            onSave = { name, ids, tags ->
                adding = false
                scope.launch {
                    val ok = runClientAction(context, snackbar, server?.host.orEmpty()) {
                        c.clients.add(ClientFields(name = name, ids = ids, tags = tags))
                    }
                    if (ok) nav.navigate(ClientRoute(key = name, persistent = true))
                }
            },
        )
    }
}

@Composable
private fun ClientRowView(row: ClientItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (row.blocked) Health.critical.copy(alpha = 0.16f) else cs.primary.copy(alpha = if (row.persistent) 0.18f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (row.blocked) Icon(Icons.Filled.Block, stringResource(R.string.client_blocked), Modifier.size(20.dp), tint = Health.critical)
            else Text(row.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = cs.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    row.detail.takeIf { it.isNotBlank() },
                    row.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
                    stringResource(R.string.client_blocked).takeIf { row.blocked },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (row.blocked) Health.critical else cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        row.queries?.let {
            Spacer(Modifier.width(8.dp))
            Text(formatCount(it), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
    }
}

private fun filterLabel(f: ClientFilter): Int = when (f) {
    ClientFilter.ALL -> R.string.clients_all
    ClientFilter.PERSISTENT -> R.string.clients_persistent
    ClientFilter.RUNTIME -> R.string.clients_runtime
    ClientFilter.BLOCKED -> R.string.clients_blocked
}

/** Where AdGuard Home learned about a runtime client. */
fun sourceLabel(context: android.content.Context, source: String): String? = when (source.lowercase()) {
    "dhcp" -> context.getString(R.string.source_dhcp)
    "arp" -> context.getString(R.string.source_arp)
    "rdns" -> context.getString(R.string.source_rdns)
    "etc/hosts", "hosts" -> context.getString(R.string.source_hosts)
    "whois" -> context.getString(R.string.source_whois)
    "" -> null
    else -> source
}
