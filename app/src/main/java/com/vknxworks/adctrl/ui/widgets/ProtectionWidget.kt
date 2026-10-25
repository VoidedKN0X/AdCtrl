package com.vknxworks.adctrl.ui.widgets

import com.vknxworks.adctrl.ui.theme.CtrlColors
import androidx.compose.ui.graphics.toArgb
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import com.vknxworks.adctrl.MainActivity
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.widget.WidgetRefreshWorker
import com.vknxworks.adctrl.data.widget.WidgetStore
import com.vknxworks.adctrl.ui.components.formatCount
import com.vknxworks.adctrl.ui.components.percent

/**
 * Health colours as on the Overview (see ui.theme.Health), as light/dark pairs from the Ctrl / Signal
 * palettes: the widget follows the device's dark mode.
 */
private enum class WidgetColors(val light: Int, val dark: Int) {
    OK(CtrlColors.Light.accentText.toArgb(), CtrlColors.Dark.accentText.toArgb()),
    WARN(CtrlColors.Light.warn.toArgb(), CtrlColors.Dark.warn.toArgb()),
    CRITICAL(CtrlColors.Light.error.toArgb(), CtrlColors.Dark.error.toArgb()),
    IDLE(CtrlColors.Light.disabled.toArgb(), CtrlColors.Dark.disabled.toArgb()),
}

/** Tints the status icon, switching with the launcher's dark mode. */
private fun RemoteViews.tintIcon(c: WidgetColors) = setColorInt(R.id.widget_icon, "setColorFilter", c.light, c.dark)

/**
 * Home screen widget for the active server: protection on, paused (with a live countdown) or off,
 * and the DNS queries and blocked share of the last 24 hours. A tap opens AdCtrl. It never changes
 * anything itself. While AdCtrl is locked and the password is sealed with the app lock, it only says
 * that AdCtrl is locked.
 */
class ProtectionWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        draw(context, manager, ids)
        // Every 30 minutes (updatePeriodMillis) and when placed: fetch fresh numbers in the background.
        WidgetRefreshWorker.enqueue(context)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshWorker.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        WidgetStore.clear(context)
    }

    companion object {
        private fun component(context: Context) = ComponentName(context, ProtectionWidget::class.java)

        fun anyPlaced(context: Context): Boolean =
            runCatching { AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty() }.getOrDefault(false)

        /**
         * Redraws every placed widget from what's saved (no network). Also runs while AdCtrl is being closed
         * (locking redraws it), so a launcher that refuses the update can never crash the app.
         */
        fun redraw(context: Context) {
            val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
            val ids = runCatching { manager.getAppWidgetIds(component(context)) }.getOrNull() ?: return
            if (ids.isNotEmpty()) runCatching { draw(context, manager, ids) }
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val c = context.container
            val views = RemoteViews(context.packageName, R.layout.widget_protection)
            views.setOnClickPendingIntent(android.R.id.background, openApp(context))
            views.setViewVisibility(R.id.widget_countdown, View.GONE)
            views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime(), null, false)

            val server = c.sessions.active.value?.server
            val snap = WidgetStore.read(context)?.takeIf { it.serverId == server?.id }
            val now = System.currentTimeMillis()
            when {
                server == null -> {
                    views.tintIcon(WidgetColors.IDLE)
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.tile_no_server))
                    views.setViewVisibility(R.id.widget_stats, View.GONE)
                }
                // The password is sealed and AdCtrl is locked: show nothing about the server.
                c.serverStore.sealedNow(server.id) -> {
                    views.tintIcon(WidgetColors.IDLE)
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_locked))
                    views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_locked_subtitle))
                    views.setViewVisibility(R.id.widget_stats, View.GONE)
                }
                snap == null -> {
                    views.tintIcon(WidgetColors.IDLE)
                    views.setTextViewText(R.id.widget_title, server.name)
                    views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.tile_unknown))
                    views.setViewVisibility(R.id.widget_stats, View.GONE)
                }
                else -> {
                    val paused = !snap.protectionEnabled && snap.pausedUntil > now
                    // A pause that has run out: AdGuard Home has turned protection back on by itself.
                    val on = snap.protectionEnabled || (snap.pausedUntil in 1..now)
                    val (title, color) = when {
                        on -> R.string.protection_on to WidgetColors.OK
                        paused -> R.string.protection_paused to WidgetColors.WARN
                        else -> R.string.protection_off to WidgetColors.CRITICAL
                    }
                    views.tintIcon(if (snap.unreachable) WidgetColors.IDLE else color)
                    views.setTextViewText(R.id.widget_title, context.getString(title))
                    val many = c.sessions.servers.value.size > 1
                    val subtitle = when {
                        snap.unreachable -> context.getString(
                            R.string.widget_unreachable,
                            DateUtils.getRelativeTimeSpanString(snap.updatedAt, now, DateUtils.MINUTE_IN_MILLIS),
                        )
                        paused -> context.getString(R.string.widget_back_in)
                        many -> snap.serverName
                        on -> context.getString(R.string.widget_blocking)
                        else -> context.getString(R.string.widget_off_until)
                    }
                    views.setTextViewText(R.id.widget_subtitle, subtitle)
                    if (paused && !snap.unreachable) {
                        views.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
                        views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + (snap.pausedUntil - now), null, true)
                        views.setChronometerCountDown(R.id.widget_countdown, true)
                    }
                    views.setViewVisibility(R.id.widget_stats, View.VISIBLE)
                    if (snap.queries >= 0) {
                        views.setTextViewText(R.id.widget_queries, formatCount(snap.queries))
                        val pct = if (snap.queries > 0) percent(snap.blocked.toDouble() / snap.queries) else 0
                        views.setTextViewText(R.id.widget_blocked, context.getString(R.string.stat_percent, pct))
                    } else {
                        views.setTextViewText(R.id.widget_queries, context.getString(R.string.widget_no_value))
                        views.setTextViewText(R.id.widget_blocked, context.getString(R.string.widget_no_value))
                    }
                }
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
