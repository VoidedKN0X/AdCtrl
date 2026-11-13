package com.vknxworks.adctrl.ui.queries

import com.vknxworks.adctrl.ui.theme.JetBrainsMono
import androidx.compose.foundation.background
import com.vknxworks.adctrl.ui.components.CtrlAlertDialog
import com.vknxworks.adctrl.ui.components.CtrlSwitch
import com.vknxworks.adctrl.ui.components.CtrlTextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import android.content.Context
import com.vknxworks.adctrl.data.rules.RuleBuilder
import com.vknxworks.adctrl.data.rules.RuleOutcome
import com.vknxworks.adctrl.data.rules.RuleResult
import com.vknxworks.adctrl.data.rules.UserRules
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.data.rules.RuleScope
import com.vknxworks.adctrl.ui.theme.Health

/**
 * Block or allow a domain. The exact rule is shown before anything is saved. When one of your own
 * custom rules does the opposite, the dialog lists it and offers to remove it (on by default); a new
 * rule (whose scope and client you pick here) is then only added if the domain still isn't the way
 * you asked afterwards. Nothing is sent until the confirm button.
 *
 * [loadOpposing] reads which of your custom rules work against the rule shown, on the active server.
 */
@Composable
fun RuleDialog(
    block: Boolean,
    host: String,
    client: String?,
    clientLabel: String?,
    writableServers: Int,
    loadOpposing: suspend (rule: String) -> List<String>,
    onDismiss: () -> Unit,
    onSave: (rule: String, allServers: Boolean, removeOpposing: Boolean) -> Unit,
    onlyClientAtStart: Boolean = false,
) {
    val registrable = remember(host) { RuleBuilder.registrable(host) }
    val shownHost = remember(host) { RuleBuilder.normalizeHost(host) ?: host }
    val wildcard = remember(host) { RuleBuilder.isWildcard(host) }
    val scopes = buildList {
        if (!wildcard) add(RuleScope.EXACT)
        add(RuleScope.HOST_AND_SUBDOMAINS)
        if (registrable != shownHost) add(RuleScope.WHOLE_DOMAIN)
    }
    var scope by remember { mutableStateOf(if (registrable != shownHost) RuleScope.WHOLE_DOMAIN else RuleScope.HOST_AND_SUBDOMAINS) }
    var onlyClient by remember { mutableStateOf(onlyClientAtStart && !client.isNullOrBlank()) }
    var all by remember { mutableStateOf(false) }
    var removeOpposing by remember { mutableStateOf(true) }
    val rule = RuleBuilder.build(block, host, scope, if (onlyClient) client else null)
    var opposing by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(rule) {
        opposing = null
        opposing = if (rule == null) emptyList() else runCatching { loadOpposing(rule) }.getOrDefault(emptyList())
    }
    val cs = MaterialTheme.colorScheme

    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (block) Icons.Outlined.Block else Icons.Outlined.Verified, null, tint = if (block) Health.critical else Health.ok) },
        title = { Text(stringResource(if (block) R.string.rule_block_title else R.string.rule_unblock_title, shownHost)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (rule == null) {
                    Text(stringResource(R.string.rule_invalid_host, host), style = MaterialTheme.typography.bodyMedium, color = cs.error)
                    return@Column
                }
                scopes.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = scope == s, role = Role.RadioButton) { scope = s }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = scope == s, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                when (s) {
                                    RuleScope.EXACT -> stringResource(R.string.scope_exact, shownHost)
                                    RuleScope.HOST_AND_SUBDOMAINS -> stringResource(R.string.scope_host_sub, shownHost)
                                    RuleScope.WHOLE_DOMAIN -> stringResource(R.string.scope_domain, registrable)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                if (!client.isNullOrBlank()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onlyClient = !onlyClient }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.rule_only_client, clientLabel ?: client), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        CtrlSwitch(onlyClient, onCheckedChange = { onlyClient = it })
                    }
                }
                if (writableServers > 1) {
                    Row(
                        Modifier.fillMaxWidth().clickable { all = !all }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.rule_all_servers, writableServers), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        CtrlSwitch(all, onCheckedChange = { all = it })
                    }
                }

                val removing = opposing
                if (removing == null) {
                    CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), strokeWidth = 2.dp)
                    return@Column
                }
                if (removing.isNotEmpty()) {
                    Text(
                        stringResource(if (block) R.string.rule_found_allow else R.string.rule_found_block),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    removing.forEach { r ->
                        Text(
                            r,
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (removeOpposing) cs.errorContainer else cs.surfaceContainerHighest).padding(10.dp),
                            color = if (removeOpposing) cs.onErrorContainer else cs.onSurface,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { removeOpposing = !removeOpposing }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pluralStringResource(R.plurals.rule_remove_opposite, removing.size),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        CtrlSwitch(removeOpposing, onCheckedChange = { removeOpposing = it })
                    }
                    Text(
                        stringResource(
                            when {
                                !removeOpposing -> if (block) R.string.rule_keep_allow_warning else R.string.rule_keep_block_note
                                block -> R.string.rule_fallback_block
                                else -> R.string.rule_fallback_unblock
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!removeOpposing && block) cs.error else cs.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(if (removing.isEmpty() || !removeOpposing) R.string.rule_preview else R.string.rule_preview_fallback),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    rule,
                    fontFamily = JetBrainsMono,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest).padding(10.dp),
                )
                Text(
                    stringResource(if (block) R.string.rule_block_note else R.string.rule_unblock_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            CtrlTextButton(onClick = { rule?.let { onSave(it, all, removeOpposing) } }, enabled = rule != null && opposing != null) {
                Text(stringResource(if (block) R.string.rule_block_save else R.string.rule_unblock_save), color = if (block) cs.error else cs.primary)
            }
        },
        dismissButton = { CtrlTextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun pluralStringResource(id: Int, count: Int): String = androidx.compose.ui.res.pluralStringResource(id, count, count)

/** What the snackbar says after blocking or allowing. */
fun ruleResultMessage(context: Context, result: RuleResult, block: Boolean, domain: String): String {
    val failed = result.failed
    if (result.done.isEmpty() && failed.isNotEmpty()) return errorMessage(context, failed.first().second, failed.first().first.host)
    if (failed.isNotEmpty()) return context.getString(R.string.rule_failed_some, failed.joinToString(", ") { it.first.name })
    return when (result.outcome) {
        RuleOutcome.REMOVED -> context.getString(if (block) R.string.rule_result_removed_allow else R.string.rule_result_removed_block, domain)
        RuleOutcome.REMOVED_AND_ADDED -> context.getString(if (block) R.string.rule_result_both_block else R.string.rule_result_both_unblock, domain)
        RuleOutcome.ALREADY -> context.getString(if (block) R.string.rule_result_already_blocked else R.string.rule_result_already_unblocked, domain)
        RuleOutcome.ADDED -> context.getString(if (block) R.string.rule_blocked else R.string.rule_unblocked, domain)
        // Servers ended differently (for example removed on one, added on another).
        null -> context.getString(if (block) R.string.rule_blocked else R.string.rule_unblocked, domain)
    }
}

/**
 * Shows what blocking or allowing did, with Undo when something changed. Undo puts the custom rules
 * back as they were (on every server that was changed), keeping any other edits made since.
 */
suspend fun showRuleResult(
    context: Context,
    snackbar: SnackbarHostState,
    userRules: UserRules,
    result: RuleResult,
    block: Boolean,
    domain: String,
) {
    val message = ruleResultMessage(context, result, block, domain)
    if (!result.canUndo) {
        snackbar.showSnackbar(message)
        return
    }
    val answer = snackbar.showSnackbar(message, actionLabel = context.getString(R.string.undo), duration = SnackbarDuration.Long)
    if (answer != SnackbarResult.ActionPerformed) return
    val failed = userRules.undo(result)
    snackbar.showSnackbar(
        if (failed.isEmpty()) context.getString(R.string.rule_undone, domain)
        else errorMessage(context, failed.first().second, failed.first().first.host),
    )
}
