package com.xijie.focustimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.xijie.focustimer.R
import com.xijie.focustimer.data.Project
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.timer.TimerService
import com.xijie.focustimer.ui.MainActivity
import com.xijie.focustimer.util.Format

/** 与项目编辑页的 PALETTE 顺序保持一致 */
private val PALETTE = intArrayOf(
    0xFF3F51B5.toInt(), 0xFF00897B.toInt(), 0xFFEF6C00.toInt(),
    0xFFC62828.toInt(), 0xFF6A1B9A.toInt(), 0xFF546E7A.toInt()
)
private val BADGE_RES = intArrayOf(
    com.xijie.focustimer.R.drawable.badge_p0, com.xijie.focustimer.R.drawable.badge_p1,
    com.xijie.focustimer.R.drawable.badge_p2, com.xijie.focustimer.R.drawable.badge_p3,
    com.xijie.focustimer.R.drawable.badge_p4, com.xijie.focustimer.R.drawable.badge_p5
)
private val PILL_RES = intArrayOf(
    com.xijie.focustimer.R.drawable.pill_p0, com.xijie.focustimer.R.drawable.pill_p1,
    com.xijie.focustimer.R.drawable.pill_p2, com.xijie.focustimer.R.drawable.pill_p3,
    com.xijie.focustimer.R.drawable.pill_p4, com.xijie.focustimer.R.drawable.pill_p5
)

private fun paletteIndex(color: Int): Int {
    val i = PALETTE.indexOfFirst { it == color }
    return if (i in PALETTE.indices) i else 0
}

/**
 * 桌面小部件基类。
 * - 2×1 用本类（size 自适应：被用户拉大也能撑成 2×2 版式）
 * - 2×2 用子类 [TimerWidget2Provider]（尺寸固定大号，桌面小部件面板里可直接选）
 * 交互：只有胶囊按钮有功能，点时长/项目名等空白处＝打开 App。
 */
open class TimerWidgetProvider : AppWidgetProvider() {

    /** 2×2 专用提供者覆写为 true */
    open val forceLarge: Boolean = false

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
                    if (running != null && running.projectId == p.id) {
                        TimerService.stop(context)
                    } else {
                        // 换项目直接 start，服务内部会把上一段结算掉；不要 stop→start 连打
                        TimerService.start(context, p.id, minutes)
                    }
                }
                updateAll(context)
                return
            }
            ACTION_PAUSE_TOGGLE -> {
                val r = Repo.running()
                if (r != null) {
                    if (r.paused) TimerService.resume(context) else TimerService.pause(context)
                }
                updateAll(context)
                return
            }
            ACTION_STOP_NOW -> {
                TimerService.stop(context)
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

    private fun dp(context: Context, v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

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
        val large = forceLarge || isLarge(mgr, widgetId)
        val night = isNight(context)

        val pid = projectIdOf(context, widgetId)
        val p = if (pid > 0L) Repo.project(pid) else null
        val running = Repo.running()
        val active = p != null && running != null && running.projectId == p.id

        // 状态切换一定换 layout id：布局不同会强制桌面重新 inflate，
        // 彻底避免「隐藏过的按钮残留、深色下多出一个停止胶囊」这类脏状态
        val layout = when {
            large && active -> R.layout.widget_timer_run          // 2×2 运行
            large -> R.layout.widget_timer                        // 2×2 空闲
            active -> R.layout.widget_timer_small_run             // 2×1 运行
            else -> R.layout.widget_timer_small                   // 2×1 空闲
        }
        val views = RemoteViews(context.packageName, layout)

        // ---- 尺寸 & 深色模式 ----
        views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, if (large) 32f else 20f)
        views.setTextViewTextSize(R.id.widget_name, TypedValue.COMPLEX_UNIT_SP, if (large) 13f else 10f)
        views.setTextViewTextSize(R.id.widget_sub, TypedValue.COMPLEX_UNIT_SP, if (large) 11f else 10f)
        views.setViewPadding(
            R.id.widget_root,
            dp(context, if (large) 16 else 12),
            dp(context, if (large) 14 else 6),
            dp(context, if (large) 16 else 10),
            dp(context, if (large) 14 else 6)
        )
        views.setInt(
            R.id.widget_root, "setBackgroundResource",
            if (night) R.drawable.widget_bg_dark else R.drawable.widget_bg
        )
        views.setTextColor(R.id.widget_name, if (night) 0xFF9A9A9A.toInt() else 0xFF7A7A7A.toInt())
        views.setTextColor(R.id.widget_time, if (night) 0xFFF2F2F2.toInt() else 0xFF1A1A1A.toInt())
        views.setTextColor(R.id.widget_sub, if (night) 0xFF808080.toInt() else 0xFFB3B3B3.toInt())

        // 2×1 显示项目名（第一行）+ 时长/胶囊；2×2 额外显示圆标与计划副标题
        views.setViewVisibility(R.id.widget_badge, if (large) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_name, View.VISIBLE)
        views.setViewVisibility(R.id.widget_sub, if (large) View.VISIBLE else View.GONE)
        // 2×1 计时中只给一个「停止」胶囊（暂停在 App 内 / 通知栏操作）；2×2 仍是暂停+停止
        views.setViewVisibility(
            R.id.widget_action,
            if (active && !large) View.GONE else View.VISIBLE
        )
        if (active) {
            views.setViewVisibility(R.id.widget_stop, View.VISIBLE)
            views.setInt(
                R.id.widget_stop, "setBackgroundResource",
                if (night) R.drawable.pill_gray_dark else R.drawable.pill_gray_light
            )
            views.setImageViewResource(
                R.id.widget_stop_icon,
                if (night) R.drawable.ic_stop_light else R.drawable.ic_stop_dark
            )
        }
        val colorIdx = paletteIndex(p?.color ?: PALETTE[0])

        views.setTextViewText(
            R.id.widget_badge,
            if (p != null && p.name.isNotEmpty()) p.name.take(1) else "·"
        )
        views.setInt(R.id.widget_badge, "setBackgroundResource", BADGE_RES[colorIdx])

        if (p == null) {
            views.setTextViewText(R.id.widget_name, if (pid > 0L) "项目已删除" else "未选择项目")
            views.setTextViewText(R.id.widget_sub, "")
            views.setTextViewText(R.id.widget_time, "--")
            setAction(views, R.drawable.ic_play, PILL_RES[colorIdx])
        } else if (active && running != null) {
            views.setTextViewText(R.id.widget_name, p.name)
            // 数字由前台服务刷新（不用 Chronometer：小米桌面不吃运行时改字号/颜色）
            views.setTextViewText(R.id.widget_time, Format.clock(Repo.elapsedMs(running)))
            views.setTextViewText(
                R.id.widget_sub,
                when {
                    running.paused -> "已暂停"
                    running.chimeFired -> "已响过 · 继续计时"
                    else -> "计划 " + running.plannedMinutes + " 分"
                }
            )
            if (running.paused) {
                setAction(
                    views,
                    if (night) R.drawable.ic_play_dark else R.drawable.ic_play,
                    if (night) R.drawable.pill_white else R.drawable.pill_bg
                )
            } else {
                setAction(
                    views,
                    if (night) R.drawable.ic_pause_dark else R.drawable.ic_pause,
                    if (night) R.drawable.pill_white else R.drawable.pill_bg
                )
            }
        } else {
            views.setTextViewText(R.id.widget_name, p.name)
            val m = minutesFor(context, widgetId, p)
            views.setTextViewText(R.id.widget_time, if (large) "00:00" else m.toString() + " 分")
            views.setTextViewText(R.id.widget_sub, "计划 " + m + " 分")
            setAction(views, R.drawable.ic_play, PILL_RES[colorIdx])
        }

        // 点空白处（时长/项目名）＝打开 App；没绑项目时＝去选项目
        views.setOnClickPendingIntent(
            R.id.widget_root,
            if (p == null) configIntent(context, widgetId) else openAppIntent(context)
        )
        val actionClick = when {
            p == null -> configIntent(context, widgetId)
            active -> pauseIntent(context, widgetId)
            else -> toggleIntent(context, widgetId)
        }
        views.setOnClickPendingIntent(R.id.widget_action, actionClick)
        if (active) views.setOnClickPendingIntent(R.id.widget_stop, stopIntent(context, widgetId))
        mgr.updateAppWidget(widgetId, views)
    }

    /** 主胶囊只放图标（不要“暂停/停止”文字） */
    private fun setAction(views: RemoteViews, iconRes: Int, bgRes: Int) {
        views.setImageViewResource(R.id.widget_action_icon, iconRes)
        views.setInt(R.id.widget_action, "setBackgroundResource", bgRes)
    }

    private fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun toggleIntent(context: Context, widgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            100 + widgetId,
            Intent(context, TimerWidgetProvider::class.java)
                .setAction(ACTION_TOGGLE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun pauseIntent(context: Context, widgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            300 + widgetId,
            Intent(context, TimerWidgetProvider::class.java)
                .setAction(ACTION_PAUSE_TOGGLE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun stopIntent(context: Context, widgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            400 + widgetId,
            Intent(context, TimerWidgetProvider::class.java)
                .setAction(ACTION_STOP_NOW)
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

    /** 点时长/项目名等空白处：打开 App 主页，不开始计时 */
    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            600,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        const val ACTION_TOGGLE = "com.xijie.focustimer.WIDGET_TOGGLE"
        const val ACTION_STATE_CHANGED = "com.xijie.focustimer.STATE_CHANGED"
        const val ACTION_PAUSE_TOGGLE = "com.xijie.focustimer.WIDGET_PAUSE"
        const val ACTION_STOP_NOW = "com.xijie.focustimer.WIDGET_STOP"
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

        /** 两种尺寸的小部件都刷新 */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            for (cls in listOf(TimerWidgetProvider::class.java, TimerWidget2Provider::class.java)) {
                val ids = try {
                    mgr.getAppWidgetIds(ComponentName(context, cls))
                } catch (t: Throwable) {
                    IntArray(0)
                }
                if (ids.isEmpty()) continue
                val inst = try {
                    cls.getDeclaredConstructor().newInstance()
                } catch (t: Throwable) {
                    null
                } ?: continue
                try {
                    inst.onUpdate(context, mgr, ids)
                } catch (t: Throwable) {
                }
            }
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

/** 2×2 小部件：固定大号版式，桌面小部件面板里可直接选 */
class TimerWidget2Provider : TimerWidgetProvider() {
    override val forceLarge: Boolean = true
}
