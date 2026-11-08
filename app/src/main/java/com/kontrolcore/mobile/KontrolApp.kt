package com.kontrolcore.mobile

import android.app.Application

class KontrolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifier.ensureChannels(this)
    }
}
