package com.vknxworks.adctrl.ui.queries

import com.vknxworks.adctrl.ui.theme.JetBrainsMono
import androidx.compose.foundation.background
import com.vknxworks.adctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.adctrl.ui.components.CtrlSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.api.DnsAnswer
import com.vknxworks.adctrl.data.api.QueryLogItem
import com.vknxworks.adctrl.ui.components.formatMs

/** Everything about one query, with Block or Unblock, the client's queries and Check host. */
@Composable
fun QueryDetailSheet(
    item: QueryLogItem,
    listNames: Map<Long, String>,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onRule: (block: Boolean) -> Unit,
    onClientQueries: () -> Unit,
    onCheckHost: () -> Unit,
) {
    val context = LocalContext.current
    val kind = item.kind
    val cs = MaterialTheme.colorScheme
    CtrlSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(kind), null, Modifier.size(20.dp), tint = kindColor(kind))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(kindLabel(kind)), style = MaterialTheme.typography.labelLarge, color = kindColor(kind))
                Spacer(Modifier.weight(1f))
                Text(queryTime(item.time), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(item.domain, style = MaterialTheme.typography.titleLarge)
            if (item.domain != item.asciiDomain && item.asciiDomain.isNotBlank()) {
                Text(item.asciiDomain, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }

            if (!readOnly) {
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind.isBlocked) {
                        CtrlSecondaryButton(onClick = { onRule(false) }) {
                            Icon(Icons.Outlined.Verified, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_unblock))
                        }
                    } else {
                        CtrlSecondaryButton(onClick = { onRule(true) }) {
                            Icon(Icons.Outlined.Block, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_block))
                        }
                    }
                }
            }

            // Why: matched rules and where they came from.
            if (item.rules.isNotEmpty() || item.serviceName.isNotBlank()) {
                Section(stringResource(R.string.detail_why))
                if (item.serviceName.isNotBlank()) Line(stringResource(R.string.detail_service), item.serviceName)
                item.rules.forEach { r ->
                    if (r.text.isNotBlank()) Code(r.text)
                    Text(stringResource(R.string.detail_from, ruleSource(r, listNames)), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
            }

            Section(stringResource(R.string.detail_answer))
            if (item.answer.isEmpty()) {
                Text(stringResource(R.string.detail_no_answer), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            } else {
                item.answer.forEach { AnswerLine(it) }
            }
            if (item.originalAnswer.isNotEmpty() && item.originalAnswer != item.answer) {
                Section(stringResource(R.string.detail_original_answer))
                item.originalAnswer.forEach { AnswerLine(it) }
            }

            Section(stringResource(R.string.detail_request))
            Line(stringResource(R.string.detail_type), listOf(item.question.type, item.question.klass).filter { it.isNotBlank() }.joinToString(" · "))
            if (item.status.isNotBlank()) Line(stringResource(R.string.detail_status), item.status)
            item.elapsed?.let { Line(stringResource(R.string.detail_elapsed), formatMs(context, it / 1000)) }
            if (item.upstream.isNotBlank()) Line(stringResource(R.string.detail_upstream), item.upstream)
            Line(stringResource(R.string.detail_cached), stringResource(if (item.cached) R.string.yes else R.string.no))
            Line(stringResource(R.string.detail_dnssec), stringResource(if (item.answerDnssec) R.string.yes else R.string.no))
            if (item.ecs.isNotBlank()) Line(stringResource(R.string.detail_ecs), item.ecs)

            Section(stringResource(R.string.detail_client))
            item.clientInfo?.name?.takeIf { it.isNotBlank() }?.let { Line(stringResource(R.string.detail_client_name), it) }
            if (item.client.isNotBlank()) Line(stringResource(R.string.detail_client_ip), item.client)
            if (item.clientId.isNotBlank()) Line(stringResource(R.string.detail_client_id), item.clientId)
            Line(stringResource(R.string.detail_protocol), protocolLabel(item.clientProto))
            item.clientInfo?.whois?.let { w ->
                listOf(w.orgname, w.city, w.country).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { Line(stringResource(R.string.detail_whois), it.joinToString(", ")) }
            }
            if (item.clientInfo?.disallowed == true) Line(stringResource(R.string.detail_disallowed), item.clientInfo.disallowedRule.ifBlank { stringResource(R.string.yes) })

            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.client.isNotBlank() || item.clientId.isNotBlank()) {
                    CtrlSecondaryButton(onClick = onClientQueries) {
                        Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_client_queries))
                    }
                }
                CtrlSecondaryButton(onClick = onCheckHost) {
                    Icon(Icons.AutoMirrored.Outlined.FactCheck, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_check_host))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Code(text: String) {
    Text(
        text,
        fontFamily = JetBrainsMono,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(10.dp),
    )
}

@Composable
private fun AnswerLine(a: DnsAnswer) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(a.type, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Text(a.value, fontFamily = JetBrainsMono, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (a.ttl > 0) Text(stringResource(R.string.detail_ttl, a.ttl), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun protocolLabel(p: String): String = when (p.lowercase()) {
    "dot" -> stringResource(R.string.proto_dot)
    "doh" -> stringResource(R.string.proto_doh)
    "doq" -> stringResource(R.string.proto_doq)
    "dnscrypt" -> stringResource(R.string.proto_dnscrypt)
    else -> stringResource(R.string.proto_plain)
}
