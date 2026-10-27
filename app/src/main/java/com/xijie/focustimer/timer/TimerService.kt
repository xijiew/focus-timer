package com.xijie.focustimer.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
    private var power: PowerManager? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        power = getSystemService(PowerManager::class.java)
        // 亮屏/解锁时立刻恢复每秒走字（息屏期间小部件看不见，不必刷）
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (power?.isInteractive == true) {
                    if (Repo.running() != null) startTicker()
                    TimerWidgetProvider.updateAll(this@TimerService)
                }
            }
        }
        screenReceiver = r
        try {
            registerReceiver(r, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            })
        } catch (t: Throwable) {
        }
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
            ACTION_PAUSE -> {
                pause()
                return START_STICKY
            }
            ACTION_RESUME -> {
                resume()
                return START_STICKY
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
        var p = Repo.project(projectId)
        if (p == null) {
            stopSelf()
            return
        }
        // 记住这一次设的时长，下次点桌面小部件就用它
        if (p.lastMinutes != minutes) {
            Repo.saveProject(p.copy(lastMinutes = minutes))
            p = Repo.project(projectId) ?: p
        }
        val running = Repo.Running(
            projectId = p.id,
            projectName = p.name,
            color = p.color,
            startWall = System.currentTimeMillis(),
            startElapsed = SystemClock.elapsedRealtime(),
            plannedMinutes = minutes,
            sound = p.sound,
            soundUri = p.soundUri,
            chimeFired = false,
            accumMs = 0L,
            paused = false
        )
        Repo.saveRunning(running)
        startAsForeground(running)
        scheduleChime(running.startWall + minutes * 60_000L, p.name)
        startTicker()
        refreshWidgets()
    }

    /** 暂停：把当前这一段累加进去，时间停住，闹钟取消 */
    private fun pause() {
        val r = Repo.running() ?: return
        if (r.paused) return
        val e = Repo.elapsedMs(r)
        Repo.saveRunning(r.copy(accumMs = e, startElapsed = SystemClock.elapsedRealtime(), paused = true))
        cancelChime()
        val nr = Repo.running() ?: return
        try {
            Notifier.updateOngoing(this, nr, e, nr.chimeFired)
        } catch (t: Throwable) {
        }
        refreshWidgets()
    }

    /** 继续：重新开始走秒，并按剩余时间重排到点闹钟 */
    private fun resume() {
        val r = Repo.running() ?: return
        if (!r.paused) return
        Repo.saveRunning(r.copy(startElapsed = SystemClock.elapsedRealtime(), paused = false))
        val nr = Repo.running() ?: return
        val elapsed = Repo.elapsedMs(nr)
        val remaining = nr.plannedMinutes * 60_000L - elapsed
        if (!nr.chimeFired && remaining > 0L) {
            scheduleChime(System.currentTimeMillis() + remaining, nr.projectName)
        }
        startTicker()
        try {
            Notifier.updateOngoing(this, nr, elapsed, nr.chimeFired)
        } catch (t: Throwable) {
        }
        refreshWidgets()
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

    /** 状态变化后立刻刷一次，并在 700ms 后再刷一次，压掉乱序/过期刷新造成的按钮错位 */
    private fun refreshWidgets() {
        TimerWidgetProvider.updateAll(this)
        try {
            scope.launch {
                delay(700L)
                TimerWidgetProvider.updateAll(this@TimerService)
            }
        } catch (t: Throwable) {
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var n = 0
            while (true) {
                val r = Repo.running() ?: break
                val screenOn = power?.isInteractive ?: true
                if (!r.paused) {
                    if (screenOn) {
                        // 亮屏：小部件数字每秒刷新（人看得见才刷）
                        TimerWidgetProvider.updateAll(this@TimerService)
                        if (n % 30 == 0) safeNotify(r)
                    } else if (n % 30 == 0) {
                        // 息屏：只保留 30 秒一次的通知刷新（通知走系统 Chronometer）
                        safeNotify(r)
                    }
                }
                n++
                delay(if (screenOn) 1000L else 30_000L)
            }
        }
    }

    private fun safeNotify(r: Repo.Running) {
        try {
            Notifier.updateOngoing(this, r, Repo.elapsedMs(r), r.chimeFired)
        } catch (t: Throwable) {
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
        refreshWidgets()
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
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Throwable) {
            }
        }
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.xijie.focustimer.action.START"
        const val ACTION_STOP = "com.xijie.focustimer.action.STOP"
        const val ACTION_CHIME = "com.xijie.focustimer.action.CHIME"
        const val ACTION_PAUSE = "com.xijie.focustimer.action.PAUSE"
        const val ACTION_RESUME = "com.xijie.focustimer.action.RESUME"
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

        fun pause(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, TimerService::class.java).setAction(ACTION_PAUSE))
            } catch (t: Throwable) {
            }
        }

        fun resume(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, TimerService::class.java).setAction(ACTION_RESUME))
            } catch (t: Throwable) {
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
