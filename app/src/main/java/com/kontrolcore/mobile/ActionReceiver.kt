package com.kontrolcore.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.kontrolcore.mobile.core.NtfyClient
import com.kontrolcore.mobile.core.Ticket

/**
 * The Acknowledge and Snooze buttons on a notification: posts "ACK <name> ~<ticket>" to the paired topic. A ticket replaces the one-time code (a button cannot
 * hold a fresh one), works once on that one scenario, and is checked again by the computer. The message is checked here too: it must be a ticket command, not
 * past its end, and it goes only to the topic this phone is paired with.
 */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Sent"
        val notification = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        val paired = Repo.paired() ?: return
        val t = Ticket.parseCommand(message)
        if (t == null || t.expiry < System.currentTimeMillis() / 1000) { toast(context, "That button has expired. Open the app to ${t?.command ?: "send it"}."); return }
        val pending = goAsync()
        Thread {
            try {
                NtfyClient(paired.topicUrl).publish(t.message)
                NotificationManagerCompat.from(context).cancel(notification)
                toast(context, "$label: sent. Your computer will act on it when it next looks.")
            } catch (e: Exception) {
                toast(context, "Could not send ($label). Check the connection and try again.")
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun toast(context: Context, text: String) = Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show() }

    companion object {
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_NOTIFICATION = "notification"
        const val EXTRA_LABEL = "label"
    }
}
