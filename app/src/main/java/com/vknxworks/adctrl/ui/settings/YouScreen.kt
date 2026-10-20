package com.vknxworks.adctrl.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.BuildConfig
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.nav.ServersRoute
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.ScreenTopBar
import com.vknxworks.adctrl.ui.nav.LocalNavigator
import com.vknxworks.adctrl.ui.nav.SettingsRoute
import com.vknxworks.adctrl.ui.theme.Gutter

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/AdCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/AdCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/**
 * Account and settings, opened from the profile picture on any tab. Shows which server you're
 * connected to, the server list and read-only mode, settings, and the About / support section.
 */
@Composable
fun YouScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val connection by c.sessions.connection.collectAsStateWithLifecycle()
    val server = active?.server
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.you_title))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (server == null) {
                        Icon(Icons.Filled.Dns, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(server.initial.uppercase(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(server?.name ?: stringResource(R.string.you_not_connected), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    if (server == null) {
                        stringResource(R.string.you_not_connected_body)
                    } else {
                        listOfNotNull(
                            (connection.status?.version ?: server.version).takeIf { it.isNotBlank() }?.let { stringResource(R.string.agh_version, it) },
                            stringResource(R.string.signed_in_as, server.username),
                        ).joinToString("\n")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (server != null) {
                Group {
                    GroupRow(
                        stringResource(R.string.servers_title),
                        stringResource(R.string.servers_subtitle),
                        icon = Icons.Filled.SwapHoriz,
                        showChevron = true,
                        onClick = { nav.navigate(ServersRoute) },
                    )
                    GroupSwitch(
                        stringResource(R.string.read_only_mode),
                        stringResource(R.string.read_only_mode_subtitle),
                        checked = server.readOnly,
                        icon = Icons.Filled.Visibility,
                    ) { c.sessions.setReadOnly(server.id, it) }
                }
            }

            Group {
                GroupRow(
                    stringResource(R.string.settings),
                    stringResource(R.string.settings_subtitle),
                    icon = Icons.Filled.Settings,
                    showChevron = true,
                    onClick = { nav.navigate(SettingsRoute) },
                )
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(
                    stringResource(R.string.support_title),
                    stringResource(R.string.support_subtitle),
                    icon = Icons.Filled.Favorite,
                    showChevron = true,
                    onClick = { open(SPONSOR_URL) },
                )
                GroupRow(
                    stringResource(R.string.bugs_title),
                    stringResource(R.string.bugs_subtitle),
                    icon = Icons.Filled.BugReport,
                    showChevron = true,
                    onClick = { open(ISSUES_URL) },
                )
                GroupRow(
                    stringResource(R.string.source_title),
                    stringResource(R.string.source_subtitle),
                    icon = Icons.Filled.Code,
                    showChevron = true,
                    onClick = { open(REPO_URL) },
                )
                GroupRow(
                    stringResource(R.string.licence_title),
                    stringResource(R.string.licence_subtitle),
                    icon = Icons.Filled.Gavel,
                    showChevron = true,
                    onClick = { open(LICENCE_URL) },
                )
            }

            Text(
                stringResource(R.string.about_footer, stringResource(R.string.app_name), BuildConfig.VERSION_NAME, stringResource(R.string.vendor)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
