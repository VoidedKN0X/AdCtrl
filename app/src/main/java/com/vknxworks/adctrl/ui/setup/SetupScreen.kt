package com.vknxworks.adctrl.ui.setup

import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.vknxworks.adctrl.ui.theme.CtrlColors
import com.vknxworks.adctrl.ui.components.CtrlWordmark
import androidx.compose.foundation.layout.Arrangement
import com.vknxworks.adctrl.ui.components.CtrlTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghClient
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.ServerStatus
import com.vknxworks.adctrl.data.server.AddressProblem
import com.vknxworks.adctrl.data.server.Server
import com.vknxworks.adctrl.data.server.ServerAddress
import com.vknxworks.adctrl.data.server.ServerSecrets
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.components.PillButton
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import com.vknxworks.adctrl.ui.components.SecureScreen
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** Everything learned from a successful connection, shown before the server is saved. */
private class Connected(val server: Server, val secrets: ServerSecrets, val status: ServerStatus)

private class CertPrompt(val cert: CertInfo, val changed: Boolean)

/**
 * Adds an AdGuard Home server, or changes the address or sign-in of an existing one ([editId]).
 *
 * 1. GET /control/status with the username and password: that shows whether the address is
 *    AdGuard Home, whether the sign-in works and which version it runs. An untrusted certificate is
 *    shown by its SHA-256 fingerprint to confirm and pin first (nothing has been sent at that point).
 * 2. A version older than [AghVersion.MINIMUM] is refused.
 * 3. The result is shown, and the server is saved on Continue.
 *
 * Only the address typed here is ever used for this server; there is no fallback address.
 */
@Composable
fun SetupScreen(editId: String? = null, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val existing = remember(editId) { editId?.let { id -> c.sessions.servers.value.firstOrNull { it.id == id } } }
    val existingSecrets = remember(editId) { editId?.let { c.sessions.secrets(it) } }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var address by remember { mutableStateOf(existing?.url.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var allowHttp by remember { mutableStateOf(existing?.allowHttp ?: false) }
    var httpPrompt by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var certPrompt by remember { mutableStateOf<CertPrompt?>(null) }
    var connected by remember { mutableStateOf<Connected?>(null) }
    var readOnly by remember { mutableStateOf(existing?.readOnly ?: false) }

    // A certificate the user trusted in this session, or the one already pinned for this server.
    var pinnedHost by remember { mutableStateOf(existing?.host) }
    var pin by remember { mutableStateOf(existing?.certPin.orEmpty()) }

    val parsed = ServerAddress.parse(address)
    val resolved: HttpUrl? = parsed.url?.takeIf { parsed.problem == null }
    val isHttp = resolved?.isHttps == false
    val addressError = when {
        address.isBlank() -> null
        parsed.problem == AddressProblem.HTTP_PUBLIC -> stringResource(R.string.address_http_public)
        parsed.problem == AddressProblem.INVALID -> stringResource(R.string.address_invalid)
        else -> null
    }
    // The saved password is only ever sent to the address and user it was saved for. Changing either
    // needs the password typed again, so an edited address can't receive the old password.
    val canReusePassword = existing != null && existingSecrets?.password?.isNotEmpty() == true &&
        resolved != null && ServerAddress.base(resolved) == existing.url && username.trim() == existing.username
    val canConnect = !busy && resolved != null && username.isNotBlank() &&
        (password.isNotEmpty() || canReusePassword) && (!isHttp || allowHttp)

    fun pinFor(url: HttpUrl): String = if (url.isHttps && pinnedHost.equals(url.host, ignoreCase = true)) pin else ""

    // Server setup is the one place an address that isn't saved yet may be contacted; never after leaving it.
    DisposableEffect(Unit) { onDispose { c.allowlist.endSetup() } }

    fun connect() {
        val url = resolved ?: return
        val base = ServerAddress.base(url)
        val secret = password.ifEmpty { if (canReusePassword) existingSecrets?.password.orEmpty() else "" }
        if (secret.isEmpty()) {
            error = context.getString(R.string.password_reenter)
            return
        }
        busy = true
        error = null
        val draft = Server(
            id = existing?.id.orEmpty(),
            name = name.trim(),
            url = base,
            username = username.trim(),
            certPin = pinFor(url),
            allowHttp = isHttp && allowHttp,
            readOnly = readOnly,
        )
        val secrets = ServerSecrets(secret)
        c.allowlist.beginSetup(url, draft.allowHttp)
        scope.launch {
            try {
                val status = AghClient(draft, c.http, secrets).status()
                val version = AghVersion.parse(status.version)
                if (!version.supported) throw AghError.TooOld(status.version)
                connected = Connected(
                    server = draft.copy(name = name.trim().ifBlank { url.host }, version = status.version),
                    secrets = secrets,
                    status = status,
                )
            } catch (e: AghError.UntrustedCertificate) {
                askToTrust(url, changed = false, onCert = { certPrompt = it }, onFail = { error = errorMessage(context, it, url.host) })
            } catch (e: AghError.PinMismatch) {
                askToTrust(url, changed = true, onCert = { certPrompt = it }, onFail = { error = errorMessage(context, it, url.host) })
            } catch (e: Exception) {
                error = errorMessage(context, e, url.host)
            } finally {
                busy = false
            }
        }
    }

    fun save() {
        val result = connected ?: return
        c.sessions.add(result.server.copy(readOnly = readOnly), result.secrets)
        c.allowlist.endSetup()
        onDone()
    }

    // The password is typed here: never in screenshots or Recents.
    SecureScreen()

    val result = connected
    Column(Modifier.fillMaxSize()) {
        if (onCancel != null) {
            ScreenTopBar(stringResource(if (editId != null) R.string.edit_server_title else R.string.add_server))
        } else {
            Spacer(Modifier.statusBarsPadding())
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            if (result == null) {
                if (onCancel == null) {
                    Column(Modifier.padding(horizontal = Gutter, vertical = 16.dp)) {
                        CtrlWordmark()
                        Spacer(Modifier.height(28.dp))
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(CtrlColors.Accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Shield, null, Modifier.size(32.dp), tint = CtrlColors.OnAccent)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CtrlTextField(
                        value = address,
                        onValueChange = { address = it.trim(); error = null; connected = null },
                        label = { Text(stringResource(R.string.field_address)) },
                        placeholder = { Text(stringResource(R.string.field_address_hint)) },
                        singleLine = true,
                        isError = addressError != null,
                        supportingText = {
                            when {
                                addressError != null -> Text(addressError)
                                resolved != null -> Text(stringResource(R.string.address_resolves, ServerAddress.base(resolved)))
                                else -> Text(stringResource(R.string.address_only_this))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CtrlTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.field_name)) },
                        placeholder = { Text(stringResource(R.string.field_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CtrlTextField(
                        value = username,
                        onValueChange = { username = it.trim() },
                        label = { Text(stringResource(R.string.field_username)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CtrlTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.field_password)) },
                        placeholder = if (canReusePassword) {
                            { Text("••••••••") }
                        } else {
                            null
                        },
                        supportingText = if (existing != null && !canReusePassword && password.isEmpty()) {
                            { Text(stringResource(R.string.password_reenter)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isHttp) {
                    Group(footer = stringResource(R.string.http_switch_footer)) {
                        GroupSwitch(
                            stringResource(R.string.http_switch),
                            stringResource(R.string.http_switch_subtitle),
                            checked = allowHttp,
                            icon = Icons.Outlined.Http,
                        ) { on -> if (on) httpPrompt = true else allowHttp = false }
                    }
                }
                Column(Modifier.padding(horizontal = Gutter)) {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    PillButton(
                        text = stringResource(if (busy) R.string.connecting else R.string.connect),
                        onClick = ::connect,
                        enabled = canConnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                ConnectedSummary(result, readOnly, onReadOnly = { readOnly = it })
                PillButton(
                    text = stringResource(if (editId != null) R.string.save_changes else R.string.continue_label),
                    onClick = ::save,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                )
            }
        }
    }

    certPrompt?.let { prompt ->
        CertTrustDialog(
            cert = prompt.cert,
            changed = prompt.changed,
            onTrust = {
                certPrompt = null
                pinnedHost = prompt.cert.host
                pin = prompt.cert.fingerprint
                connect()
            },
            onCancel = { certPrompt = null },
        )
    }
    if (httpPrompt) {
        HttpWarningDialog(
            host = resolved?.host.orEmpty(),
            onAllow = { allowHttp = true; httpPrompt = false },
            onCancel = { httpPrompt = false },
        )
    }
}

/** Reads the certificate the server presents and asks the user to confirm it. */
private suspend fun askToTrust(url: HttpUrl, changed: Boolean, onCert: (CertPrompt) -> Unit, onFail: (AghError) -> Unit) {
    try {
        onCert(CertPrompt(fetchCertInfo(url), changed))
    } catch (e: java.io.IOException) {
        onFail(AghError.fromIo(e))
    } catch (e: Exception) {
        onFail(AghError.UntrustedCertificate(e))
    }
}

@Composable
private fun ConnectedSummary(result: Connected, readOnly: Boolean, onReadOnly: (Boolean) -> Unit) {
    val server = result.server
    val status = result.status
    Column(Modifier.padding(horizontal = Gutter, vertical = 16.dp)) {
        Icon(Icons.Outlined.Dns, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.connected_to, server.name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            listOf(
                stringResource(R.string.agh_version, status.version),
                stringResource(R.string.signed_in_as, server.username),
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Group {
        GroupRow(
            stringResource(if (status.protectionEnabled) R.string.protection_on else R.string.protection_off),
            dnsSummary(status),
            icon = Icons.Outlined.Shield,
        )
        GroupRow(
            server.displayUrl,
            stringResource(
                when {
                    server.isHttp -> R.string.conn_http
                    server.certPin.isNotBlank() -> R.string.cert_pinned
                    else -> R.string.cert_system
                },
            ),
            icon = if (server.isHttp) Icons.Outlined.Http else Icons.Outlined.Lock,
        )
    }
    Group {
        GroupSwitch(
            stringResource(R.string.read_only_mode),
            stringResource(R.string.read_only_mode_subtitle),
            checked = readOnly,
            onChange = onReadOnly,
        )
    }
}

/** "DNS on 192.168.1.2, fd00::2 (port 53)" from the status. */
@Composable
fun dnsSummary(status: ServerStatus): String {
    val addresses = status.dnsAddresses.take(3).joinToString(", ")
    return if (addresses.isBlank()) stringResource(R.string.dns_port_only, status.dnsPort)
    else stringResource(R.string.dns_on, addresses, status.dnsPort)
}
