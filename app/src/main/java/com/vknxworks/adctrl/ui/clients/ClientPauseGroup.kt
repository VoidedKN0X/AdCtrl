package com.vknxworks.adctrl.ui.clients

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.FilteringStatus
import com.vknxworks.adctrl.data.rules.RulesApi
import com.vknxworks.adctrl.data.timed.ClientPause
import com.vknxworks.adctrl.data.timed.PauseSecrets
import com.vknxworks.adctrl.data.timed.TimedWorker
import com.vknxworks.adctrl.ui.components.ChoiceSheet
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.errorMessage
import com.vknxworks.adctrl.ui.theme.Health
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How long a client can be paused for, with its label. */
private val DURATIONS = listOf(
    10 * 60_000L to R.string.pause_10m,
    30 * 60_000L to R.string.pause_30m,
    60 * 60_000L to R.string.pause_1h,
    2 * 60 * 60_000L to R.string.pause_2h,
    8 * 60 * 60_000L to R.string.pause_8h,
)

private fun clock(ms: Long): String =
    DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** The end time as written into the rules' comment line: plain digits and Latin letters, whatever the phone's language. */
private fun markerLabel(ms: Long): String =
    DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.US).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/**
 * Pause blocking for one client for a while: an allow-everything rule for that client is added to the
 * custom rules together with a comment holding the end time ([ClientPause]), and a job removes both
 * when the time is up. Blocked services, safe browsing and parental control are separate settings and
 * stay as they are. [client] is a persistent client's name or a found device's address.
 */
@Composable
fun ClientPauseGroup(client: String, readOnly: Boolean, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val filters by c.filters.state.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(server?.id) {
        if (filters.filtering == null) runCatching { c.filters.loadLists() }
    }
    val rules = filters.filtering?.userRules ?: return
    val until = ClientPause.untilFor(rules, client)
    LaunchedEffect(until) {
        while (until != null) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    if (until == null && readOnly) return

    /**
     * Reads the rules, applies [edit], runs [before] with the new rules, writes them, then runs [after].
     * [before] runs ahead of the write so that the job that ends a pause is always queued before the pause
     * exists on the server (a job with nothing to end is harmless).
     */
    fun change(
        done: String,
        edit: (List<String>) -> List<String>,
        before: suspend (List<String>) -> Unit = {},
        after: suspend (List<String>) -> Unit = {},
    ) {
        val api = active?.client ?: return
        busy = true
        scope.launch {
            try {
                // Read again right before writing, so nothing that changed elsewhere is lost.
                val current = api.get("filtering/status", FilteringStatus.serializer()).userRules
                val updated = edit(current)
                before(updated)
                api.post("filtering/set_rules", RulesApi.setRulesBody(updated))
                after(updated)
                runCatching { c.filters.loadLists() }
                onMessage(done)
            } catch (e: AghError) {
                onMessage(errorMessage(context, e, server?.host.orEmpty()))
            } finally {
                busy = false
            }
        }
    }

    Group(stringResource(R.string.client_pause_group), footer = stringResource(R.string.client_pause_footer)) {
        if (until != null) {
            GroupRow(
                if (until > now) stringResource(R.string.client_paused_until, clock(until)) else stringResource(R.string.client_pause_ended),
                if (readOnly) null else stringResource(R.string.client_pause_resume),
                icon = Icons.Outlined.PlayCircle,
                iconTint = Health.warn,
                onClick = if (readOnly || busy) null else ({
                    val id = server?.id
                    change(
                        context.getString(R.string.client_pause_resumed),
                        { ClientPause.remove(it, client) },
                        after = { updated ->
                            // No pause left on this server: the password copy for the pause-end job goes.
                            if (id != null) {
                                withContext(Dispatchers.IO) { PauseSecrets.afterPausesChanged(context, id, ClientPause.nextEnd(updated)) }
                            }
                        },
                    )
                }),
            )
        } else {
            GroupRow(
                stringResource(R.string.client_pause),
                stringResource(R.string.client_pause_subtitle),
                icon = Icons.Outlined.PauseCircle,
                onClick = if (busy) null else ({ picking = true }),
            )
        }
    }

    if (picking) {
        ChoiceSheet(
            stringResource(R.string.client_pause_title),
            DURATIONS.map { (ms, label) -> ms to stringResource(label) },
            null,
            onDismiss = { picking = false },
        ) { ms ->
            picking = false
            val end = System.currentTimeMillis() + ms
            val id = server?.id
            change(
                context.getString(R.string.client_paused_until, clock(end)),
                { ClientPause.add(it, client, end, markerLabel(end)) },
                before = { updated ->
                    if (id != null) {
                        withContext(Dispatchers.IO) {
                            // The job must be able to sign in even when AdCtrl is locked then and the normal
                            // password is sealed by the app lock: keep a copy for that job only.
                            PauseSecrets.save(context, id, c.serverStore.secrets(id).password)
                        }
                        // For the pause that ends first (another device's may end before this one).
                        val next = ClientPause.nextEnd(updated) ?: end
                        TimedWorker.enqueueResume(context, id, next - System.currentTimeMillis())
                    }
                },
            )
        }
    }
}
