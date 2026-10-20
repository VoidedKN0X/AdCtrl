package com.vknxworks.adctrl.ui.clients

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.BlockedService
import com.vknxworks.adctrl.ui.components.PillButton
import com.vknxworks.adctrl.ui.components.errorMessage

/** Runs a change and shows what went wrong; true when it worked. */
suspend fun runClientAction(context: Context, snackbar: SnackbarHostState, host: String, success: String? = null, block: suspend () -> Unit): Boolean =
    try {
        block()
        success?.let { snackbar.showSnackbar(it) }
        true
    } catch (e: AghError) {
        snackbar.showSnackbar(errorMessage(context, e, host))
        false
    }

private val ID_LINE = Regex("""^[A-Za-z0-9.:/_\-]+$""")

/** Name, IDs (IP, CIDR, MAC or ClientID, one per line) and tags of a persistent client. */
@Composable
fun ClientEditSheet(
    title: String,
    initialName: String,
    initialIds: List<String>,
    supportedTags: List<String>,
    initialTags: List<String>,
    takenNames: Set<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, ids: List<String>, tags: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var idsText by remember { mutableStateOf(initialIds.joinToString("\n")) }
    var tags by remember { mutableStateOf(initialTags.toSet()) }
    val ids = idsText.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val badIds = ids.filterNot { ID_LINE.matches(it) }
    val nameTaken = name.trim().lowercase() in takenNames
    val ok = name.isNotBlank() && ids.isNotEmpty() && badIds.isEmpty() && !nameTaken
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                name, { name = it },
                label = { Text(stringResource(R.string.client_name)) },
                isError = nameTaken,
                supportingText = if (nameTaken) { { Text(stringResource(R.string.client_name_taken)) } } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                idsText, { idsText = it },
                label = { Text(stringResource(R.string.client_ids)) },
                supportingText = { Text(if (badIds.isNotEmpty()) stringResource(R.string.client_ids_bad, badIds.first()) else stringResource(R.string.client_ids_hint)) },
                isError = badIds.isNotEmpty(),
                minLines = 2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            if (supportedTags.isNotEmpty()) {
                Text(stringResource(R.string.client_tags), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    supportedTags.forEach { t ->
                        FilterChip(selected = t in tags, onClick = { tags = if (t in tags) tags - t else tags + t }, label = { Text(t) })
                    }
                }
            }
            PillButton(stringResource(R.string.save_changes), onClick = { onSave(name.trim(), ids, tags.sorted()) }, enabled = ok, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Picks the services blocked for one client. */
@Composable
fun ServicesPickerSheet(services: List<BlockedService>, initial: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var picked by remember { mutableStateOf(initial.toSet()) }
    var search by remember { mutableStateOf("") }
    val shown = services.filter { search.isBlank() || it.name.contains(search, true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.client_services_title, picked.size), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                search, { search = it },
                placeholder = { Text(stringResource(R.string.services_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(shown, key = { it.id }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = if (s.id in picked) picked - s.id else picked + s.id }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = s.id in picked, onCheckedChange = null)
                        Text(s.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            PillButton(stringResource(R.string.save_changes), onClick = { onSave(picked.sorted()) }, enabled = picked != initial.toSet(), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The client's own upstream DNS servers, one per line; empty means the global ones. */
@Composable
fun UpstreamsSheet(initial: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var text by remember { mutableStateOf(initial.joinToString("\n")) }
    val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.client_upstreams), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.client_upstreams_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                text, { text = it },
                placeholder = { Text("tls://dns.quad9.net\nhttps://dns.cloudflare.com/dns-query") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            PillButton(stringResource(R.string.save_changes), onClick = { onSave(lines) }, enabled = lines != initial, modifier = Modifier.fillMaxWidth())
        }
    }
}
