package com.xijie.focustimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.xijie.focustimer.R
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.timer.TimerService
import com.xijie.focustimer.util.Format

/**
 * 桌面小部件：放置时绑定项目；点一下开始、再点一下停止；时长用该项目上次设定的分钟数；不打开 App。
 */
class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val e = prefs(context).edit()
        appWidgetIds.forEach { e.remove(key(it)) }
        e.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val pid = projectIdOf(context, widgetId)
                if (pid > 0L) {
                    val running = Repo.running()
                    val proj = Repo.project(pid)
                    val minutes = proj?.widgetMinutes ?: 25
                    when {
                        running != null && running.projectId == pid -> TimerService.stop(context)
                        running != null -> {
                            TimerService.stop(context)
                            TimerService.start(context, pid, minutes)
                        }
                        else -> TimerService.start(context, pid, minutes)
                    }
                }
                updateAll(context)
                return
            }
            ACTION_STATE_CHANGED -> {
                updateAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_timer)
        val pid = projectIdOf(context, widgetId)
        val p = if (pid > 0L) Repo.project(pid) else null
        val running = Repo.running()
        val active = p != null && running != null && running.projectId == p.id

        if (p == null) {
            views.setViewVisibility(R.id.widget_chrono, View.GONE)
            views.setViewVisibility(R.id.widget_time, View.VISIBLE)
            views.setTextViewText(R.id.widget_name, "未选择项目")
            views.setTextViewText(R.id.widget_time, "--")
            views.setTextViewText(R.id.widget_hint, "删掉重放，先选一个项目")
        } else if (active && running != null) {
            // 用系统 Chronometer 在桌面自己走秒，不需要我们频繁刷新
            views.setViewVisibility(R.id.widget_time, View.GONE)
            views.setViewVisibility(R.id.widget_chrono, View.VISIBLE)
            views.setChronometer(
                R.id.widget_chrono,
                SystemClock.elapsedRealtime() - Repo.elapsedMs(running),
                null,
                true
            )
            views.setTextViewText(R.id.widget_name, p.name)
            views.setTextViewText(
                R.id.widget_hint,
                if (running.chimeFired) "已到时间 · 点停止"
                else "计划 ${running.plannedMinutes} 分"
            )
        } else {
            views.setViewVisibility(R.id.widget_chrono, View.GONE)
            views.setViewVisibility(R.id.widget_time, View.VISIBLE)
            views.setTextViewText(R.id.widget_name, p.name)
            views.setTextViewText(R.id.widget_time, p.widgetMinutes.toString() + " 分")
            views.setTextViewText(R.id.widget_hint, "点开始 · 再点停止")
        }

        val pi = PendingIntent.getBroadcast(
            context,
            100 + widgetId,
            Intent(context, TimerWidgetProvider::class.java)
                .setAction(ACTION_TOGGLE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        mgr.updateAppWidget(widgetId, views)
    }

    companion object {
        const val ACTION_TOGGLE = "com.xijie.focustimer.WIDGET_TOGGLE"
        const val ACTION_STATE_CHANGED = "com.xijie.focustimer.STATE_CHANGED"
        private const val PREFS = "widget_state"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun key(widgetId: Int) = "w_" + widgetId

        fun projectIdOf(context: Context, widgetId: Int): Long =
            prefs(context).getLong(key(widgetId), -1L)

        fun bindProject(context: Context, widgetId: Int, projectId: Long) {
            prefs(context).edit().putLong(key(widgetId), projectId).apply()
        }

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            val provider = TimerWidgetProvider()
            provider.onUpdate(context, mgr, ids)
        }

        /** 让所有小部件立刻刷新（计时状态变化时调用） */
        fun notifyChanged(context: Context) {
            try {
                context.sendBroadcast(
                    Intent(context, TimerWidgetProvider::class.java).setAction(ACTION_STATE_CHANGED)
                )
            } catch (t: Throwable) {
            }
        }
    }
}
