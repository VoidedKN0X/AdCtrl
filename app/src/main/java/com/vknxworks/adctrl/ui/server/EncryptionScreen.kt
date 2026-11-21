package com.vknxworks.adctrl.ui.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.CertState
import com.vknxworks.adctrl.data.api.TlsStatus
import com.vknxworks.adctrl.data.api.certState
import com.vknxworks.adctrl.data.api.daysUntil
import com.vknxworks.adctrl.data.api.tlsStatus
import com.vknxworks.adctrl.ui.components.CtrlTextButton
import com.vknxworks.adctrl.ui.components.EmptyState
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.ui.theme.CtrlColors
import com.vknxworks.adctrl.ui.theme.Health
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun parseTime(raw: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(raw) }.getOrNull()

private fun showDate(t: OffsetDateTime): String =
    t.atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/**
 * AdGuard Home's encryption settings, to look at: whether encryption is on, the ports for HTTPS,
 * DNS-over-TLS and DNS-over-QUIC, and the certificate with its validity and the days left. Nothing
 * here can be changed; certificates are set up in AdGuard Home's own web interface.
 */
@Composable
fun EncryptionScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server

    var tls by remember { mutableStateOf<TlsStatus?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var loading by remember { mutableStateOf(true) }

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
                tls = client.tlsStatus()
            } catch (e: AghError) {
                loadError = e
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(server?.id) {
        tls = null
        load()
    }

    val t = tls
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.enc_title))
        when {
            t == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            t != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                Group(stringResource(R.string.enc_group_status)) {
                    GroupRow(
                        stringResource(R.string.enc_encryption),
                        stringResource(if (t.enabled) R.string.enc_on else R.string.enc_off),
                        icon = if (t.enabled) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        iconTint = if (t.enabled) Health.ok else CtrlColors.Muted,
                    )
                    if (t.serverName.isNotBlank()) GroupRow(stringResource(R.string.enc_server_name), t.serverName)
                    if (t.enabled) {
                        GroupRow(stringResource(R.string.enc_https), if (t.portHttps > 0) stringResource(R.string.enc_port, t.portHttps) + if (t.forceHttps) " · " + stringResource(R.string.enc_force_https) else "" else stringResource(R.string.state_off))
                        GroupRow(stringResource(R.string.enc_dot), if (t.portDnsOverTls > 0) stringResource(R.string.enc_port, t.portDnsOverTls) else stringResource(R.string.state_off))
                        GroupRow(stringResource(R.string.enc_doq), if (t.portDnsOverQuic > 0) stringResource(R.string.enc_port, t.portDnsOverQuic) else stringResource(R.string.state_off))
                        GroupRow(stringResource(R.string.enc_plain_dns), stringResource(if (t.servePlainDns) R.string.state_on else R.string.state_off))
                    }
                }
                if (t.hasCertificate) {
                    val notAfter = parseTime(t.notAfter)
                    val notBefore = parseTime(t.notBefore)
                    val now = System.currentTimeMillis()
                    Group(stringResource(R.string.enc_group_cert)) {
                        if (notAfter != null) {
                            val state = certState(notBefore?.toInstant()?.toEpochMilli(), notAfter.toInstant().toEpochMilli(), now)
                            val days = daysUntil(notAfter.toInstant().toEpochMilli(), now).toInt().coerceAtLeast(0)
                            GroupRow(
                                stringResource(R.string.enc_expiry),
                                when (state) {
                                    CertState.EXPIRED -> stringResource(R.string.enc_expired, showDate(notAfter))
                                    CertState.NOT_YET_VALID -> stringResource(R.string.enc_not_yet, notBefore?.let { showDate(it) }.orEmpty())
                                    else -> pluralStringResource(R.plurals.enc_days_left, days, days, showDate(notAfter))
                                },
                                icon = if (state == CertState.VALID) Icons.Outlined.EventAvailable else Icons.Outlined.Warning,
                                iconTint = when (state) {
                                    CertState.VALID -> Health.ok
                                    CertState.EXPIRES_SOON -> Health.warn
                                    else -> Health.critical
                                },
                                titleColor = if (state == CertState.EXPIRED || state == CertState.NOT_YET_VALID) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        GroupRow(
                            stringResource(R.string.enc_checks),
                            listOf(
                                stringResource(if (t.validCert) R.string.enc_cert_ok else R.string.enc_cert_bad),
                                stringResource(if (t.validChain) R.string.enc_chain_ok else R.string.enc_chain_bad),
                                stringResource(if (t.validKey && t.validPair) R.string.enc_key_ok else R.string.enc_key_bad),
                            ).joinToString("\n"),
                            icon = Icons.Outlined.VerifiedUser,
                            iconTint = if (t.validCert && t.validChain && t.validKey && t.validPair) Health.ok else Health.warn,
                        )
                        if (t.subject.isNotBlank()) GroupRow(stringResource(R.string.enc_subject), t.subject)
                        if (t.issuer.isNotBlank()) GroupRow(stringResource(R.string.enc_issuer), t.issuer)
                        if (t.dnsNames.isNotEmpty()) GroupRow(stringResource(R.string.enc_names), t.dnsNames.joinToString(", "))
                        if (notBefore != null) GroupRow(stringResource(R.string.enc_valid_from), showDate(notBefore))
                        if (t.keyType.isNotBlank()) GroupRow(stringResource(R.string.enc_key_type), t.keyType)
                    }
                }
                if (t.warningValidation.isNotBlank()) {
                    Group(stringResource(R.string.enc_group_warning)) {
                        GroupRow(stringResource(R.string.enc_warning), t.warningValidation, icon = Icons.Outlined.Warning, iconTint = Health.warn)
                    }
                }
                Text(
                    stringResource(if (t.hasCertificate || t.enabled) R.string.enc_footer else R.string.enc_footer_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            else -> EmptyState(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.enc_load_failed),
                body = loadError?.let { errorMessage(context, it, server?.host.orEmpty()) }.orEmpty(),
                action = { CtrlTextButton(onClick = ::load) { Text(stringResource(R.string.retry)) } },
            )
        }
    }
}
