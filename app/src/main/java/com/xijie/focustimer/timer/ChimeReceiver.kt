package com.xijie.focustimer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.notify.Notifier
import com.xijie.focustimer.widget.TimerWidgetProvider

/** 到点提示：只响一声，然后继续静默计时（不停止） */
class ChimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val r = Repo.running() ?: return
        if (r.chimeFired) return
        ChimePlayer.play(context.applicationContext, r.sound)
        Repo.markChimeFired()
        val elapsed = (SystemClock.elapsedRealtime() - r.startElapsed).coerceAtLeast(0L)
        Notifier.updateOngoing(context.applicationContext, r, elapsed, timeUp = true)
        TimerWidgetProvider.notifyChanged(context.applicationContext)
    }
}
