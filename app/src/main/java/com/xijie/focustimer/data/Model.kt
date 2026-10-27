package com.xijie.focustimer.data

/** 项目：一个专注事项 */
data class Project(
    val id: Long = 0L,
    val name: String,
    val color: Int,
    val negative: Boolean = false,
    val defaultMinutes: Int = 25,
    val sound: String = "bell",
    val dailyLimitMinutes: Int = 0,
    val weeklyLimitMinutes: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val soundLabel: String
        get() = when (sound) {
            "ding" -> "叮"
            "bell" -> "钟"
            "bowl" -> "钵"
            else -> "关闭"
        }
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
