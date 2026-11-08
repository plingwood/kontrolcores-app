package com.kontrolcore.mobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kontrolcore.mobile.core.Alert
import com.kontrolcore.mobile.core.AlertKind
import com.kontrolcore.mobile.core.Alerts
import com.kontrolcore.mobile.core.NtfyMessage
import com.kontrolcore.mobile.core.SafeAction

/** Android notifications for alerts, with the buttons the extension put on them (Acknowledge, Snooze) and one of our own (Run again). */
object Notifier {
    const val CH_ALERTS = "alerts"
    const val CH_OTHER = "other"
    const val CH_SERVICE = "service"
    /** An alert older than this when it arrives (history read after a reconnect) is kept in the inbox but not announced again. */
    private const val FRESH_SECONDS = 600L

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_OTHER, context.getString(R.string.channel_other), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, context.getString(R.string.channel_service), NotificationManager.IMPORTANCE_MIN))
    }

    fun canNotify(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun idFor(name: String): Int = 1000 + (name.lowercase().hashCode() and 0x0fffffff)

    fun serviceNotification(context: Context, text: String) =
        NotificationCompat.Builder(context, CH_SERVICE).setSmallIcon(R.drawable.ic_stat_k).setContentTitle(context.getString(R.string.app_name)).setContentText(text)
            .setOngoing(true).setContentIntent(open(context, 1, null)).build()

    private fun open(context: Context, req: Int, name: String?): PendingIntent {
        val i = if (name == null) Intent(context, MainActivity::class.java) else Intent(context, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_NAME, name)
        return PendingIntent.getActivity(context, req, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Announces a message that just arrived. */
    fun announce(context: Context, m: NtfyMessage) {
        val paired = Repo.paired() ?: return
        val alert = Alerts.classify(m) ?: return
        if (alert.kind == AlertKind.COMMAND || alert.kind == AlertKind.REPLY) return
        val now = System.currentTimeMillis() / 1000
        if (now - m.time > FRESH_SECONDS || !canNotify(context)) return
        val nm = NotificationManagerCompat.from(context)
        if (alert.kind == AlertKind.RECOVERED) nm.cancel(idFor(alert.name))
        val urgent = alert.needsYou
        val id = if (alert.name.isNotEmpty()) idFor(alert.name) else (m.id.hashCode() and 0x0fffffff)
        val b = NotificationCompat.Builder(context, if (urgent) CH_ALERTS else CH_OTHER)
            .setSmallIcon(R.drawable.ic_stat_k)
            .setContentTitle(alert.title)
            .setContentText(alert.step.ifEmpty { alert.body.lineSequence().firstOrNull() ?: "" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(open(context, id, alert.name.ifEmpty { null }))
        if (urgent && alert.name.isNotEmpty()) {
            // The extension's own buttons: each carries a single-use ticket, so a tap sends it straight back without a code. Only ours (to this topic) are shown.
            for ((n, a) in SafeAction.accepted(m, paired.topicUrl, now).withIndex()) {
                val i = Intent(context, ActionReceiver::class.java).putExtra(ActionReceiver.EXTRA_MESSAGE, a.ticket.message).putExtra(ActionReceiver.EXTRA_NOTIFICATION, id).putExtra(ActionReceiver.EXTRA_LABEL, a.label)
                b.addAction(0, a.label, PendingIntent.getBroadcast(context, id * 4 + n, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            // Run again needs a real one-time code, so it opens the app to unlock and ask first.
            val run = Intent(context, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_NAME, alert.name).putExtra(DetailActivity.EXTRA_AUTO, "run")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            b.addAction(0, context.getString(R.string.run_again), PendingIntent.getActivity(context, id * 4 + 3, run, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        try { nm.notify(id, b.build()) } catch (e: SecurityException) { /* permission taken away while we were building it */ }
    }
}
