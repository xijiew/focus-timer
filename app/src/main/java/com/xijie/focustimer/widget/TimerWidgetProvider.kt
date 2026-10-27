package com.xijie.focustimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.xijie.focustimer.R
import com.xijie.focustimer.data.Project
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.timer.TimerService

/**
 * 桌面小部件：
 * - 放置/点击齿轮时配置项目与时长来源
 * - 点一下开始、再点一下停止，不打开 App
 * - 支持 2x1 与 2x2（按系统给的尺寸自动换布局）
 * - 绑定的项目被删除后，点一下会弹出重新选择项目
 */
class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val e = prefs(context).edit()
        appWidgetIds.forEach { id ->
            e.remove(keyPid(id))
            e.remove(keyMode(id))
            e.remove(keyMin(id))
        }
        e.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val pid = projectIdOf(context, widgetId)
                val p = if (pid > 0L) Repo.project(pid) else null
                if (p == null) {
                    // 没绑项目、或项目已被删除 → 让用户重新选
                    try {
                        context.startActivity(
                            Intent(context, WidgetConfigActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    } catch (t: Throwable) {
                    }
                } else {
                    val minutes = minutesFor(context, widgetId, p)
                    val running = Repo.running()
                    when {
                        running != null && running.projectId == p.id -> TimerService.stop(context)
                        running != null -> {
                            TimerService.stop(context)
                            TimerService.start(context, p.id, minutes)
                        }
                        else -> TimerService.start(context, p.id, minutes)
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

    private fun isLarge(mgr: AppWidgetManager, widgetId: Int): Boolean {
        return try {
            val o = mgr.getAppWidgetOptions(widgetId)
            val h = maxOf(
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            )
            h >= 130
        } catch (t: Throwable) {
            false
        }
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val large = isLarge(mgr, widgetId)
        val views = RemoteViews(context.packageName, R.layout.widget_timer)
        val timeSize = if (large) 44f else 20f
        views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, timeSize)
        views.setTextViewTextSize(R.id.widget_chrono, TypedValue.COMPLEX_UNIT_SP, timeSize)
        views.setViewVisibility(R.id.widget_badge, if (large) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_action, if (large) View.VISIBLE else View.GONE)
        val pid = projectIdOf(context, widgetId)
        val p = if (pid > 0L) Repo.project(pid) else null
        val running = Repo.running()
        val active = p != null && running != null && running.projectId == p.id

        views.setTextViewText(
            R.id.widget_badge,
            if (p != null && p.name.isNotEmpty()) p.name.take(1) else "·"
        )

        if (p == null) {
            views.setViewVisibility(R.id.widget_chrono, View.GONE)
            views.setViewVisibility(R.id.widget_time, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_name,
                if (pid > 0L) "项目已删除" else "未选择项目"
            )
            views.setTextViewText(R.id.widget_time, "--")
            views.setTextViewText(R.id.widget_hint, if (large) "点一下重新选择项目" else "")
            if (large) views.setTextViewText(R.id.widget_action, "选择项目")
        } else if (active && running != null) {
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
                if (!large) "" else if (running.chimeFired) "已到时间 · 继续计时中" else "计划 ${running.plannedMinutes} 分"
            )
            if (large) views.setTextViewText(R.id.widget_action, "■ 停止")
        } else {
            views.setViewVisibility(R.id.widget_chrono, View.GONE)
            views.setViewVisibility(R.id.widget_time, View.VISIBLE)
            views.setTextViewText(R.id.widget_name, p.name)
            views.setTextViewText(R.id.widget_time, minutesFor(context, widgetId, p).toString() + " 分")
            views.setTextViewText(R.id.widget_hint, if (large) "点一下开始" else "")
            if (large) views.setTextViewText(R.id.widget_action, "▶ 开始")
        }

        val click = if (p == null) configIntent(context, widgetId) else toggleIntent(context, widgetId)
        views.setOnClickPendingIntent(R.id.widget_root, click)
        views.setOnClickPendingIntent(R.id.widget_cfg, configIntent(context, widgetId))
        views.setOnClickPendingIntent(R.id.widget_action, click)

        mgr.updateAppWidget(widgetId, views)
    }

    private fun toggleIntent(context: Context, widgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            100 + widgetId,
            Intent(context, TimerWidgetProvider::class.java)
                .setAction(ACTION_TOGGLE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun configIntent(context: Context, widgetId: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            500 + widgetId,
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        const val ACTION_TOGGLE = "com.xijie.focustimer.WIDGET_TOGGLE"
        const val ACTION_STATE_CHANGED = "com.xijie.focustimer.STATE_CHANGED"
        private const val PREFS = "widget_state"

        const val MODE_LAST = "last"
        const val MODE_DEFAULT = "default"
        const val MODE_CUSTOM = "custom"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun keyPid(id: Int) = "w_" + id
        private fun keyMode(id: Int) = "w_" + id + "_mode"
        private fun keyMin(id: Int) = "w_" + id + "_min"

        fun projectIdOf(context: Context, widgetId: Int): Long =
            prefs(context).getLong(keyPid(widgetId), -1L)

        fun timeModeOf(context: Context, widgetId: Int): String =
            prefs(context).getString(keyMode(widgetId), MODE_LAST) ?: MODE_LAST

        fun customMinutesOf(context: Context, widgetId: Int): Int =
            prefs(context).getInt(keyMin(widgetId), 0)

        fun setConfig(context: Context, widgetId: Int, projectId: Long, mode: String, customMinutes: Int) {
            prefs(context).edit()
                .putLong(keyPid(widgetId), projectId)
                .putString(keyMode(widgetId), mode)
                .putInt(keyMin(widgetId), customMinutes)
                .apply()
        }

        /** 小部件点击时用多少分钟 */
        fun minutesFor(context: Context, widgetId: Int, p: Project?): Int {
            if (p == null) return 25
            return when (timeModeOf(context, widgetId)) {
                MODE_DEFAULT -> p.defaultMinutes
                MODE_CUSTOM -> {
                    val c = customMinutesOf(context, widgetId)
                    if (c in 1..1440) c else p.widgetMinutes
                }
                else -> p.widgetMinutes
            }
        }

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            TimerWidgetProvider().onUpdate(context, mgr, ids)
        }

        /** 计时状态变化时让所有小部件刷新 */
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
