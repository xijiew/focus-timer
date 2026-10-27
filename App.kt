package com.xijie.focustimer

import android.app.Application
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.notify.Notifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifier.ensureChannels(this)
    }
}
