package com.vknxworks.adctrl.ui.queries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.CheckHostResult
import com.vknxworks.adctrl.data.api.QueryKind
import com.vknxworks.adctrl.ui.components.PillButton
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

private val QTYPES = listOf("A", "AAAA", "HTTPS", "CNAME", "TXT", "MX")

/**
 * "Would this be blocked?": asks AdGuard Home how it would answer a domain (optionally for a
 * client and query type) and shows the reason, the rules and the lists they came from.
 */
@Composable
fun CheckHostScreen(initialHost: String, initialClient: String) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    val queries by c.queries.state.collectAsStateWithLifecycle()
    val server = active?.server
    val clientParams = AghVersion.parse(server?.version.orEmpty()).checkHostClient
    var host by remember { mutableStateOf(initialHost) }
    var client by remember { mutableStateOf(initialClient) }
    var qtype by remember { mutableStateOf("A") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CheckHostResult?>(null) }
    var checked by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var rule by remember { mutableStateOf<Boolean?>(null) }
    val snackbar = remember { SnackbarHostState() }

    fun check() {
        val h = host.trim().trimEnd('.').lowercase()
        if (h.isEmpty()) return
        focus.clearFocus()
        busy = true
        error = null
        scope.launch {
            try {
                result = c.queries.checkHost(h, client, qtype)
                checked = h
            } catch (e: AghError) {
                error = errorMessage(context, e, server?.host.orEmpty())
                result = null
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { if (initialHost.isNotBlank()) check() }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.check_host_title))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = Gutter).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.check_host_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text(stringResource(R.string.check_host_domain)) },
                placeholder = { Text("example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { check() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (clientParams) {
                OutlinedTextField(
                    value = client,
                    onValueChange = { client = it.trim() },
                    label = { Text(stringResource(R.string.check_host_client)) },
                    placeholder = { Text(stringResource(R.string.check_host_client_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { check() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QTYPES.forEach { t -> FilterChip(selected = qtype == t, onClick = { qtype = t }, label = { Text(t) }) }
                }
            }
            PillButton(
                text = stringResource(R.string.check_host_button),
                onClick = ::check,
                enabled = !busy && host.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            result?.let { r -> CheckResultCard(checked, r, queries.listNames, readOnly = server?.readOnly != false, onRule = { rule = it }) }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
    }

    rule?.let { block ->
        RuleDialog(
            block = block,
            host = checked,
            client = client.ifBlank { null },
            clientLabel = null,
            writableServers = servers.count { !it.readOnly },
            onDismiss = { rule = null },
            onSave = { text, all ->
                rule = null
                scope.launch {
                    val res = c.userRules.add(text, all)
                    snackbar.showSnackbar(
                        when {
                            res.failed.isNotEmpty() -> context.getString(R.string.rule_failed_some, res.failed.joinToString(", ") { it.first.name })
                            res.added.isEmpty() -> context.getString(R.string.rule_already)
                            else -> context.getString(if (block) R.string.rule_blocked else R.string.rule_unblocked, checked)
                        },
                    )
                    check()
                }
            },
        )
    }
}

@Composable
private fun CheckResultCard(host: String, r: CheckHostResult, names: Map<Long, String>, readOnly: Boolean, onRule: (block: Boolean) -> Unit) {
    val kind = r.kind
    val cs = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(kind), null, Modifier.size(22.dp), tint = kindColor(kind))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(
                        when {
                            kind.isBlocked -> R.string.check_result_blocked
                            kind == QueryKind.ALLOWED -> R.string.check_result_allowed
                            kind == QueryKind.REWRITTEN -> R.string.check_result_rewritten
                            kind == QueryKind.SAFE_SEARCH -> R.string.check_result_safe_search
                            else -> R.string.check_result_not_blocked
                        },
                        host,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(stringResource(kindLabel(kind)), style = MaterialTheme.typography.bodySmall, color = kindColor(kind))
            if (r.serviceName.isNotBlank()) Text(stringResource(R.string.check_result_service, r.serviceName), style = MaterialTheme.typography.bodyMedium)
            r.rules.forEach { rule ->
                if (rule.text.isNotBlank()) {
                    Text(
                        rule.text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest).padding(10.dp),
                    )
                }
                Text(stringResource(R.string.detail_from, ruleSource(rule, names)), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (r.cname.isNotBlank()) Text(stringResource(R.string.check_result_cname, r.cname), style = MaterialTheme.typography.bodyMedium)
            if (r.ipAddrs.isNotEmpty()) Text(stringResource(R.string.check_result_ips, r.ipAddrs.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
            if (!readOnly) {
                Spacer(Modifier.height(4.dp))
                if (kind.isBlocked) {
                    FilledTonalButton(onClick = { onRule(false) }) {
                        Icon(Icons.Filled.Verified, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_unblock))
                    }
                } else if (kind != QueryKind.ALLOWED) {
                    FilledTonalButton(onClick = { onRule(true) }) {
                        Icon(Icons.Filled.Block, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_block))
                    }
                }
            }
        }
    }
}
