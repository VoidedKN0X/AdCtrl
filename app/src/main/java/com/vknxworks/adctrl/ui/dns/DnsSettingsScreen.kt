package com.vknxworks.adctrl.ui.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.vknxworks.adctrl.data.api.DnsSettings
import com.vknxworks.adctrl.data.api.UpstreamMode
import com.vknxworks.adctrl.data.api.UpstreamSyntax
import com.vknxworks.adctrl.data.api.UpstreamTest
import com.vknxworks.adctrl.data.dns.DnsEdit
import com.vknxworks.adctrl.ui.components.ConfirmDialog
import com.vknxworks.adctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.adctrl.ui.components.CtrlTextButton
import com.vknxworks.adctrl.ui.components.CtrlTextField
import com.vknxworks.adctrl.ui.components.EmptyState
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupChoice
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.components.PillButton
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.ui.theme.CtrlColors
import com.vknxworks.adctrl.ui.theme.Gutter
import com.vknxworks.adctrl.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch

/** What's typed on the screen: the server lists as text, numbers as text so they can be checked. */
private data class DnsForm(
    val upstreams: String,
    val bootstrap: String,
    val fallback: String,
    val privateRdns: String,
    val usePrivateRdns: Boolean,
    val mode: UpstreamMode,
    val cacheEnabled: Boolean,
    val cacheSize: String,
    val ttlMin: String,
    val ttlMax: String,
    val optimistic: Boolean,
) {
    companion object {
        fun from(d: DnsSettings): DnsForm {
            val e = DnsEdit.from(d)
            return DnsForm(
                upstreams = e.upstreams.joinToString("\n"),
                bootstrap = e.bootstrap.joinToString("\n"),
                fallback = e.fallback.joinToString("\n"),
                privateRdns = e.privateRdns.joinToString("\n"),
                usePrivateRdns = e.usePrivateRdns,
                mode = e.mode,
                cacheEnabled = e.cacheEnabled,
                cacheSize = e.cacheSize.toString(),
                ttlMin = e.cacheTtlMin.toString(),
                ttlMax = e.cacheTtlMax.toString(),
                optimistic = e.cacheOptimistic,
            )
        }
    }
}

private const val UINT32_MAX = 4_294_967_295L

private fun uint(s: String): Long? = s.trim().toLongOrNull()?.takeIf { it in 0..UINT32_MAX }

/** The first problem with the form, as a message; null when it can be sent. */
private data class FormCheck(
    val upstreamsBad: Int? = null,
    val upstreamsEmpty: Boolean = false,
    val bootstrapBad: Int? = null,
    val fallbackBad: Int? = null,
    val privateBad: Int? = null,
    val sizeBad: Boolean = false,
    val sizeZero: Boolean = false,
    val ttlMinBad: Boolean = false,
    val ttlMaxBad: Boolean = false,
    val ttlOrder: Boolean = false,
) {
    /** The server lists are fine, so they can be tested. */
    val serversOk: Boolean get() = upstreamsBad == null && !upstreamsEmpty && bootstrapBad == null && fallbackBad == null && privateBad == null
    val ok: Boolean get() = serversOk && !sizeBad && !sizeZero && !ttlMinBad && !ttlMaxBad && !ttlOrder
}

/** [fromFile]: the server reads its upstreams from a file, so the list may be empty. */
private fun validate(f: DnsForm, version: AghVersion, fromFile: Boolean): FormCheck {
    val size = uint(f.cacheSize)
    val min = uint(f.ttlMin)
    val max = uint(f.ttlMax)
    return FormCheck(
        upstreamsBad = UpstreamSyntax.firstBadLine(f.upstreams, domains = true),
        upstreamsEmpty = !fromFile && UpstreamSyntax.entries(UpstreamSyntax.lines(f.upstreams)).isEmpty(),
        bootstrapBad = UpstreamSyntax.firstBadLine(f.bootstrap, domains = false, comments = version.bootstrapComments),
        fallbackBad = UpstreamSyntax.firstBadLine(f.fallback, domains = true),
        privateBad = UpstreamSyntax.firstBadLine(f.privateRdns, domains = true),
        sizeBad = size == null,
        sizeZero = f.cacheEnabled && size == 0L,
        ttlMinBad = min == null,
        ttlMaxBad = max == null,
        ttlOrder = min != null && max != null && max > 0 && min > max,
    )
}

private fun DnsForm.toEdit() = DnsEdit(
    upstreams = UpstreamSyntax.lines(upstreams),
    bootstrap = UpstreamSyntax.lines(bootstrap),
    fallback = UpstreamSyntax.lines(fallback),
    privateRdns = UpstreamSyntax.lines(privateRdns),
    usePrivateRdns = usePrivateRdns,
    mode = mode,
    cacheEnabled = cacheEnabled,
    cacheSize = uint(cacheSize) ?: 0,
    cacheTtlMin = uint(ttlMin) ?: 0,
    cacheTtlMax = uint(ttlMax) ?: 0,
    cacheOptimistic = optimistic,
)

/**
 * View and edit the active server's upstream DNS servers, bootstrap, fallback and private reverse DNS
 * servers, the upstream mode and the cache, with "Test upstreams" for the settings as typed. Applying
 * asks first (AdGuard Home restarts its DNS server), and read-only mode only allows looking and testing.
 */
@Composable
fun DnsSettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly == true
    val version = AghVersion.parse(server?.version.orEmpty())
    val snackbar = remember { SnackbarHostState() }

    var loaded by remember { mutableStateOf<DnsSettings?>(null) }
    var form by remember { mutableStateOf<DnsForm?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<UpstreamTest>?>(null) }
    var confirm by remember { mutableStateOf(false) }

    fun host() = server?.host.orEmpty()

    fun load() {
        loading = true
        loadError = null
        scope.launch {
            try {
                val d = c.dns.load()
                loaded = d
                form = DnsForm.from(d)
            } catch (e: AghError) {
                loadError = e
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(server?.id) {
        loaded = null
        form = null
        results = null
        if (server != null) {
            load()
        } else {
            loading = false
        }
    }

    val f = form
    val chk = f?.let { validate(it, version, fromFile = loaded?.upstreamDnsFile?.isNotBlank() == true) }
    val base = loaded
    val changed = f != null && base != null && f != DnsForm.from(base)

    fun test() {
        val edit = f?.toEdit() ?: return
        testing = true
        scope.launch {
            try {
                results = c.dns.test(edit)
            } catch (e: AghError) {
                snackbar.showSnackbar(errorMessage(context, e, host()))
            } finally {
                testing = false
            }
        }
    }

    fun save() {
        val edit = f?.toEdit() ?: return
        busy = true
        scope.launch {
            try {
                c.dns.apply(edit)
                val d = c.dns.load()
                loaded = d
                form = DnsForm.from(d)
                snackbar.showSnackbar(context.getString(R.string.dns_saved))
                // The Overview shows the upstreams and the cache too.
                c.appScope.launch { c.overview.refresh() }
            } catch (e: AghError) {
                snackbar.showSnackbar(errorMessage(context, e, host()))
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.dns_settings))
            when {
                f == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                f != null && chk != null -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 24.dp),
                ) {
                    Text(
                        stringResource(R.string.dns_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                    )
                    if (readOnly) {
                        Text(
                            stringResource(R.string.dns_read_only),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CtrlColors.Warn,
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                        )
                    }

                    // --- Upstreams ---
                    loaded?.upstreamDnsFile?.takeIf { it.isNotBlank() }?.let { file ->
                        Text(
                            stringResource(R.string.dns_upstreams_file, file),
                            style = MaterialTheme.typography.bodySmall,
                            color = CtrlColors.Warn,
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                        )
                    }
                    ServerList(
                        stringResource(R.string.dns_upstreams),
                        f.upstreams,
                        stringResource(R.string.dns_upstreams_hint),
                        chk.upstreamsBad,
                        empty = chk.upstreamsEmpty,
                    ) { form = f.copy(upstreams = it) }

                    Group {
                        GroupChoice(
                            stringResource(R.string.dns_mode),
                            UpstreamMode.entries.map { it to stringResource(modeLabel(it)) },
                            f.mode,
                            icon = Icons.Outlined.AltRoute,
                        ) { m -> form = f.copy(mode = m) }
                        Text(
                            stringResource(modeSub(f.mode)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 50.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }

                    ServerList(stringResource(R.string.dns_fallback), f.fallback, stringResource(R.string.dns_fallback_hint), chk.fallbackBad) {
                        form = f.copy(fallback = it)
                    }
                    ServerList(stringResource(R.string.dns_bootstrap), f.bootstrap, stringResource(R.string.dns_bootstrap_hint), chk.bootstrapBad) {
                        form = f.copy(bootstrap = it)
                    }

                    // --- Private reverse DNS ---
                    Group(stringResource(R.string.dns_private)) {
                        GroupSwitch(
                            stringResource(R.string.dns_private_use),
                            stringResource(R.string.dns_private_use_sub),
                            checked = f.usePrivateRdns,
                            icon = Icons.Outlined.Router,
                        ) { on -> form = f.copy(usePrivateRdns = on) }
                    }
                    val defaults = loaded?.defaultLocalPtrUpstreams.orEmpty()
                    ServerList(
                        stringResource(R.string.dns_private),
                        f.privateRdns,
                        if (defaults.isEmpty()) stringResource(R.string.dns_private_hint_none) else stringResource(R.string.dns_private_hint, defaults.joinToString(", ")),
                        chk.privateBad,
                    ) { form = f.copy(privateRdns = it) }

                    // --- Cache ---
                    Group(stringResource(R.string.dns_cache)) {
                        GroupSwitch(
                            stringResource(R.string.dns_cache_enabled),
                            stringResource(R.string.dns_cache_enabled_sub),
                            checked = f.cacheEnabled,
                            icon = Icons.Outlined.Memory,
                        ) { on -> form = f.copy(cacheEnabled = on) }
                        if (f.cacheEnabled) {
                            NumberField(
                                stringResource(R.string.dns_cache_size),
                                f.cacheSize,
                                when {
                                    chk.sizeBad -> stringResource(R.string.dns_number_bad)
                                    chk.sizeZero -> stringResource(R.string.dns_cache_size_zero)
                                    else -> null
                                },
                                stringResource(R.string.dns_cache_size_hint),
                            ) { form = f.copy(cacheSize = it) }
                            NumberField(
                                stringResource(R.string.dns_ttl_min),
                                f.ttlMin,
                                when {
                                    chk.ttlMinBad -> stringResource(R.string.dns_number_bad)
                                    chk.ttlOrder -> stringResource(R.string.dns_ttl_order)
                                    else -> null
                                },
                                stringResource(R.string.dns_ttl_hint),
                            ) { form = f.copy(ttlMin = it) }
                            NumberField(
                                stringResource(R.string.dns_ttl_max),
                                f.ttlMax,
                                if (chk.ttlMaxBad) stringResource(R.string.dns_number_bad) else null,
                                stringResource(R.string.dns_ttl_hint),
                            ) { form = f.copy(ttlMax = it) }
                            GroupSwitch(
                                stringResource(R.string.dns_optimistic),
                                stringResource(R.string.dns_optimistic_sub),
                                checked = f.optimistic,
                                icon = Icons.Outlined.Bolt,
                            ) { on -> form = f.copy(optimistic = on) }
                        }
                    }

                    // --- Test ---
                    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CtrlSecondaryButton(onClick = ::test, enabled = !testing && !busy && chk.serversOk) {
                            if (testing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.NetworkCheck, null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (testing) R.string.dns_testing else R.string.dns_test))
                        }
                    }
                    results?.let { list ->
                        Group(stringResource(R.string.dns_test_title), footer = stringResource(R.string.dns_test_intro)) {
                            if (list.isEmpty()) {
                                GroupRow(stringResource(R.string.dns_test_none))
                            }
                            list.forEach { r ->
                                GroupRow(
                                    r.server,
                                    if (r.ok) stringResource(R.string.dns_test_ok) else r.message,
                                    icon = if (r.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                    iconTint = if (r.ok) CtrlColors.AccentText else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    if (!readOnly) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton(
                                stringResource(R.string.save_changes),
                                onClick = { confirm = true },
                                enabled = changed && chk.ok && !busy && !testing,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                else -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.dns_load_failed),
                    body = loadError?.let { errorMessage(context, it, host()) }.orEmpty(),
                    action = { CtrlTextButton(onClick = ::load) { Text(stringResource(R.string.retry)) } },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp))
    }

    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.dns_apply_title),
            body = stringResource(R.string.dns_apply_body, server?.name.orEmpty()),
            confirmLabel = stringResource(R.string.dns_apply),
            onDismiss = { confirm = false },
            onConfirm = { confirm = false; save() },
            danger = false,
        )
    }
}

@Composable
private fun ServerList(
    label: String,
    value: String,
    hint: String,
    bad: Int?,
    empty: Boolean = false,
    onChange: (String) -> Unit,
) {
    CtrlTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = {
            Text(
                when {
                    bad != null -> stringResource(R.string.dns_bad_line, bad)
                    empty -> stringResource(R.string.dns_upstreams_empty)
                    else -> hint
                },
            )
        },
        isError = bad != null || empty,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
        minLines = 3,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
    )
}

@Composable
private fun NumberField(label: String, value: String, error: String?, hint: String?, onChange: (String) -> Unit) {
    CtrlTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(10)) },
        label = { Text(label) },
        supportingText = if (error != null || hint != null) {
            { Text(error ?: hint.orEmpty()) }
        } else {
            null
        },
        isError = error != null,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

private fun modeLabel(m: UpstreamMode) = when (m) {
    UpstreamMode.LOAD_BALANCE -> R.string.dns_mode_load_balance
    UpstreamMode.PARALLEL -> R.string.dns_mode_parallel
    UpstreamMode.FASTEST -> R.string.dns_mode_fastest
}

private fun modeSub(m: UpstreamMode) = when (m) {
    UpstreamMode.LOAD_BALANCE -> R.string.dns_mode_load_balance_sub
    UpstreamMode.PARALLEL -> R.string.dns_mode_parallel_sub
    UpstreamMode.FASTEST -> R.string.dns_mode_fastest_sub
}
