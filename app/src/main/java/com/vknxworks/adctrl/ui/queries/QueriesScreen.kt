package com.vknxworks.adctrl.ui.queries

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.vknxworks.adctrl.ShortcutAction
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.ui.lock.lockCovering
import com.vknxworks.adctrl.data.api.QueryFilter
import com.vknxworks.adctrl.data.api.QueryKind
import com.vknxworks.adctrl.data.api.QueryLogItem
import com.vknxworks.adctrl.ui.components.ConnectionBanner
import com.vknxworks.adctrl.ui.components.EmptyState
import com.vknxworks.adctrl.ui.components.LocalBottomClearance
import com.vknxworks.adctrl.ui.components.PollWhileVisible
import com.vknxworks.adctrl.ui.components.TabTitle
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.ui.components.formatMs
import com.vknxworks.adctrl.ui.nav.CheckHostRoute
import com.vknxworks.adctrl.ui.nav.LocalNavigator
import com.vknxworks.adctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val LIVE_MS = 5_000L

/** A pending block or unblock from a row's detail sheet. */
private class RuleTarget(val block: Boolean, val item: QueryLogItem)

/** The live query log: newest first, filters, and one-tap block or unblock for any query. */
@Composable
fun QueriesScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val state by c.queries.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val connection by c.sessions.connection.collectAsStateWithLifecycle()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly == true
    val search = state.search
    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<QueryLogItem?>(null) }
    var ruleTarget by remember { mutableStateOf<RuleTarget?>(null) }
    var text by remember(search.text) { mutableStateOf(search.text) }

    // Launcher shortcuts, after unlocking: "Search the query log" puts the cursor in the search field,
    // "Blocked queries" shows only blocked ones.
    val searchFocus = remember { FocusRequester() }
    val shortcut by c.pendingShortcut.collectAsStateWithLifecycle()
    val covered = lockCovering(c.vault)
    LaunchedEffect(shortcut, covered) {
        val action = shortcut ?: return@LaunchedEffect
        if (covered || action == ShortcutAction.PAUSE) return@LaunchedEffect
        c.pendingShortcut.value = null
        when (action) {
            ShortcutAction.SEARCH -> {
                delay(300)
                runCatching { searchFocus.requestFocus() }
            }
            ShortcutAction.BLOCKED -> c.queries.setSearch(c.queries.state.value.search.copy(filter = QueryFilter.BLOCKED))
            else -> Unit
        }
    }

    // First load, and again whenever the server changes.
    LaunchedEffect(server?.id) {
        if (server != null && state.updatedAt == 0L) c.queries.reload()
    }
    // Live updates only while this screen is visible, and only when switched on.
    if (settings.queriesLive) {
        PollWhileVisible(server?.id to search, LIVE_MS) { c.queries.poll() }
    }
    // Typing filters the log after a short pause.
    LaunchedEffect(text) {
        if (text == search.text) return@LaunchedEffect
        delay(400)
        c.queries.setSearch(search.copy(text = text))
    }

    val list = rememberLazyListState()
    // Infinite scroll: load the next older page near the end.
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= list.layoutInfo.totalItemsCount - 10 }
            .collect { c.queries.loadMore() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_queries), trailing = {
                IconButton(onClick = { nav.navigate(CheckHostRoute()) }) {
                    Icon(Icons.AutoMirrored.Filled.FactCheck, stringResource(R.string.check_host_title))
                }
                IconButton(onClick = { c.appScope.launch { c.settings.update { it.copy(queriesLive = !it.queriesLive) } } }) {
                    Icon(
                        if (settings.queriesLive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(if (settings.queriesLive) R.string.live_pause else R.string.live_resume),
                    )
                }
            })
            if (connection.error != null) ConnectionBanner()
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(if (search.client.isNotBlank()) R.string.queries_search_domain else R.string.queries_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = if (text.isNotEmpty()) {
                    { IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear)) } }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp).focusRequester(searchFocus),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (search.client.isNotBlank()) {
                    InputChip(
                        selected = true,
                        onClick = { scope.launch { c.queries.setSearch(search.copy(client = "")) } },
                        label = { Text(stringResource(R.string.queries_client_chip, search.client)) },
                        trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.clear), Modifier.size(18.dp)) },
                    )
                }
                QueryFilter.entries.forEach { f ->
                    FilterChip(
                        selected = search.filter == f,
                        onClick = { scope.launch { c.queries.setSearch(search.copy(filter = f)) } },
                        label = { Text(stringResource(filterLabel(f))) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        c.queries.reload()
                        refreshing = false
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.items.isEmpty() && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.items.isEmpty() && state.error != null -> EmptyState(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.queries_error_title),
                        body = errorMessage(context, state.error!!, server?.host.orEmpty()),
                        modifier = Modifier.padding(bottom = LocalBottomClearance.current),
                    )
                    state.items.isEmpty() && state.updatedAt > 0 -> EmptyState(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.queries_empty_title),
                        body = stringResource(if (search.isEmpty) R.string.queries_empty_body else R.string.queries_empty_filtered),
                        modifier = Modifier.padding(bottom = LocalBottomClearance.current),
                    )
                    else -> LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp),
                    ) {
                        items(state.items, key = { it.key }) { item ->
                            QueryRow(item, state.listNames) { selected = item }
                            HorizontalDivider(Modifier.padding(start = Gutter + 32.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (state.loadingMore) {
                            item(key = "more") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        } else if (state.endReached && state.items.isNotEmpty()) {
                            item(key = "end") {
                                Text(
                                    stringResource(R.string.queries_end),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current + 8.dp))
    }

    selected?.let { item ->
        QueryDetailSheet(
            item = item,
            listNames = state.listNames,
            readOnly = readOnly,
            onDismiss = { selected = null },
            onRule = { block -> selected = null; ruleTarget = RuleTarget(block, item) },
            onClientQueries = {
                selected = null
                scope.launch { c.queries.setSearch(search.copy(client = item.client.ifBlank { item.clientId }, text = "")) }
            },
            onCheckHost = {
                selected = null
                nav.navigate(CheckHostRoute(host = item.asciiDomain, client = item.client.ifBlank { item.clientId }))
            },
        )
    }
    ruleTarget?.let { target ->
        val item = target.item
        val ruleClient = item.client.ifBlank { item.clientId }.ifBlank { null }
        RuleDialog(
            block = target.block,
            host = item.asciiDomain,
            client = ruleClient,
            clientLabel = item.clientLabel,
            writableServers = servers.count { !it.readOnly },
            loadOpposing = { c.userRules.opposing(target.block, item.asciiDomain, ruleClient) },
            onDismiss = { ruleTarget = null },
            onSave = { rule, all ->
                ruleTarget = null
                scope.launch {
                    val result = c.userRules.apply(target.block, item.asciiDomain, ruleClient, rule, all)
                    snackbar.showSnackbar(ruleResultMessage(context, result, target.block, item.domain))
                }
            },
        )
    }
}

/** One query: time, domain, type, client, what happened, response time and upstream. */
@Composable
private fun QueryRow(item: QueryLogItem, listNames: Map<Long, String>, onClick: () -> Unit) {
    val context = LocalContext.current
    val kind = item.kind
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(kindIcon(kind), stringResource(kindLabel(kind)), Modifier.size(20.dp).padding(top = 2.dp), tint = kindColor(kind))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.domain, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(queryTime(item.time), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            val what = buildString {
                append(stringResource(kindLabel(kind)))
                if (kind == QueryKind.BLOCKED_SERVICE && item.serviceName.isNotBlank()) append(" · ").append(item.serviceName)
                item.rules.firstOrNull()?.takeIf { kind.isBlocked || kind == QueryKind.ALLOWED }?.let { append(" · ").append(ruleSource(it, listNames)) }
                if (item.cached) append(" · ").append(stringResource(R.string.kind_cached))
            }
            Text(what, style = MaterialTheme.typography.bodySmall, color = kindColor(kind), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                item.question.type.takeIf { it.isNotBlank() },
                item.clientLabel.takeIf { it.isNotBlank() },
                item.elapsed?.let { formatMs(context, it / 1000) },
                item.upstream.takeIf { it.isNotBlank() }?.let(::shortUpstream),
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
