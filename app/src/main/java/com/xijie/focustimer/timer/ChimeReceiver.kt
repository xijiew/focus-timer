package com.xijie.focustimer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.notify.Notifier
import com.xijie.focustimer.widget.TimerWidgetProvider

/**
 * 到点提示：只响一声，然后继续静默计时（不停止）。
 * 在休眠/锁屏下被闹钟唤醒时，持一个短暂的唤醒锁，保证这一声真的发得出来，
 * 不会等到用户点亮屏幕才响。
 */
class ChimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val r = Repo.running() ?: return
        if (r.chimeFired) return
        Repo.markChimeFired()

        val app = context.applicationContext
        val pm = app.getSystemService(PowerManager::class.java)
        var lock: PowerManager.WakeLock? = null
        try {
            lock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "focus-timer:chime")
            lock?.acquire(20_000L)
        } catch (t: Throwable) {
            lock = null
        }
        val held = lock
        ChimePlayer.play(app, r.sound, r.soundUri) {
            try {
                if (held != null && held.isHeld) held.release()
            } catch (t: Throwable) {
            }
        }

        val elapsed = (SystemClock.elapsedRealtime() - r.startElapsed).coerceAtLeast(0L)
        Notifier.updateOngoing(app, r, elapsed, timeUp = true)
        TimerWidgetProvider.notifyChanged(app)
    }
}
