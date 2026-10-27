package com.xijie.focustimer

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.notify.Notifier
import com.xijie.focustimer.widget.TimerWidgetProvider

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifier.ensureChannels(this)
        // 系统切到深色/浅色模式时，立刻让桌面小部件跟着换底
        try {
            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    TimerWidgetProvider.updateAll(this@App)
                }
            }, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        } catch (t: Throwable) {
        }
    }
}
