package com.xijie.focustimer.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.xijie.focustimer.R
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.timer.TimerService
import com.xijie.focustimer.ui.MainActivity
import com.xijie.focustimer.util.Format
import com.xijie.focustimer.util.Stats

object Notifier {

    // v2：通知渠道创建后不可修改，改"锁屏可见"必须换新 id
    const val CH_ONGOING = "ongoing_v3"
    const val CH_ALERT = "alert"
    const val ONGOING_ID = 1001
    private const val ALERT_ID = 1100

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        // 清掉旧渠道（渠道创建后属性不能改，只能换 id 重建）
        for (old in listOf("ongoing", "ongoing_v2")) {
            try {
                nm.deleteNotificationChannel(old)
            } catch (t: Throwable) {
            }
        }
        if (nm.getNotificationChannel(CH_ONGOING) == null) {
            // 注意：不能用 IMPORTANCE_LOW —— 低重要性会被系统归入"静默通知"，
            // 锁屏上默认折叠/不显示。用 DEFAULT + 关闭声音，才能正常显示在锁屏。
            val ch = NotificationChannel(CH_ONGOING, "计时中", NotificationManager.IMPORTANCE_DEFAULT)
            ch.setShowBadge(false)
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(CH_ALERT) == null) {
            val ch = NotificationChannel(CH_ALERT, "越界提醒", NotificationManager.IMPORTANCE_DEFAULT)
            ch.setSound(null, null)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    private fun pauseIntent(ctx: Context, resume: Boolean): PendingIntent =
        PendingIntent.getService(
            ctx,
            if (resume) 2004 else 2005,
            Intent(ctx, TimerService::class.java)
                .setAction(if (resume) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun stopIntent(ctx: Context): PendingIntent =
        PendingIntent.getService(
            ctx, 2002,
            Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 2003,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * 用系统自带的计时器（setUsesChronometer）显示已用时长：
     * 由系统每秒自己刷新，锁屏上也实时走，不用我们一秒一次地唤醒 CPU。
     */
    fun ongoing(ctx: Context, r: Repo.Running, elapsedMs: Long, timeUp: Boolean): Notification {
        val text = when {
            r.paused -> "已暂停 · " + Format.clock(elapsedMs)
            timeUp -> "已到时间 · 继续计时中（点停止结束）"
            else -> "计划 " + r.plannedMinutes + " 分钟 · 计时中"
        }
        val b = NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(r.projectName)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent(ctx))
        if (r.paused) {
            b.setShowWhen(false)
        } else {
            b.setWhen(r.startWall)
            b.setUsesChronometer(true)
        }
        b.addAction(0, if (r.paused) "继续" else "暂停", pauseIntent(ctx, r.paused))
        b.addAction(0, "停止并保存", stopIntent(ctx))
        return b.build()
    }

    fun updateOngoing(ctx: Context, r: Repo.Running, elapsedMs: Long, timeUp: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.notify(ONGOING_ID, ongoing(ctx, r, elapsedMs, timeUp))
        } catch (t: Throwable) {
            // 通知权限缺失时忽略
        }
    }

    fun alert(ctx: Context, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent(ctx))
            .build()
        try {
            nm.notify(ALERT_ID, n)
        } catch (t: Throwable) {
            // 忽略
        }
    }

    /** 锁屏自检用：发一条和计时同渠道的通知，用来判断锁屏显示是否正常 */
    fun testOngoing(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("锁屏自检通知")
            .setContentText("如果你在锁屏上看到这一条，说明锁屏通知是通的")
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent(ctx))
            .build()
        try {
            nm.notify(1200, n)
        } catch (t: Throwable) {
        }
    }

    /** 清掉自检用的测试通知 */
    fun cancelTest(ctx: Context) {
        try {
            ctx.getSystemService(NotificationManager::class.java)?.cancel(1200)
        } catch (t: Throwable) {
        }
    }

    /** 负面项目越界提醒：单日上限 / 单周上限，各自去重一次 */
    fun checkThresholds(ctx: Context, projectId: Long) {
        val p = Repo.project(projectId) ?: return
        if (!p.negative) return
        val now = System.currentTimeMillis()
        if (p.dailyLimitMinutes > 0) {
            val from = Stats.todayStart()
            val used = Stats.sumMs(Repo.sessionsBetween(projectId, from, now + 1))
            if (used > p.dailyLimitMinutes * 60_000L) {
                val key = "d_${projectId}_$from"
                if (!Repo.warned(key)) {
                    Repo.markWarned(key)
                    alert(ctx, p.name, "今日已超单日上限 ${p.dailyLimitMinutes} 分钟（已 ${Format.duration(used)}）")
                }
            }
        }
        if (p.weeklyLimitMinutes > 0) {
            val from = Stats.weekStart()
            val used = Stats.sumMs(Repo.sessionsBetween(projectId, from, now + 1))
            if (used > p.weeklyLimitMinutes * 60_000L) {
                val key = "w_${projectId}_$from"
                if (!Repo.warned(key)) {
                    Repo.markWarned(key)
                    alert(ctx, p.name, "本周已超单周上限 ${p.weeklyLimitMinutes} 分钟（已 ${Format.duration(used)}）")
                }
            }
        }
    }
}
