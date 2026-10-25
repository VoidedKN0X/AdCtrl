package com.vknxworks.adctrl.ui.dns

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.RetentionConfig
import com.vknxworks.adctrl.data.dns.DnsRepository
import com.vknxworks.adctrl.ui.components.ConfirmDialog
import com.vknxworks.adctrl.ui.components.CtrlTextButton
import com.vknxworks.adctrl.ui.components.EmptyState
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupChoice
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import com.vknxworks.adctrl.ui.components.errorMessage
import kotlinx.coroutines.launch

private enum class Clear { QUERY_LOG, STATS }

/** "6 hours", "1 day", "90 days": a retention period in milliseconds, in whole hours or days. */
fun retentionLabel(context: Context, ms: Long): String {
    val hours = (ms / DnsRepository.HOUR_MS).toInt()
    return if (hours % 24 == 0 && hours > 0) {
        context.resources.getQuantityString(R.plurals.duration_days, hours / 24, hours / 24)
    } else {
        context.resources.getQuantityString(R.plurals.duration_hours, hours, hours)
    }
}

/** The choices, plus the server's own value when it was set to something else (in its config file, say). */
private fun choices(context: Context, base: List<Long>, current: Long): List<Pair<Long, String>> =
    (if (current > 0 && current !in base) (base + current).sorted() else base).map { it to retentionLabel(context, it) }

/**
 * The active server's query log and statistics: on or off, how long they're kept, IP anonymising, and
 * clearing the query log or resetting the statistics, each behind a confirmation. Changes apply at once,
 * through the same client as every change (read-only mode and "Confirm changes" apply).
 */
@Composable
fun LogsStatsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly == true
    val snackbar = remember { SnackbarHostState() }

    var log by remember { mutableStateOf<RetentionConfig?>(null) }
    var stats by remember { mutableStateOf<RetentionConfig?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf<Clear?>(null) }

    fun host() = server?.host.orEmpty()

    fun load() {
        loading = true
        loadError = null
        scope.launch {
            try {
                log = c.dns.queryLogConfig()
                stats = c.dns.statsConfig()
            } catch (e: AghError) {
                loadError = e
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(server?.id) {
        log = null
        stats = null
        if (server != null) {
            load()
        } else {
            loading = false
        }
    }

    /** Runs a change, reloads both configurations and says how it went. */
    fun change(done: Int, action: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                action()
                runCatching {
                    log = c.dns.queryLogConfig()
                    stats = c.dns.statsConfig()
                }
                snackbar.showSnackbar(context.getString(done))
            } catch (e: AghError) {
                snackbar.showSnackbar(errorMessage(context, e, host()))
            } finally {
                busy = false
            }
        }
    }

    val l = log
    val st = stats
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.logs_stats))
            when {
                (l == null || st == null) && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                l != null && st != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    if (readOnly) {
                        Text(
                            stringResource(R.string.dns_read_only),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    Group(stringResource(R.string.group_querylog), footer = stringResource(R.string.logs_stats_footer)) {
                        if (readOnly) {
                            GroupRow(
                                stringResource(R.string.querylog_enabled),
                                stringResource(if (l.enabled) R.string.state_on else R.string.state_off),
                                icon = Icons.Outlined.History,
                            )
                            GroupRow(stringResource(R.string.querylog_retention), retentionLabel(context, l.interval), icon = Icons.Outlined.Timer)
                            GroupRow(
                                stringResource(R.string.querylog_anonymize),
                                stringResource(if (l.anonymizeClientIp) R.string.state_on else R.string.state_off),
                                icon = Icons.Outlined.VisibilityOff,
                            )
                        } else {
                            GroupSwitch(
                                stringResource(R.string.querylog_enabled),
                                stringResource(R.string.querylog_enabled_sub),
                                checked = l.enabled,
                                icon = Icons.Outlined.History,
                            ) { on -> if (!busy) change(R.string.logs_saved) { c.dns.setQueryLog(on, l.interval, l.anonymizeClientIp) } }
                            GroupChoice(
                                stringResource(R.string.querylog_retention),
                                choices(context, DnsRepository.QUERY_LOG_RETENTION, l.interval),
                                l.interval,
                                icon = Icons.Outlined.Timer,
                            ) { ms -> if (!busy && ms != l.interval) change(R.string.logs_saved) { c.dns.setQueryLog(l.enabled, ms, l.anonymizeClientIp) } }
                            GroupSwitch(
                                stringResource(R.string.querylog_anonymize),
                                stringResource(R.string.querylog_anonymize_sub),
                                checked = l.anonymizeClientIp,
                                icon = Icons.Outlined.VisibilityOff,
                            ) { on -> if (!busy) change(R.string.logs_saved) { c.dns.setQueryLog(l.enabled, l.interval, on) } }
                            GroupRow(
                                stringResource(R.string.querylog_clear),
                                stringResource(R.string.querylog_clear_sub),
                                icon = Icons.Outlined.Delete,
                                iconTint = MaterialTheme.colorScheme.error,
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = if (busy) null else { { clearing = Clear.QUERY_LOG } },
                            )
                        }
                    }
                    Group(stringResource(R.string.group_stats)) {
                        if (readOnly) {
                            GroupRow(
                                stringResource(R.string.stats_enabled),
                                stringResource(if (st.enabled) R.string.state_on else R.string.state_off),
                                icon = Icons.Outlined.QueryStats,
                            )
                            GroupRow(stringResource(R.string.stats_retention), retentionLabel(context, st.interval), icon = Icons.Outlined.Timer)
                        } else {
                            GroupSwitch(
                                stringResource(R.string.stats_enabled),
                                stringResource(R.string.stats_enabled_sub),
                                checked = st.enabled,
                                icon = Icons.Outlined.QueryStats,
                            ) { on -> if (!busy) change(R.string.logs_saved) { c.dns.setStats(on, st.interval) } }
                            GroupChoice(
                                stringResource(R.string.stats_retention),
                                choices(context, DnsRepository.STATS_RETENTION, st.interval),
                                st.interval,
                                icon = Icons.Outlined.Timer,
                            ) { ms -> if (!busy && ms != st.interval) change(R.string.logs_saved) { c.dns.setStats(st.enabled, ms) } }
                            GroupRow(
                                stringResource(R.string.stats_reset),
                                stringResource(R.string.stats_reset_sub),
                                icon = Icons.Outlined.Refresh,
                                iconTint = MaterialTheme.colorScheme.error,
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = if (busy) null else { { clearing = Clear.STATS } },
                            )
                        }
                    }
                }
                else -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.dns_load_failed),
                    body = loadError?.let { errorMessage(context, it, host()) }.orEmpty(),
                    action = { CtrlTextButton(onClick = ::load) { Text(stringResource(R.string.retry)) } },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
    }

    when (clearing) {
        Clear.QUERY_LOG -> ConfirmDialog(
            title = stringResource(R.string.querylog_clear_title),
            body = stringResource(R.string.querylog_clear_body, server?.name.orEmpty()),
            confirmLabel = stringResource(R.string.clear),
            icon = Icons.Outlined.Delete,
            onDismiss = { clearing = null },
            onConfirm = {
                clearing = null
                change(R.string.querylog_cleared) {
                    c.dns.clearQueryLog()
                    c.appScope.launch { runCatching { c.queries.reload() } }
                }
            },
        )
        Clear.STATS -> ConfirmDialog(
            title = stringResource(R.string.stats_reset_title),
            body = stringResource(R.string.stats_reset_body, server?.name.orEmpty()),
            confirmLabel = stringResource(R.string.stats_reset),
            icon = Icons.Outlined.Refresh,
            onDismiss = { clearing = null },
            onConfirm = {
                clearing = null
                change(R.string.stats_was_reset) {
                    c.dns.resetStats()
                    c.appScope.launch { runCatching { c.overview.refresh() } }
                }
            },
        )
        null -> {}
    }
}
