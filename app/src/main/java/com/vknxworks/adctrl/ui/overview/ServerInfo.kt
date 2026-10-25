package com.vknxworks.adctrl.ui.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.overview.OverviewSnapshot
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.hideFirstDivider
import com.vknxworks.adctrl.ui.setup.dnsSummary

/** Version, DNS addresses, upstreams, DHCP and cache, plus clearing the cache and updating. */
@Composable
fun ServerInfoCard(
    snap: OverviewSnapshot,
    readOnly: Boolean,
    busy: Boolean,
    onClearCache: () -> Unit,
    onCheckUpdate: () -> Unit,
    onUpdate: () -> Unit,
) {
    val status = snap.status ?: return
    val update = snap.versionInfo?.takeIf { !it.disabled && it.newVersion.isNotBlank() && it.newVersion != status.version }
    OverviewCard(stringResource(R.string.server_info_title)) {
        Column(Modifier.hideFirstDivider()) {
            GroupRow(
                stringResource(R.string.agh_version, status.version),
                when {
                    update != null -> stringResource(R.string.update_available, update.newVersion)
                    snap.versionInfo?.disabled == true -> stringResource(R.string.update_check_disabled)
                    else -> stringResource(R.string.up_to_date)
                },
                icon = Icons.Outlined.Info,
            )
            GroupRow(stringResource(R.string.info_dns), dnsSummary(status), icon = Icons.Outlined.Dns)
            snap.dnsInfo?.let { dns ->
                val ups = dns.upstreams
                GroupRow(
                    stringResource(R.string.info_upstreams),
                    if (ups.isEmpty()) stringResource(R.string.info_none) else (ups.take(5) + listOfNotNull(if (ups.size > 5) stringResource(R.string.info_more, ups.size - 5) else null)).joinToString("\n"),
                    icon = Icons.Outlined.CloudSync,
                )
                dns.cacheEnabled?.let { on ->
                    GroupRow(stringResource(R.string.info_cache), stringResource(if (on) R.string.state_on else R.string.state_off), icon = Icons.Outlined.Memory)
                }
            }
            if (status.dhcpAvailable || snap.dhcp != null) {
                GroupRow(
                    stringResource(R.string.info_dhcp),
                    stringResource(if (snap.dhcp?.enabled == true) R.string.state_on else R.string.state_off),
                    icon = Icons.Outlined.Router,
                )
            }
            if (!readOnly) {
                GroupRow(
                    stringResource(R.string.clear_cache),
                    stringResource(R.string.clear_cache_subtitle),
                    icon = Icons.Outlined.CleaningServices,
                    onClick = if (busy) null else onClearCache,
                )
                if (update?.canAutoupdate == true) {
                    GroupRow(
                        stringResource(R.string.update_now, update.newVersion),
                        stringResource(R.string.update_now_subtitle),
                        icon = Icons.Outlined.SystemUpdate,
                        onClick = if (busy) null else onUpdate,
                    )
                }
            }
            GroupRow(
                stringResource(R.string.check_update),
                stringResource(R.string.check_update_subtitle),
                icon = Icons.Outlined.Refresh,
                onClick = if (busy) null else onCheckUpdate,
            )
        }
    }
}
