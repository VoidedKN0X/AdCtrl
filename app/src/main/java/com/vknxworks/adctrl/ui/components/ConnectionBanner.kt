package com.vknxworks.adctrl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.ui.nav.EditServerRoute
import com.vknxworks.adctrl.ui.nav.LocalNavigator
import com.vknxworks.adctrl.ui.theme.Gutter

/**
 * Shown on the tabs when the active server can't be used right now: unreachable, a changed
 * certificate, a rejected password or a version that's too old. Offers the one action that fixes it.
 * There is no fallback address: AdCtrl only ever uses the address saved for the server.
 */
@Composable
fun ConnectionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val connection by c.sessions.connection.collectAsStateWithLifecycle()
    val server = active?.server ?: return
    val error = connection.error ?: return

    val needsSetup = error is AghError.PinMismatch || error is AghError.Unauthorized ||
        error is AghError.UntrustedCertificate || error is AghError.Redirect || error is AghError.CleartextRefused
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(stringResource(R.string.connection_problem), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        errorMessage(context, error, server.host),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (needsSetup) {
                    TextButton(onClick = { nav.navigate(EditServerRoute(server.id)) }) {
                        Text(
                            stringResource(
                                when (error) {
                                    is AghError.PinMismatch, is AghError.UntrustedCertificate -> R.string.check_again
                                    is AghError.Unauthorized -> R.string.sign_in_again
                                    else -> R.string.edit_server
                                },
                            ),
                        )
                    }
                }
                // Also after updating an old AdGuard Home: Retry checks the version again.
                TextButton(onClick = c.sessions::refreshStatus) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}
