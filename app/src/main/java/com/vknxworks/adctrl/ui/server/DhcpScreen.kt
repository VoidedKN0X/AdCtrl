package com.vknxworks.adctrl.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.DhcpInfo
import com.vknxworks.adctrl.data.api.DhcpLease
import com.vknxworks.adctrl.data.api.DhcpStaticLease
import com.vknxworks.adctrl.data.api.addStaticLease
import com.vknxworks.adctrl.data.api.dhcpInfo
import com.vknxworks.adctrl.data.api.removeStaticLease
import com.vknxworks.adctrl.data.api.updateStaticLease
import com.vknxworks.adctrl.ui.components.ConfirmDialog
import com.vknxworks.adctrl.ui.components.CtrlAlertDialog
import com.vknxworks.adctrl.ui.components.CtrlTextButton
import com.vknxworks.adctrl.ui.components.CtrlTextField
import com.vknxworks.adctrl.ui.components.EmptyState
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.ui.components.relativeTime
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

private val MAC = Regex("""^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$""")
private val IPV4 = Regex("""^(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}$""")
private val HOSTNAME = Regex("""^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$""")

/** What can be typed for a static lease: a MAC address, an IPv4 address and a plain host name (or none). */
internal fun leaseProblem(mac: String, ip: String, hostname: String): Boolean =
    !MAC.matches(mac) || !IPV4.matches(ip) || !(hostname.isEmpty() || HOSTNAME.matches(hostname))

/** A static lease being added ([existing] false) or changed. */
private data class LeaseEdit(val existing: Boolean, val mac: String, val ip: String, val hostname: String)

/** Seconds as "12 hours" / "1 day" style text. */
@Composable
private fun leaseTime(seconds: Long): String = when {
    seconds <= 0 -> "—"
    seconds % 86_400 == 0L -> stringResource(R.string.dhcp_days, seconds / 86_400)
    seconds % 3_600 == 0L -> stringResource(R.string.dhcp_hours, seconds / 3_600)
    else -> stringResource(R.string.dhcp_minutes, seconds / 60)
}

/**
 * AdGuard Home's own DHCP server on the active server: its range, the leases it handed out and the
 * static leases, with add, change and remove. Only reachable while that DHCP server is switched on;
 * if it turns out to be off (or missing on this system), the screen says so and offers nothing.
 */
@Composable
fun DhcpScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly == true
    val canUpdate = AghVersion.parse(server?.version.orEmpty()).dhcpUpdateLease
    val snackbar = remember { SnackbarHostState() }

    var info by remember { mutableStateOf<DhcpInfo?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LeaseEdit?>(null) }
    var removing by remember { mutableStateOf<DhcpLease?>(null) }

    fun host() = server?.host.orEmpty()

    fun load() {
        val client = active?.client
        if (client == null) {
            loading = false
            return
        }
        loading = true
        loadError = null
        scope.launch {
            try {
                info = client.dhcpInfo()
            } catch (e: AghError) {
                loadError = e
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(server?.id) {
        info = null
        load()
    }

    /** Runs a change, reloads the leases and says how it went. */
    fun change(done: Int, action: suspend () -> Unit) {
        val client = active?.client ?: return
        busy = true
        scope.launch {
            try {
                action()
                runCatching { info = client.dhcpInfo() }
                snackbar.showSnackbar(context.getString(done))
            } catch (e: AghError) {
                snackbar.showSnackbar(errorMessage(context, e, host()))
            } finally {
                busy = false
            }
        }
    }

    val d = info
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                stringResource(R.string.dhcp_title),
                actions = {
                    if (d != null && d.enabled && !readOnly) {
                        IconButton(onClick = { editing = LeaseEdit(false, "", "", "") }, enabled = !busy) { Icon(Icons.Outlined.Add, stringResource(R.string.dhcp_static_add)) }
                    }
                },
            )
            when {
                d == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                d != null && !d.enabled -> EmptyState(
                    icon = Icons.Outlined.Lan,
                    title = stringResource(R.string.dhcp_off_title),
                    body = stringResource(R.string.dhcp_off_body),
                )
                d != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    if (readOnly) {
                        Text(
                            stringResource(R.string.dns_read_only),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    Group(stringResource(R.string.dhcp_server), footer = stringResource(R.string.dhcp_server_footer)) {
                        if (d.interfaceName.isNotBlank()) GroupRow(stringResource(R.string.dhcp_interface), d.interfaceName, icon = Icons.Outlined.Lan)
                        if (d.v4.rangeStart.isNotBlank()) {
                            GroupRow(stringResource(R.string.dhcp_range), d.v4.rangeStart + " – " + d.v4.rangeEnd)
                            GroupRow(stringResource(R.string.dhcp_gateway), listOf(d.v4.gatewayIp, d.v4.subnetMask).filter { it.isNotBlank() }.joinToString(" / "))
                            GroupRow(stringResource(R.string.dhcp_lease_time), leaseTime(d.v4.leaseDuration))
                        }
                        if (d.v6.rangeStart.isNotBlank()) GroupRow(stringResource(R.string.dhcp_range_v6), d.v6.rangeStart)
                    }
                    Group(stringResource(R.string.dhcp_static, d.staticLeases.size)) {
                        if (d.staticLeases.isEmpty()) GroupRow(stringResource(R.string.dhcp_static_none))
                        d.staticLeases.forEach { l ->
                            GroupRow(
                                l.hostname.ifBlank { l.ip },
                                listOf(l.ip, l.mac).filter { it.isNotBlank() }.joinToString(" · "),
                                icon = Icons.Outlined.PushPin,
                                onClick = if (!readOnly && !busy && canUpdate) ({ editing = LeaseEdit(true, l.mac, l.ip, l.hostname) }) else null,
                                trailing = if (!readOnly) ({ IconButton(onClick = { removing = l }, enabled = !busy) { Icon(Icons.Outlined.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error) } }) else null,
                            )
                        }
                    }
                    Group(stringResource(R.string.dhcp_leases, d.leases.size), footer = if (readOnly) null else stringResource(R.string.dhcp_leases_footer)) {
                        if (d.leases.isEmpty()) GroupRow(stringResource(R.string.dhcp_leases_none))
                        d.leases.sortedBy { it.hostname.lowercase() }.forEach { l ->
                            val until = runCatching { OffsetDateTime.parse(l.expires).toInstant().toEpochMilli() }.getOrNull()
                            GroupRow(
                                l.hostname.ifBlank { l.ip },
                                listOfNotNull(l.ip, l.mac, until?.let { stringResource(R.string.dhcp_expires, relativeTime(it)) }).filter { it.isNotBlank() }.joinToString(" · "),
                                onClick = if (!readOnly && !busy && IPV4.matches(l.ip)) ({ editing = LeaseEdit(false, l.mac, l.ip, l.hostname) }) else null,
                            )
                        }
                    }
                }
                else -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.dhcp_load_failed),
                    // 501: this AdGuard Home runs on a system where its DHCP server doesn't exist.
                    body = loadError?.let { e -> if (e is AghError.Server && e.code == 501) stringResource(R.string.dhcp_unsupported) else errorMessage(context, e, host()) }.orEmpty(),
                    action = { CtrlTextButton(onClick = ::load) { Text(stringResource(R.string.retry)) } },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
    }

    editing?.let { e ->
        var mac by remember(e) { mutableStateOf(e.mac) }
        var ip by remember(e) { mutableStateOf(e.ip) }
        var name by remember(e) { mutableStateOf(e.hostname) }
        val bad = leaseProblem(mac.trim(), ip.trim(), name.trim())
        CtrlAlertDialog(
            onDismissRequest = { editing = null },
            icon = { Icon(Icons.Outlined.PushPin, null) },
            title = { Text(stringResource(if (e.existing) R.string.dhcp_static_edit else R.string.dhcp_static_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CtrlTextField(value = mac, onValueChange = { mac = it.trim() }, enabled = !e.existing, label = { Text(stringResource(R.string.dhcp_field_mac)) }, isError = mac.isNotEmpty() && !MAC.matches(mac), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                    CtrlTextField(value = ip, onValueChange = { ip = it.trim() }, label = { Text(stringResource(R.string.dhcp_field_ip)) }, isError = ip.isNotEmpty() && !IPV4.matches(ip), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    CtrlTextField(value = name, onValueChange = { name = it.trim() }, label = { Text(stringResource(R.string.dhcp_field_hostname)) }, isError = name.isNotEmpty() && !HOSTNAME.matches(name), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                CtrlTextButton(
                    onClick = {
                        editing = null
                        val lease = DhcpStaticLease(mac.trim(), ip.trim(), name.trim())
                        val client = active?.client
                        if (client != null) {
                            change(R.string.dhcp_static_saved) { if (e.existing) client.updateStaticLease(lease) else client.addStaticLease(lease) }
                        }
                    },
                    enabled = !bad,
                ) { Text(stringResource(R.string.dhcp_save)) }
            },
            dismissButton = { CtrlTextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    removing?.let { l ->
        ConfirmDialog(
            title = stringResource(R.string.dhcp_static_remove),
            body = stringResource(R.string.dhcp_static_remove_body, l.hostname.ifBlank { l.mac }, l.ip),
            confirmLabel = stringResource(R.string.remove),
            icon = Icons.Outlined.Delete,
            onDismiss = { removing = null },
            onConfirm = {
                removing = null
                val client = active?.client
                if (client != null) change(R.string.dhcp_static_removed) { client.removeStaticLease(DhcpStaticLease(l.mac, l.ip, l.hostname)) }
            },
        )
    }
}
