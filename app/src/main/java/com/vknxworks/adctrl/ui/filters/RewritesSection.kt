package com.vknxworks.adctrl.ui.filters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.api.Rewrite
import com.vknxworks.adctrl.ui.components.ConfirmDialog
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.components.LocalBottomClearance
import com.vknxworks.adctrl.ui.components.PillButton
import com.vknxworks.adctrl.ui.theme.Gutter

/** What the rewrites section can do; implemented by the Filters screen with the repository. */
interface RewriteActions {
    fun add(r: Rewrite)
    fun update(old: Rewrite, new: Rewrite)
    fun delete(r: Rewrite)
    fun setAllEnabled(on: Boolean)
}

private sealed class RewriteEdit {
    data object New : RewriteEdit()
    data class Existing(val rewrite: Rewrite) : RewriteEdit()
}

/** DNS rewrites: domain → IP address or CNAME, for example to give local services a name. */
@Composable
fun RewritesSection(
    rewrites: List<Rewrite>,
    allEnabled: Boolean?,
    canToggle: Boolean,
    readOnly: Boolean,
    actions: RewriteActions,
) {
    var search by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<RewriteEdit?>(null) }
    var deleting by remember { mutableStateOf<Rewrite?>(null) }
    val shown = rewrites.filter { search.isBlank() || it.domain.contains(search, true) || it.answer.contains(search, true) }
        .sortedBy { it.domain.lowercase() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomClearance.current + 16.dp)) {
        if (allEnabled != null) {
            item(key = "all") {
                Group {
                    if (readOnly) {
                        GroupRow(stringResource(if (allEnabled) R.string.rewrites_on else R.string.rewrites_off), icon = Icons.Filled.SwapHoriz)
                    } else {
                        GroupSwitch(stringResource(R.string.rewrites_enabled), stringResource(R.string.rewrites_enabled_subtitle), checked = allEnabled, icon = Icons.Filled.SwapHoriz) {
                            actions.setAllEnabled(it)
                        }
                    }
                }
            }
        }
        item(key = "bar") {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.rewrites_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = if (search.isNotEmpty()) {
                        { IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear)) } }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (!readOnly) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { edit = RewriteEdit.New }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.rewrite_add))
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(if (rewrites.isEmpty()) R.string.rewrites_empty else R.string.rewrites_none_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 12.dp),
                )
            }
        } else {
            item(key = "list") {
                Group {
                    shown.forEach { r ->
                        GroupRow(
                            r.domain,
                            "→ " + answerLabel(r.answer),
                            onClick = if (readOnly) null else { { edit = RewriteEdit.Existing(r) } },
                            titleColor = if (r.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            trailing = if (readOnly || !canToggle) {
                                null
                            } else {
                                { Switch(checked = r.enabled, onCheckedChange = { on -> actions.update(r, r.copy(enabled = on)) }) }
                            },
                        )
                    }
                }
            }
        }
    }

    edit?.let { e ->
        val existing = (e as? RewriteEdit.Existing)?.rewrite
        RewriteSheet(
            existing = existing,
            taken = rewrites.map { it.key }.toSet() - setOfNotNull(existing?.key),
            onDismiss = { edit = null },
            onSave = { r ->
                edit = null
                if (existing == null) actions.add(r) else actions.update(existing, r.copy(enabled = existing.enabled))
            },
            onDelete = { edit = null; deleting = existing },
        )
    }
    deleting?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.rewrite_delete_title, r.domain),
            body = stringResource(R.string.rewrite_delete_body, r.domain, answerLabel(r.answer)),
            confirmLabel = stringResource(R.string.remove),
            onDismiss = { deleting = null },
            onConfirm = { deleting = null; actions.delete(r) },
        )
    }
}

/** "A" and "AAAA" mean "keep the upstream's answer for this type". */
@Composable
private fun answerLabel(a: String): String = when (a.uppercase()) {
    "A" -> stringResource(R.string.rewrite_keep_a)
    "AAAA" -> stringResource(R.string.rewrite_keep_aaaa)
    else -> a
}

private val IPV4 = Regex("""^(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}$""")
private val DOMAIN = Regex("""^(\*\.)?([A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?\.)*[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?\.?$""")

private fun validAnswer(a: String): Boolean {
    val t = a.trim()
    return t.equals("A", true) || t.equals("AAAA", true) || IPV4.matches(t) || (t.contains(':') && t.all { it.isLetterOrDigit() || it == ':' || it == '.' }) || DOMAIN.matches(t)
}

@Composable
private fun RewriteSheet(existing: Rewrite?, taken: Set<String>, onDismiss: () -> Unit, onSave: (Rewrite) -> Unit, onDelete: () -> Unit) {
    var domain by remember { mutableStateOf(existing?.domain.orEmpty()) }
    var answer by remember { mutableStateOf(existing?.answer.orEmpty()) }
    val d = domain.trim().lowercase()
    val a = answer.trim()
    val domainOk = d.isNotEmpty() && DOMAIN.matches(d)
    val answerOk = a.isNotEmpty() && validAnswer(a)
    val duplicate = "$d\u0000$a" in taken
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (existing == null) R.string.rewrite_add else R.string.rewrite_edit), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                domain,
                { domain = it.trim() },
                label = { Text(stringResource(R.string.rewrite_domain)) },
                placeholder = { Text("nas.home.arpa") },
                supportingText = { Text(stringResource(R.string.rewrite_domain_hint)) },
                isError = domain.isNotBlank() && !domainOk,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                answer,
                { answer = it.trim() },
                label = { Text(stringResource(R.string.rewrite_answer)) },
                placeholder = { Text("192.168.1.10") },
                supportingText = { Text(if (duplicate) stringResource(R.string.rewrite_duplicate) else stringResource(R.string.rewrite_answer_hint)) },
                isError = (answer.isNotBlank() && !answerOk) || duplicate,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            PillButton(
                stringResource(if (existing == null) R.string.rewrite_add else R.string.save_changes),
                onClick = { onSave(Rewrite(d, a, existing?.enabled ?: true)) },
                enabled = domainOk && answerOk && !duplicate && (existing == null || existing.domain != d || existing.answer != a),
                modifier = Modifier.fillMaxWidth(),
            )
            if (existing != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.rewrite_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}
