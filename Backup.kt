package com.xijie.focustimer.util

import android.content.Context
import android.net.Uri
import com.xijie.focustimer.data.FocusSession
import com.xijie.focustimer.data.Project
import com.xijie.focustimer.data.Repo
import org.json.JSONArray
import org.json.JSONObject

object Backup {

    private const val VERSION = 1

    fun exportJson(ctx: Context, uri: Uri): String {
        val root = JSONObject()
        root.put("app", "focus-timer")
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val pj = JSONArray()
        Repo.projects().forEach { p ->
            pj.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("color", p.color)
                put("negative", p.negative)
                put("defaultMinutes", p.defaultMinutes)
                put("sound", p.sound)
                put("dailyLimitMinutes", p.dailyLimitMinutes)
                put("weeklyLimitMinutes", p.weeklyLimitMinutes)
                put("createdAt", p.createdAt)
            })
        }
        root.put("projects", pj)

        val sj = JSONArray()
        Repo.allSessions().forEach { s ->
            sj.put(JSONObject().apply {
                put("projectId", s.projectId)
                put("startAt", s.startAt)
                put("endAt", s.endAt)
                put("plannedMinutes", s.plannedMinutes)
                put("chimeFired", s.chimeFired)
            })
        }
        root.put("sessions", sj)

        ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        }
        return "已导出 ${pj.length()} 个项目、${sj.length()} 条记录"
    }

    fun exportCsv(ctx: Context, uri: Uri): String {
        val sb = StringBuilder()
        sb.append("项目,开始时间,结束时间,时长(分钟),计划(分钟),到点提示\n")
        val nameById = Repo.projects().associate { it.id to it.name }
        val rows = Repo.allSessions()
        rows.forEach { s ->
            val name = nameById[s.projectId] ?: ("#" + s.projectId)
            sb.append(escape(name)).append(',')
                .append(escape(Format.fullDate(s.startAt) + " " + Format.time(s.startAt))).append(',')
                .append(escape(Format.fullDate(s.endAt) + " " + Format.time(s.endAt))).append(',')
                .append(String.format("%.1f", s.durationMs / 60000.0)).append(',')
                .append(s.plannedMinutes).append(',')
                .append(if (s.chimeFired) "是" else "否").append('\n')
        }
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            // 加 BOM，Excel 打开中文不乱码
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        return "已导出 ${rows.size} 条记录（CSV）"
    }

    private fun escape(s: String): String =
        if (s.contains(',') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun importJson(ctx: Context, uri: Uri): String {
        val text = ctx.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return "读取失败"
        val root = try {
            JSONObject(text)
        } catch (t: Throwable) {
            return "文件不是合法的 JSON"
        }
        if (root.optInt("version", 0) > VERSION) return "这个备份来自更新的版本，先升级 App"

        val existingProjects = Repo.projects()
        val idMap = HashMap<Long, Long>()   // 旧 id -> 新 id
        var newProjects = 0

        val pj = root.optJSONArray("projects") ?: JSONArray()
        for (i in 0 until pj.length()) {
            val o = pj.getJSONObject(i)
            val oldId = o.optLong("id", 0L)
            val name = o.optString("name", "")
            if (name.isEmpty()) continue
            val same = existingProjects.firstOrNull { it.name == name }
            if (same != null) {
                idMap[oldId] = same.id
            } else {
                val np = Project(
                    name = name,
                    color = o.optInt("color", 0),
                    negative = o.optBoolean("negative", false),
                    defaultMinutes = o.optInt("defaultMinutes", 25),
                    sound = o.optString("sound", "bell"),
                    dailyLimitMinutes = o.optInt("dailyLimitMinutes", 0),
                    weeklyLimitMinutes = o.optInt("weeklyLimitMinutes", 0),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
                val newId = Repo.saveProject(np)
                idMap[oldId] = newId
                newProjects++
            }
        }

        val existing = Repo.allSessions().map { it.projectId to it.startAt }.toHashSet()
        var newSessions = 0
        val sj = root.optJSONArray("sessions") ?: JSONArray()
        for (i in 0 until sj.length()) {
            val o = sj.getJSONObject(i)
            val oldPid = o.optLong("projectId", 0L)
            val pid = idMap[oldPid] ?: continue
            val startAt = o.optLong("startAt", 0L)
            if (startAt <= 0L) continue
            if (existing.contains(pid to startAt)) continue
            Repo.addSession(
                FocusSession(
                    projectId = pid,
                    startAt = startAt,
                    endAt = o.optLong("endAt", startAt),
                    plannedMinutes = o.optInt("plannedMinutes", 0),
                    chimeFired = o.optBoolean("chimeFired", false)
                )
            )
            newSessions++
        }
        return "导入完成：新增 $newProjects 个项目、$newSessions 条记录（同名项目已合并）"
    }
}
