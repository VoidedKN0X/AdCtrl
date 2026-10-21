package com.vknxworks.adctrl.ui.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.ShortcutAction
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.ui.lock.lockCovering
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.QueryFilter
import com.vknxworks.adctrl.data.api.TopEntry
import com.vknxworks.adctrl.data.queries.QuerySearch
import com.vknxworks.adctrl.ui.nav.LocalNavigator
import com.vknxworks.adctrl.ui.nav.ClientRoute
import com.vknxworks.adctrl.ui.nav.QueriesRoute
import com.vknxworks.adctrl.data.overview.Attention
import com.vknxworks.adctrl.data.overview.PauseDuration
import com.vknxworks.adctrl.data.overview.StatsRange
import com.vknxworks.adctrl.ui.components.ConfirmDialog
import com.vknxworks.adctrl.ui.components.ConnectionBanner
import com.vknxworks.adctrl.ui.components.LocalBottomClearance
import com.vknxworks.adctrl.ui.components.PollWhileVisible
import com.vknxworks.adctrl.ui.components.StaleBanner
import com.vknxworks.adctrl.ui.components.TabTitle
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.ui.components.formatCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POLL_MS = 30_000L

private enum class Pending { CLEAR_CACHE, UPDATE }

/** The only summary screen: protection first, then what needs attention, statistics and server info. */
@Composable
fun OverviewScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val state by c.overview.state.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val connection by c.sessions.connection.collectAsStateWithLifecycle()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val server = active?.server
    val snap = state.snapshot.takeIf { it.serverId == server?.id }
    val readOnly = server?.readOnly == true

    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pauseSheet by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // The "Pause protection" launcher shortcut opens the pause menu here, after unlocking. Nothing
    // changes until a duration is picked, and "Confirm changes" still asks before it's sent.
    val shortcut by c.pendingShortcut.collectAsStateWithLifecycle()
    val covered = lockCovering(c.vault)
    LaunchedEffect(shortcut, covered) {
        if (shortcut != ShortcutAction.PAUSE || covered) return@LaunchedEffect
        c.pendingShortcut.value = null
        if (server != null && !readOnly) pauseSheet = true
    }

    PollWhileVisible(server?.id, POLL_MS) {
        c.overview.refresh()
        c.overview.state.value.error == null
    }

    // A one-second clock for the pause countdown; when it runs out, the status is fetched again.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.pausedUntil) {
        if (state.pausedUntil <= 0) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (now >= state.pausedUntil) {
                c.overview.refreshStatus()
                break
            }
            delay(1_000)
        }
    }

    val status = snap?.status
    val protection = when {
        status == null -> ProtectionState.Unknown
        status.protectionEnabled -> ProtectionState.On
        state.pausedUntil > now -> ProtectionState.Paused(state.pausedUntil - now)
        else -> ProtectionState.Off
    }
    val attention = remember(snap, state.pausedUntil, now / 1000) { snap?.let { Attention.compute(it, state.pausedUntil, now) }.orEmpty() }

    /** Opens the Queries tab filtered by a domain or client from a top list. */
    fun openQueries(search: QuerySearch) {
        c.appScope.launch { c.queries.setSearch(search) }
        nav.tab(QueriesRoute)
    }

    fun report(e: Throwable) {
        scope.launch { snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty())) }
    }

    fun changeProtection(pause: PauseDuration?, all: Boolean) {
        busy = true
        scope.launch {
            val result = c.overview.setProtection(pause, all)
            busy = false
            val failed = result.failed
            when {
                failed.size == 1 && !all -> report(failed.first().second)
                failed.isNotEmpty() -> snackbar.showSnackbar(
                    context.getString(R.string.protection_failed_some, failed.joinToString(", ") { it.first.name }),
                )
                all -> snackbar.showSnackbar(context.getString(if (pause == null) R.string.resumed_all else R.string.paused_all))
            }
        }
    }

    fun runAction(action: Pending) {
        busy = true
        scope.launch {
            try {
                when (action) {
                    Pending.CLEAR_CACHE -> {
                        c.overview.clearCache()
                        snackbar.showSnackbar(context.getString(R.string.cache_cleared))
                    }
                    Pending.UPDATE -> {
                        c.overview.startUpdate()
                        snackbar.showSnackbar(context.getString(R.string.update_started))
                    }
                }
            } catch (e: AghError) {
                report(e)
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_overview))
            val err = state.error
            if (connection.error != null) {
                ConnectionBanner()
            } else if (err != null && snap != null && snap.updatedAt > 0) {
                StaleBanner(snap.updatedAt, err, server?.host.orEmpty()) { scope.launch { c.overview.refresh(force = false) } }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        c.overview.refresh(force = true)
                        refreshing = false
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
                    item(key = "protection") {
                        ProtectionCard(
                            state = protection,
                            serverName = server?.name.orEmpty(),
                            readOnly = readOnly,
                            busy = busy,
                            onPause = { pauseSheet = true },
                            onResume = { changeProtection(null, all = false) },
                        )
                    }
                    if (attention.isNotEmpty()) {
                        item(key = "attention") { AttentionCard(attention, now) }
                    }
                    if (snap != null && snap.stats != null) {
                        val stats = snap.stats
                        item(key = "range") {
                            val version = status?.version?.let { AghVersion.parse(it) }
                            val interval = snap.statsConfig?.interval ?: 0
                            val available = if (version?.statsRecent == true && interval > 0) StatsRange.entries.filter { it.ms <= interval } else emptyList()
                            val days = (interval / 86_400_000L).toInt()
                            val hours = (interval / 3_600_000L).toInt()
                            val whole = when {
                                interval <= 0 -> null
                                days >= 1 -> pluralStringResource(R.plurals.range_last_days, days, days)
                                else -> pluralStringResource(R.plurals.range_last_hours, hours, hours)
                            }
                            RangeChips(available, state.range, whole, c.overview::setRange)
                        }
                        item(key = "grid") { StatsGrid(stats) }
                        item(key = "chart") { QueriesChart(stats, snap.updatedAt) }
                        item(key = "top-queried") {
                            TopListCard(
                                stringResource(R.string.top_queried),
                                TopEntry.parse(stats.topQueriedDomains).take(TOP_COUNT),
                                stringResource(R.string.top_empty),
                                format = { formatCount(it.value.toLong()) },
                                onClick = { e -> openQueries(QuerySearch(text = e.key)) },
                            )
                        }
                        item(key = "top-blocked") {
                            TopListCard(
                                stringResource(R.string.top_blocked),
                                TopEntry.parse(stats.topBlockedDomains).take(TOP_COUNT),
                                stringResource(R.string.top_empty),
                                format = { formatCount(it.value.toLong()) },
                                onClick = { e -> openQueries(QuerySearch(text = e.key, filter = QueryFilter.BLOCKED)) },
                            )
                        }
                        item(key = "top-clients") {
                            TopListCard(
                                stringResource(R.string.top_clients),
                                TopEntry.parse(stats.topClients).take(TOP_COUNT),
                                stringResource(R.string.top_empty),
                                format = { formatCount(it.value.toLong()) },
                                onClick = { e -> nav.navigate(ClientRoute(key = e.key, persistent = false)) },
                            )
                        }
                        item(key = "top-upstreams") { UpstreamsCard(stats) }
                    }
                    if (snap != null && snap.status != null) {
                        item(key = "info") {
                            ServerInfoCard(
                                snap = snap,
                                readOnly = readOnly,
                                busy = busy,
                                onClearCache = { pending = Pending.CLEAR_CACHE },
                                onCheckUpdate = {
                                    busy = true
                                    scope.launch {
                                        c.overview.refresh(force = true)
                                        busy = false
                                        val v = c.overview.state.value.snapshot.versionInfo
                                        val cur = c.overview.state.value.snapshot.status?.version
                                        snackbar.showSnackbar(
                                            if (v != null && v.newVersion.isNotBlank() && v.newVersion != cur) context.getString(R.string.update_available, v.newVersion)
                                            else context.getString(R.string.up_to_date),
                                        )
                                    }
                                },
                                onUpdate = { pending = Pending.UPDATE },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current + 8.dp))
    }

    if (pauseSheet) {
        PauseSheet(
            writableServers = servers.count { !it.readOnly },
            onDismiss = { pauseSheet = false },
            onPick = { d, all ->
                pauseSheet = false
                changeProtection(d, all)
            },
        )
    }
    when (pending) {
        Pending.CLEAR_CACHE -> ConfirmDialog(
            title = stringResource(R.string.clear_cache_confirm_title),
            body = stringResource(R.string.clear_cache_confirm_body, server?.name.orEmpty()),
            confirmLabel = stringResource(R.string.clear_cache_confirm),
            onDismiss = { pending = null },
            onConfirm = { pending = null; runAction(Pending.CLEAR_CACHE) },
            danger = false,
            icon = Icons.Filled.CleaningServices,
        )
        Pending.UPDATE -> ConfirmDialog(
            title = stringResource(R.string.update_confirm_title, snap?.versionInfo?.newVersion.orEmpty()),
            body = stringResource(R.string.update_confirm_body, server?.name.orEmpty()),
            confirmLabel = stringResource(R.string.update_confirm),
            onDismiss = { pending = null },
            onConfirm = { pending = null; runAction(Pending.UPDATE) },
            icon = Icons.Filled.SystemUpdate,
        )
        null -> Unit
    }
}
