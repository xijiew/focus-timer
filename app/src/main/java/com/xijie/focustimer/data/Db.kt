package com.xijie.focustimer.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(context: Context) : SQLiteOpenHelper(context, "focus.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE projects(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "color INTEGER NOT NULL," +
                "negative INTEGER NOT NULL DEFAULT 0," +
                "defaultMinutes INTEGER NOT NULL DEFAULT 25," +
                "sound TEXT NOT NULL DEFAULT 'bell'," +
                "soundUri TEXT NOT NULL DEFAULT ''," +
                "lastMinutes INTEGER NOT NULL DEFAULT 0," +
                "dailyLimitMinutes INTEGER NOT NULL DEFAULT 0," +
                "weeklyLimitMinutes INTEGER NOT NULL DEFAULT 0," +
                "createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE sessions(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "projectId INTEGER NOT NULL," +
                "startAt INTEGER NOT NULL," +
                "endAt INTEGER NOT NULL," +
                "plannedMinutes INTEGER NOT NULL," +
                "chimeFired INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_sessions_project ON sessions(projectId)")
        db.execSQL("CREATE INDEX idx_sessions_start ON sessions(startAt)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE projects ADD COLUMN soundUri TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE projects ADD COLUMN lastMinutes INTEGER NOT NULL DEFAULT 0")
        }
    }

    // ---------------- projects ----------------

    fun projects(): List<Project> {
        val out = ArrayList<Project>()
        readableDatabase.rawQuery("SELECT id,name,color,negative,defaultMinutes,sound,dailyLimitMinutes,weeklyLimitMinutes,createdAt,soundUri,lastMinutes FROM projects ORDER BY createdAt ASC", null).use { c ->
            while (c.moveToNext()) out.add(readProject(c))
        }
        return out
    }

    fun project(id: Long): Project? {
        readableDatabase.rawQuery("SELECT id,name,color,negative,defaultMinutes,sound,dailyLimitMinutes,weeklyLimitMinutes,createdAt,soundUri,lastMinutes FROM projects WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) return readProject(c)
        }
        return null
    }

    private fun readProject(c: Cursor) = Project(
        id = c.getLong(0),
        name = c.getString(1),
        color = c.getInt(2),
        negative = c.getInt(3) != 0,
        defaultMinutes = c.getInt(4),
        sound = c.getString(5),
        dailyLimitMinutes = c.getInt(6),
        weeklyLimitMinutes = c.getInt(7),
        createdAt = c.getLong(8),
        soundUri = c.getString(9) ?: "",
        lastMinutes = c.getInt(10)
    )

    fun insertProject(p: Project): Long {
        val v = ContentValues().apply {
            put("name", p.name)
            put("color", p.color)
            put("negative", if (p.negative) 1 else 0)
            put("defaultMinutes", p.defaultMinutes)
            put("sound", p.sound)
            put("soundUri", p.soundUri)
            put("lastMinutes", p.lastMinutes)
            put("dailyLimitMinutes", p.dailyLimitMinutes)
            put("weeklyLimitMinutes", p.weeklyLimitMinutes)
            put("createdAt", p.createdAt)
        }
        return writableDatabase.insert("projects", null, v)
    }

    fun updateProject(p: Project) {
        val v = ContentValues().apply {
            put("name", p.name)
            put("color", p.color)
            put("negative", if (p.negative) 1 else 0)
            put("defaultMinutes", p.defaultMinutes)
            put("sound", p.sound)
            put("soundUri", p.soundUri)
            put("lastMinutes", p.lastMinutes)
            put("dailyLimitMinutes", p.dailyLimitMinutes)
            put("weeklyLimitMinutes", p.weeklyLimitMinutes)
        }
        writableDatabase.update("projects", v, "id=?", arrayOf(p.id.toString()))
    }

    fun deleteProject(id: Long) {
        writableDatabase.delete("projects", "id=?", arrayOf(id.toString()))
        writableDatabase.delete("sessions", "projectId=?", arrayOf(id.toString()))
    }

    // ---------------- sessions ----------------

    fun insertSession(s: FocusSession): Long {
        val v = ContentValues().apply {
            put("projectId", s.projectId)
            put("startAt", s.startAt)
            put("endAt", s.endAt)
            put("plannedMinutes", s.plannedMinutes)
            put("chimeFired", if (s.chimeFired) 1 else 0)
        }
        return writableDatabase.insert("sessions", null, v)
    }

    fun sessionsOf(projectId: Long, from: Long = 0L, to: Long = Long.MAX_VALUE): List<FocusSession> {
        val out = ArrayList<FocusSession>()
        readableDatabase.rawQuery(
            "SELECT id,projectId,startAt,endAt,plannedMinutes,chimeFired FROM sessions WHERE projectId=? AND startAt>=? AND startAt<? ORDER BY startAt DESC",
            arrayOf(projectId.toString(), from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(
                FocusSession(
                    id = c.getLong(0),
                    projectId = c.getLong(1),
                    startAt = c.getLong(2),
                    endAt = c.getLong(3),
                    plannedMinutes = c.getInt(4),
                    chimeFired = c.getInt(5) != 0
                )
            )
        }
        return out
    }

    fun allSessions(): List<FocusSession> {
        val out = ArrayList<FocusSession>()
        readableDatabase.rawQuery(
            "SELECT id,projectId,startAt,endAt,plannedMinutes,chimeFired FROM sessions ORDER BY startAt ASC", null
        ).use { c ->
            while (c.moveToNext()) out.add(
                FocusSession(
                    id = c.getLong(0),
                    projectId = c.getLong(1),
                    startAt = c.getLong(2),
                    endAt = c.getLong(3),
                    plannedMinutes = c.getInt(4),
                    chimeFired = c.getInt(5) != 0
                )
            )
        }
        return out
    }

    fun clearAll() {
        writableDatabase.delete("sessions", null, null)
        writableDatabase.delete("projects", null, null)
    }

    fun insertSessionRaw(s: FocusSession) {
        insertSession(s)
    }
}
