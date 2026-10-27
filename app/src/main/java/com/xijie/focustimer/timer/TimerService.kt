package com.xijie.focustimer.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.xijie.focustimer.data.FocusSession
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.notify.Notifier
import com.xijie.focustimer.widget.TimerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 前台服务：负责"到点响一声 → 继续静默计时 → 手动停止才结束"。
 * 计时不靠循环累加，而是记下开始时刻，用 elapsedRealtime 差算，后台/休眠都不丢时间。
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val pid = intent.getLongExtra(EXTRA_PROJECT_ID, -1L)
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                if (pid > 0L && minutes > 0) begin(pid, minutes) else stopSelf()
            }
            ACTION_STOP -> {
                finish()
                return START_NOT_STICKY
            }
            else -> {
                val r = Repo.running()
                if (r != null) {
                    startAsForeground(r)
                    if (!r.chimeFired) scheduleChime(r.startWall + r.plannedMinutes * 60_000L, r.projectName)
                    startTicker()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun begin(projectId: Long, minutes: Int) {
        // 若已有计时在跑，先把它结算掉，避免覆盖丢记录
        Repo.running()?.let { saveAndClear(it) }
        val p = Repo.project(projectId)
        if (p == null) {
            stopSelf()
            return
        }
        val running = Repo.Running(
            projectId = p.id,
            projectName = p.name,
            color = p.color,
            startWall = System.currentTimeMillis(),
            startElapsed = SystemClock.elapsedRealtime(),
            plannedMinutes = minutes,
            sound = p.sound,
            chimeFired = false
        )
        Repo.saveRunning(running)
        startAsForeground(running)
        scheduleChime(running.startWall + minutes * 60_000L, p.name)
        startTicker()
        TimerWidgetProvider.notifyChanged(this)
    }

    private fun startAsForeground(r: Repo.Running) {
        val n = Notifier.ongoing(this, r, Repo.elapsedMs(r), r.chimeFired)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, Notifier.ONGOING_ID, n, type)
        } catch (t: Throwable) {
            // 通知权限缺失等情况下仍然继续计时
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val r = Repo.running() ?: break
                Notifier.updateOngoing(this@TimerService, r, Repo.elapsedMs(r), r.chimeFired)
                TimerWidgetProvider.notifyChanged(this@TimerService)
                delay(30_000L)
            }
        }
    }

    private fun saveAndClear(r: Repo.Running): Long {
        val end = r.startWall + Repo.elapsedMs(r)
        val id = Repo.addSession(
            FocusSession(
                projectId = r.projectId,
                startAt = r.startWall,
                endAt = end,
                plannedMinutes = r.plannedMinutes,
                chimeFired = r.chimeFired
            )
        )
        Repo.clearRunning()
        return id
    }

    private fun finish() {
        val r = Repo.running()
        if (r != null) {
            saveAndClear(r)
            cancelChime()
            Notifier.checkThresholds(this, r.projectId)
        }
        ticker?.cancel()
        TimerWidgetProvider.notifyChanged(this)
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (t: Throwable) {
        }
        stopSelf()
    }

    private fun chimePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,
        REQ_CHIME,
        Intent(this, ChimeReceiver::class.java).setAction(ACTION_CHIME),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleChime(atWall: Long, label: String) {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pi = chimePendingIntent()
        // 优先用"闹钟时钟"闹钟（setAlarmClock）：系统会把它当真正的闹钟对待，
        // 深度休眠 / 锁屏 / 后台严管下也能准点唤醒，这是锁屏不响的关键。
        try {
            val show = PendingIntent.getActivity(
                this,
                REQ_SHOW,
                Intent(this, com.xijie.focustimer.ui.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atWall, show), pi)
            return
        } catch (t: Throwable) {
            // 落到精确闹钟
        }
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWall, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWall, pi)
            }
        } catch (t: Throwable) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWall, pi)
            } catch (t2: Throwable) {
            }
        }
    }

    private fun cancelChime() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        try {
            am.cancel(chimePendingIntent())
        } catch (t: Throwable) {
        }
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.xijie.focustimer.action.START"
        const val ACTION_STOP = "com.xijie.focustimer.action.STOP"
        const val ACTION_CHIME = "com.xijie.focustimer.action.CHIME"
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_MINUTES = "minutes"
        private const val REQ_CHIME = 9001
        private const val REQ_SHOW = 9002

        fun start(ctx: Context, projectId: Long, minutes: Int) {
            val i = Intent(ctx, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROJECT_ID, projectId)
                .putExtra(EXTRA_MINUTES, minutes)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (t: Throwable) {
                // Android 12+ 后台限制：从桌面小部件点击属于用户交互，通常允许；失败则忽略
            }
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, TimerService::class.java).setAction(ACTION_STOP)
            try {
                ctx.startService(i)
            } catch (t: Throwable) {
                try {
                    ContextCompat.startForegroundService(ctx, i)
                } catch (t2: Throwable) {
                }
            }
        }
    }
}
