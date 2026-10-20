package com.vknxworks.adctrl.ui.queries

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.rules.RuleBuilder
import com.vknxworks.adctrl.data.rules.RuleScope
import com.vknxworks.adctrl.ui.theme.Health

/**
 * Block or unblock a domain: pick how much of it (this host only, this host and its subdomains, or
 * the whole domain), optionally only for one client, see the exact rule, and save it to the custom
 * rules. Nothing is sent until Save.
 */
@Composable
fun RuleDialog(
    block: Boolean,
    host: String,
    client: String?,
    clientLabel: String?,
    writableServers: Int,
    onDismiss: () -> Unit,
    onSave: (rule: String, allServers: Boolean) -> Unit,
) {
    val registrable = remember(host) { RuleBuilder.registrable(host) }
    val scopes = buildList {
        add(RuleScope.EXACT)
        add(RuleScope.HOST_AND_SUBDOMAINS)
        if (registrable != host) add(RuleScope.WHOLE_DOMAIN)
    }
    var scope by remember { mutableStateOf(if (registrable != host) RuleScope.WHOLE_DOMAIN else RuleScope.HOST_AND_SUBDOMAINS) }
    var onlyClient by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf(false) }
    val rule = RuleBuilder.build(block, host, scope, if (onlyClient) client else null)
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (block) Icons.Filled.Block else Icons.Filled.Verified, null, tint = if (block) Health.critical else Health.ok) },
        title = { Text(stringResource(if (block) R.string.rule_block_title else R.string.rule_unblock_title, host)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                scopes.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = scope == s, role = Role.RadioButton) { scope = s }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = scope == s, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                when (s) {
                                    RuleScope.EXACT -> stringResource(R.string.scope_exact, host)
                                    RuleScope.HOST_AND_SUBDOMAINS -> stringResource(R.string.scope_host_sub, host)
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
                        Switch(onlyClient, onCheckedChange = { onlyClient = it })
                    }
                }
                if (writableServers > 1) {
                    Row(
                        Modifier.fillMaxWidth().clickable { all = !all }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.rule_all_servers, writableServers), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(all, onCheckedChange = { all = it })
                    }
                }
                Text(stringResource(R.string.rule_preview), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Text(
                    rule,
                    fontFamily = FontFamily.Monospace,
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
            TextButton(onClick = { onSave(rule, all) }) {
                Text(stringResource(if (block) R.string.rule_block_save else R.string.rule_unblock_save), color = if (block) cs.error else cs.primary)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
