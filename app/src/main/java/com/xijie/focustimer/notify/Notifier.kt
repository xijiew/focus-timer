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
    const val CH_ONGOING = "ongoing_v2"
    const val CH_ALERT = "alert"
    const val ONGOING_ID = 1001
    private const val ALERT_ID = 1100

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        // 清掉旧渠道（v1 的 ongoing 不能改属性了）
        try {
            nm.deleteNotificationChannel("ongoing")
        } catch (t: Throwable) {
        }
        if (nm.getNotificationChannel(CH_ONGOING) == null) {
            val ch = NotificationChannel(CH_ONGOING, "计时中", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            ch.setSound(null, null)
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
        val text = if (timeUp) {
            "已到时间 · 继续计时中（点停止结束）"
        } else {
            "计划 " + r.plannedMinutes + " 分钟 · 计时中"
        }
        return NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(r.projectName)
            .setContentText(text)
            .setWhen(r.startWall)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent(ctx))
            .addAction(0, "停止并保存", stopIntent(ctx))
            .build()
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
