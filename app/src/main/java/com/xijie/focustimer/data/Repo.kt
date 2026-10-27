package com.xijie.focustimer.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock

object Repo {

    private lateinit var helper: Db
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        helper = Db(ctx.applicationContext)
        prefs = ctx.applicationContext.getSharedPreferences("focus_state", Context.MODE_PRIVATE)
    }

    // ---------- 项目 ----------
    fun projects(): List<Project> = helper.projects()
    fun project(id: Long): Project? = helper.project(id)
    fun saveProject(p: Project): Long {
        if (p.id == 0L) return helper.insertProject(p)
        helper.updateProject(p)
        return p.id
    }
    fun deleteProject(id: Long) = helper.deleteProject(id)

    // ---------- 会话 ----------
    fun addSession(s: FocusSession): Long = helper.insertSession(s)
    fun sessionsOf(projectId: Long): List<FocusSession> = helper.sessionsOf(projectId)
    fun sessionsBetween(projectId: Long, from: Long, to: Long): List<FocusSession> =
        helper.sessionsOf(projectId, from, to)
    fun allSessions(): List<FocusSession> = helper.allSessions()

    // ---------- 正在计时 ----------
    data class Running(
        val projectId: Long,
        val projectName: String,
        val color: Int,
        val startWall: Long,
        val startElapsed: Long,
        val plannedMinutes: Int,
        val sound: String,
        val soundUri: String,
        val chimeFired: Boolean
    )

    fun running(): Running? {
        val id = prefs.getLong("run_projectId", -1L)
        if (id <= 0L) return null
        return Running(
            projectId = id,
            projectName = prefs.getString("run_name", "") ?: "",
            color = prefs.getInt("run_color", 0),
            startWall = prefs.getLong("run_startWall", 0L),
            startElapsed = prefs.getLong("run_startElapsed", 0L),
            plannedMinutes = prefs.getInt("run_planned", 0),
            sound = prefs.getString("run_sound", "bell") ?: "bell",
            soundUri = prefs.getString("run_soundUri", "") ?: "",
            chimeFired = prefs.getBoolean("run_chime", false)
        )
    }

    fun saveRunning(r: Running) {
        prefs.edit()
            .putLong("run_projectId", r.projectId)
            .putString("run_name", r.projectName)
            .putInt("run_color", r.color)
            .putLong("run_startWall", r.startWall)
            .putLong("run_startElapsed", r.startElapsed)
            .putInt("run_planned", r.plannedMinutes)
            .putString("run_sound", r.sound)
            .putString("run_soundUri", r.soundUri)
            .putBoolean("run_chime", r.chimeFired)
            .apply()
    }

    fun markChimeFired() {
        prefs.edit().putBoolean("run_chime", true).apply()
    }

    fun clearRunning() {
        prefs.edit().clear().apply()
    }

    fun elapsedMs(r: Running): Long = (SystemClock.elapsedRealtime() - r.startElapsed).coerceAtLeast(0L)

    // ---------- 提醒去重 ----------
    fun warned(key: String): Boolean = prefs.getBoolean("warn_$key", false)
    fun markWarned(key: String) {
        prefs.edit().putBoolean("warn_$key", true).apply()
    }
}
