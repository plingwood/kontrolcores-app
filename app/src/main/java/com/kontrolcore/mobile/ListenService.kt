package com.kontrolcore.mobile

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.kontrolcore.mobile.core.NtfyClient

/**
 * Keeps one stream open to the person's ntfy topic so alerts arrive at once (the same thing ntfy's own app does; we run no push service of our own). It
 * reconnects with a growing wait, asks for what it missed since the last message it saw, and stops itself when the phone is unpaired.
 */
class ListenService : Service() {
    @Volatile private var running = false
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        val note = Notifier.serviceNotification(this, getString(R.string.listening))
        // The "special use" type exists from Android 14; before that a service has no type to give (naming it would throw).
        if (Build.VERSION.SDK_INT >= 34) ServiceCompat.startForeground(this, 1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, note)
        if (!running) {
            running = true
            thread = Thread({ loop() }, "kontrolcore-listen").also { it.start() }
        }
        return START_STICKY
    }

    private fun loop() {
        var wait = 2_000L
        while (running) {
            val paired = Repo.paired()
            if (paired == null) { stopSelf(); break }
            try {
                NtfyClient(paired.topicUrl).stream(
                    since = Repo.lastId() ?: "all",
                    shouldStop = { !running || Repo.paired() != paired },
                    onConnected = { wait = 2_000L; Repo.setConnected(true) },
                ) { m -> if (Repo.add(m)) Notifier.announce(applicationContext, m) }
                Repo.setConnected(false, "Reconnecting…")
            } catch (e: Exception) {
                Repo.setConnected(false, if (e is NtfyClient.HttpError) "The alert server answered ${e.code}." else "No connection. Trying again…")
            }
            if (!running) break
            try { Thread.sleep(wait) } catch (e: InterruptedException) { break }
            wait = minOf(wait * 2, 60_000L)
        }
        Repo.setConnected(false)
    }

    override fun onDestroy() {
        running = false
        thread?.interrupt()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            if (Repo.paired() == null) return
            context.startForegroundService(Intent(context, ListenService::class.java))
        }
        fun stop(context: Context) { context.stopService(Intent(context, ListenService::class.java)) }
    }
}
