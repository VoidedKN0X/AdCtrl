package com.vknxworks.adctrl.data.timed

import android.content.Context
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.notify.Alert
import com.vknxworks.adctrl.data.notify.Notifier
import com.vknxworks.adctrl.data.notify.NotifyEvent
import com.vknxworks.adctrl.data.overview.Severity
import com.vknxworks.adctrl.data.server.Server

/**
 * The notification for a client pause that couldn't be ended after every retry: that device is still
 * unfiltered, and opening AdCtrl ends the pause (the Overview removes ended pauses). Posted regardless of
 * the background-check settings, because the user set up the pause and was promised it would end by itself.
 * On a locked phone only the redacted version shows.
 */
object PauseStuck {
    private fun key(serverId: String) = "$serverId:client-pause-stuck"

    fun notify(context: Context, server: Server) {
        Notifier.post(
            context,
            Alert(
                key = key(server.id),
                event = NotifyEvent.PROTECTION_OFF,
                severity = Severity.WARNING,
                title = context.getString(R.string.client_pause_stuck_title, server.name),
                text = context.getString(R.string.client_pause_stuck_text),
            ),
        )
    }

    fun cancel(context: Context, serverId: String) = Notifier.cancel(context, key(serverId))
}
