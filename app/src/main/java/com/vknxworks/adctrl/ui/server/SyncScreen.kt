package com.vknxworks.adctrl.ui.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.sync.SyncFailure
import com.vknxworks.adctrl.data.sync.SyncPart
import com.vknxworks.adctrl.data.sync.SyncPlan
import com.vknxworks.adctrl.data.sync.applySync
import com.vknxworks.adctrl.data.sync.planSync
import com.vknxworks.adctrl.data.sync.readSnapshot
import com.vknxworks.adctrl.ui.components.ConfirmDialog
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupChoice
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.components.PillButton
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.ui.theme.CtrlColors
import com.vknxworks.adctrl.ui.theme.Gutter
import com.vknxworks.adctrl.ui.theme.Health
import kotlinx.coroutines.launch

private fun partLabel(p: SyncPart): Int = when (p) {
    SyncPart.LISTS -> R.string.sync_part_lists
    SyncPart.RULES -> R.string.sync_part_rules
    SyncPart.REWRITES -> R.string.sync_part_rewrites
    SyncPart.SERVICES -> R.string.sync_part_services
    SyncPart.PROTECTION -> R.string.sync_part_protection
    SyncPart.CLIENTS -> R.string.sync_part_clients
}

/** Names for a summary line, cut off after a few. */
private fun names(list: List<String>): String = list.take(6).joinToString(", ") + if (list.size > 6) ", …" else ""

/**
 * Copies settings from the active server (the primary) to another saved server: blocklists and
 * allowlists, custom rules, DNS rewrites, blocked services, the safe browsing / parental / safe search
 * switches and the clients. "Compare" only reads both servers and lists what would change; nothing is
 * sent until "Copy" is confirmed. Upstream DNS, DHCP, encryption, the access list and passwords are never copied.
 */
@Composable
fun SyncScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val source = active?.server
    val targets = servers.filter { it.id != source?.id && !it.readOnly }
    val snackbar = remember { SnackbarHostState() }

    var targetId by remember { mutableStateOf<String?>(null) }
    var parts by remember { mutableStateOf(SyncPart.entries.toSet()) }
    var plan by remember { mutableStateOf<SyncPlan?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var failures by remember { mutableStateOf<List<SyncFailure>>(emptyList()) }
    var confirming by remember { mutableStateOf(false) }
    val target = targets.firstOrNull { it.id == targetId } ?: targets.singleOrNull()

    fun compare(then: (SyncPlan) -> Unit = {}) {
        val from = active ?: return
        val to = target ?: return
        busy = true
        scope.launch {
            var fresh: SyncPlan? = null
            var problem: String? = null
            try {
                val a = readSnapshot(from.client, AghVersion.parse(from.server.version))
                val b = try {
                    readSnapshot(c.sessions.clientFor(to), AghVersion.parse(to.version))
                } catch (e: AghError) {
                    problem = context.getString(R.string.sync_target_failed, to.name, errorMessage(context, e, to.host))
                    null
                }
                if (b != null) fresh = planSync(a, b, parts)
            } catch (e: AghError) {
                problem = errorMessage(context, e, from.server.host)
            } finally {
                busy = false
            }
            val p = fresh
            if (p != null) {
                plan = p
                then(p)
            }
            problem?.let { snackbar.showSnackbar(it) }
        }
    }

    fun copy(p: SyncPlan) {
        val to = target ?: return
        busy = true
        failures = emptyList()
        progress = 0 to p.steps
        scope.launch {
            try {
                val failed = applySync(p, c.sessions.clientFor(to), AghVersion.parse(to.version)) { done -> progress = done to p.steps }
                failures = failed
                snackbar.showSnackbar(
                    if (failed.isEmpty()) context.getString(R.string.sync_done, to.name) else context.getString(R.string.sync_done_partly, failed.size, p.steps),
                )
            } catch (e: AghError) {
                snackbar.showSnackbar(context.getString(R.string.sync_stopped, errorMessage(context, e, to.host)))
            } finally {
                progress = null
                busy = false
                // Show what is still different (nothing, when everything went through).
                compare()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.sync_title))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                Group(stringResource(R.string.sync_group_servers), footer = stringResource(R.string.sync_servers_footer)) {
                    GroupRow(stringResource(R.string.sync_from), source?.let { it.name + " · " + it.displayUrl }.orEmpty(), icon = Icons.Outlined.Dns)
                    if (targets.isEmpty()) {
                        GroupRow(stringResource(R.string.sync_to), stringResource(R.string.sync_no_target), icon = Icons.Outlined.SyncAlt)
                    } else {
                        GroupChoice(
                            stringResource(R.string.sync_to),
                            targets.map { it.id to (it.name + " · " + it.displayUrl) },
                            target?.id,
                            icon = Icons.Outlined.SyncAlt,
                        ) { id ->
                            if (!busy) {
                                targetId = id
                                plan = null
                                failures = emptyList()
                            }
                        }
                    }
                }
                Group(stringResource(R.string.sync_group_parts)) {
                    SyncPart.entries.forEach { p ->
                        GroupSwitch(stringResource(partLabel(p)), checked = p in parts) { on ->
                            if (!busy) {
                                parts = if (on) parts + p else parts - p
                                plan = null
                            }
                        }
                    }
                }
                PillButton(
                    text = stringResource(if (busy && progress == null) R.string.sync_comparing else R.string.sync_compare),
                    onClick = { compare() },
                    enabled = !busy && target != null && parts.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                )

                val p = plan
                if (p != null && target != null) {
                    Group(stringResource(R.string.sync_group_changes, target.name)) {
                        if (p.isEmpty) {
                            GroupRow(stringResource(R.string.sync_same), stringResource(R.string.sync_same_sub), icon = Icons.Outlined.CheckCircle, iconTint = Health.ok)
                        }
                        if (p.listsAdd.isNotEmpty() || p.listsRemove.isNotEmpty() || p.listsUpdate.isNotEmpty() || p.filteringConfig != null) {
                            GroupRow(
                                stringResource(R.string.sync_part_lists),
                                listOfNotNull(
                                    stringResource(R.string.sync_counts, p.listsAdd.size, p.listsRemove.size, p.listsUpdate.size + if (p.filteringConfig != null) 1 else 0),
                                    if (p.listsRemove.isNotEmpty()) stringResource(R.string.sync_removed_names, names(p.listsRemove.map { it.name.ifBlank { it.url } })) else null,
                                ).joinToString("\n"),
                            )
                        }
                        p.rules?.let { rules -> GroupRow(stringResource(R.string.sync_part_rules), stringResource(R.string.sync_rules_replace, p.rulesBefore, rules.size)) }
                        if (p.rewritesAdd.isNotEmpty() || p.rewritesRemove.isNotEmpty() || p.rewritesToggle.isNotEmpty() || p.rewritesEnabled != null) {
                            GroupRow(
                                stringResource(R.string.sync_part_rewrites),
                                listOfNotNull(
                                    stringResource(R.string.sync_counts, p.rewritesAdd.size, p.rewritesRemove.size, p.rewritesToggle.size + if (p.rewritesEnabled != null) 1 else 0),
                                    if (p.rewritesRemove.isNotEmpty()) stringResource(R.string.sync_removed_names, names(p.rewritesRemove.map { it.domain })) else null,
                                ).joinToString("\n"),
                            )
                        }
                        if (p.services != null) GroupRow(stringResource(R.string.sync_part_services), stringResource(R.string.sync_services_replace, p.services.ids.size))
                        if (p.safeBrowsing != null || p.parental != null || p.safeSearch != null) {
                            GroupRow(stringResource(R.string.sync_part_protection), stringResource(R.string.sync_protection_change))
                        }
                        if (p.clientsAdd.isNotEmpty() || p.clientsRemove.isNotEmpty() || p.clientsUpdate.isNotEmpty()) {
                            GroupRow(
                                stringResource(R.string.sync_part_clients),
                                listOfNotNull(
                                    stringResource(R.string.sync_counts, p.clientsAdd.size, p.clientsRemove.size, p.clientsUpdate.size),
                                    if (p.clientsRemove.isNotEmpty()) stringResource(R.string.sync_removed_names, names(p.clientsRemove)) else null,
                                ).joinToString("\n"),
                            )
                        }
                    }
                    if (!p.isEmpty) {
                        val pr = progress
                        PillButton(
                            text = if (pr != null) stringResource(R.string.sync_copying, pr.first, pr.second) else stringResource(R.string.sync_copy_to, target.name),
                            onClick = { confirming = true },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                        )
                    }
                }
                if (failures.isNotEmpty()) {
                    Group(stringResource(R.string.sync_group_refused)) {
                        failures.forEach { f ->
                            GroupRow(
                                listOf(stringResource(partLabel(f.part)), f.subject).filter { it.isNotBlank() }.joinToString(": "),
                                errorMessage(context, f.error, target?.host.orEmpty()),
                                icon = Icons.Outlined.ErrorOutline,
                                iconTint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.sync_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
    }

    val p = plan
    if (confirming && p != null && target != null && source != null) {
        ConfirmDialog(
            title = stringResource(R.string.sync_confirm_title, target.name),
            body = stringResource(R.string.sync_confirm_body, source.name, target.name, p.steps) +
                if (p.removals > 0) "\n\n" + stringResource(R.string.sync_confirm_removals, p.removals, target.name) else "",
            confirmLabel = stringResource(R.string.sync_confirm),
            icon = Icons.Outlined.SyncAlt,
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                // Compared again right before copying, so what is sent matches what the servers hold now.
                compare { fresh -> if (!fresh.isEmpty) copy(fresh) }
            },
        )
    }
}
