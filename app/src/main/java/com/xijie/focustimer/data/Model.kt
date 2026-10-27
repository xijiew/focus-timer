package com.xijie.focustimer.data

/** 项目：一个专注事项 */
data class Project(
    val id: Long = 0L,
    val name: String,
    val color: Int,
    val negative: Boolean = false,
    val defaultMinutes: Int = 25,
    val sound: String = "bell",
    /** 自定义音效的 content:// 地址（sound == "custom" 时使用） */
    val soundUri: String = "",
    val dailyLimitMinutes: Int = 0,
    val weeklyLimitMinutes: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 上一次在 App 里点“开始”时设定的分钟数（小部件优先用它） */
    val lastMinutes: Int = 0
) {
    val soundLabel: String
        get() = when (sound) {
            "ding" -> "叮"
            "bell" -> "钟"
            "bowl" -> "钵"
            "custom" -> "自定义"
            else -> "关闭"
        }

    /** 小部件点击时用的时长：上次 App 里设的优先，没有则用默认 */
    val widgetMinutes: Int
        get() = if (lastMinutes in 1..1440) lastMinutes else defaultMinutes
}

/** 一次专注会话 */
data class FocusSession(
    val id: Long = 0L,
    val projectId: Long,
    val startAt: Long,
    val endAt: Long,
    val plannedMinutes: Int,
    val chimeFired: Boolean
) {
    val durationMs: Long get() = (endAt - startAt).coerceAtLeast(0L)
}
