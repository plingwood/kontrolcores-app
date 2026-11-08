package com.kontrolcore.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a restart or an update of the app, start listening again if the phone is paired. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        runCatching { ListenService.start(context) }
    }
}
