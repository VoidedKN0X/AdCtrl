package com.vknxworks.adctrl.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.adctrl.AppContainer
import com.vknxworks.adctrl.BuildConfig
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.notify.NotifyEvent
import com.vknxworks.adctrl.data.notify.NotifySettings
import com.vknxworks.adctrl.data.server.Server
import com.vknxworks.adctrl.data.server.ServerAddress
import com.vknxworks.adctrl.ui.lock.AppLock
import com.vknxworks.adctrl.data.settings.BackupFormatException
import com.vknxworks.adctrl.data.settings.BackupServer
import com.vknxworks.adctrl.data.settings.BackupSettings
import com.vknxworks.adctrl.data.settings.ImportPlan
import com.vknxworks.adctrl.data.settings.SettingsBackup
import com.vknxworks.adctrl.data.settings.SettingsBackups
import com.vknxworks.adctrl.data.settings.ThemeChoice
import com.vknxworks.adctrl.ui.components.ConfirmDialog
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/** Everything that goes into a settings file: the app settings and the saved servers, nothing secret. */
private fun currentBackup(c: AppContainer): SettingsBackup {
    val s = c.settings.settings.value
    val n = c.notifySettings.settings.value
    return SettingsBackup(
        appVersion = BuildConfig.VERSION_NAME,
        createdAt = OffsetDateTime.now().toString(),
        settings = BackupSettings(
            theme = s.theme.name,
            dynamicColor = s.dynamicColor,
            hideScreenshots = s.hideScreenshots,
            hideRecents = s.hideRecents,
            queriesLive = s.queriesLive,
            pauseLimitMinutes = n.pauseLimitMinutes,
            notifyEvents = n.events.map { it.name }.sorted(),
            notifyIntervalMinutes = n.intervalMinutes,
        ),
        servers = c.serverStore.servers.value.map { BackupServer(it.name, it.url, it.username, it.readOnly) },
    )
}

/**
 * Applies a settings file: the app settings it holds replace the ones here, and [add] (servers that
 * aren't here yet) are saved without a password, without a trusted certificate and with plain HTTP switched off. Nothing already saved
 * is changed or removed, the app lock is left alone, and hiding in screenshots or recent apps is only
 * ever switched on.
 */
private suspend fun applyImport(c: AppContainer, b: SettingsBackup, add: List<BackupServer>): Int {
    val s = b.settings
    c.settings.update { cur ->
        cur.copy(
            theme = s.theme?.let { n -> ThemeChoice.entries.firstOrNull { it.name == n } } ?: cur.theme,
            dynamicColor = s.dynamicColor ?: cur.dynamicColor,
            hideScreenshots = SettingsBackups.keepHidden(cur.hideScreenshots, s.hideScreenshots),
            hideRecents = SettingsBackups.keepHidden(cur.hideRecents, s.hideRecents),
            queriesLive = s.queriesLive ?: cur.queriesLive,
        )
    }
    c.notifySettings.update { cur ->
        cur.copy(
            events = s.notifyEvents?.mapNotNull { n -> NotifyEvent.entries.firstOrNull { it.name == n } }?.toSet() ?: cur.events,
            intervalMinutes = s.notifyIntervalMinutes?.takeIf { it in NotifySettings.INTERVALS } ?: cur.intervalMinutes,
            pauseLimitMinutes = s.pauseLimitMinutes?.takeIf { it in NotifySettings.PAUSE_LIMITS } ?: cur.pauseLimitMinutes,
        )
    }
    var added = 0
    for (item in add) {
        // The same checks as typing the address in: plain HTTP only for addresses on your own network.
        val parsed = ServerAddress.parse(item.url)
        val url = parsed.url
        if (url == null || parsed.problem != null) continue
        val saved = Server(
            id = UUID.randomUUID().toString(),
            name = item.name.ifBlank { url.host },
            url = ServerAddress.base(url),
            username = item.username,
            certPin = "",
            allowHttp = false,
            readOnly = item.readOnly,
        )
        c.serverStore.save(saved)
        if (c.serverStore.activeId.value == null) c.serverStore.setActive(saved.id)
        added++
    }
    return added
}

/**
 * Picks a settings file, shows what it would change and applies it after a confirmation. Returns the
 * action that opens the file picker; the dialogs live here.
 */
@Composable
fun rememberSettingsImport(): () -> Unit {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<SettingsBackup, ImportPlan>?>(null) }

    fun toast(id: Int) = Toast.makeText(context, id, Toast.LENGTH_LONG).show()

    fun runImport(b: SettingsBackup, add: List<BackupServer>) {
        scope.launch {
            runCatching { applyImport(c, b, add) }
                .onSuccess { n -> Toast.makeText(context, if (n > 0) context.getString(R.string.sx_imported_servers, n) else context.getString(R.string.sx_imported), Toast.LENGTH_LONG).show() }
                .onFailure { if (it !is kotlin.coroutines.cancellation.CancellationException) toast(R.string.sx_import_failed) }
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > SettingsBackups.MAX_BYTES) throw BackupFormatException(newer = false)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: throw IOException("no stream")
                }
                val b = SettingsBackups.parse(text)
                b to SettingsBackups.plan(b, c.serverStore.servers.value.map { it.url to it.username })
            }
            result.onSuccess { pending = it }.onFailure { e ->
                toast(if (e is BackupFormatException && e.newer) R.string.sx_too_new else R.string.sx_not_valid)
            }
        }
    }

    pending?.let { (b, plan) ->
        val lines = buildList {
            add(stringResource(R.string.sx_confirm_settings, b.createdAt?.take(10) ?: "?"))
            if (plan.toAdd.isNotEmpty()) add(stringResource(R.string.sx_confirm_add, plan.toAdd.joinToString("\n") { "• " + it.name.ifBlank { it.url } + " (" + it.url + ")" }))
            if (plan.alreadyHere.isNotEmpty()) add(stringResource(R.string.sx_confirm_kept, plan.alreadyHere.joinToString(", ") { it.name.ifBlank { it.url } }))
            add(stringResource(R.string.sx_confirm_never))
        }
        ConfirmDialog(
            title = stringResource(R.string.sx_confirm_title),
            body = lines.joinToString("\n\n"),
            confirmLabel = stringResource(R.string.sx_import),
            danger = false,
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                runImport(b, plan.toAdd)
            },
        )
    }

    return {
        AppLock.expectReturn()
        open.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
    }
}

/** Settings › Export and import: a JSON file in a place the user picks. */
@Composable
fun BackupGroup() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val importSettings = rememberSettingsImport()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = SettingsBackups.encode(currentBackup(c))
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IOException("no stream")
                }
            }.isSuccess
            Toast.makeText(context, if (ok) R.string.sx_exported else R.string.sx_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    Group(stringResource(R.string.sx_title), footer = stringResource(R.string.sx_footer)) {
        GroupRow(
            stringResource(R.string.sx_export),
            stringResource(R.string.sx_export_sub),
            icon = Icons.Outlined.FileUpload,
            onClick = {
                AppLock.expectReturn()
                save.launch("adctrl-settings-${LocalDate.now()}.json")
            },
        )
        GroupRow(
            stringResource(R.string.sx_import),
            stringResource(R.string.sx_import_sub),
            icon = Icons.Outlined.FileDownload,
            onClick = importSettings,
        )
    }
}

/** "Import settings from a file" under the first-start setup form, for moving to a new phone. */
@Composable
fun SettingsImportButton(modifier: Modifier = Modifier) {
    val importSettings = rememberSettingsImport()
    TextButton(onClick = importSettings, modifier = modifier) { Text(stringResource(R.string.sx_import_setup)) }
}
