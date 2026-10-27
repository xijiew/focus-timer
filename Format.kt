package com.xijie.focustimer.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Format {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val md = DateTimeFormatter.ofPattern("MM-dd")
    private val ymd = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 00:00 形式（用于运行中的计时） */
    fun clock(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    /** 1小时23分 / 45分 / 30秒 */
    fun duration(ms: Long): String {
        if (ms <= 0) return "0分"
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}小时${m}分"
            h > 0 -> "${h}小时"
            totalMin > 0 -> "${m}分"
            else -> "${ms / 1000}秒"
        }
    }

    fun minutes(ms: Long): Double = ms / 60000.0

    fun time(ts: Long): String = Instant.ofEpochMilli(ts).atZone(zone).format(hm)
    fun date(ts: Long): String = Instant.ofEpochMilli(ts).atZone(zone).format(md)
    fun fullDate(ts: Long): String = Instant.ofEpochMilli(ts).atZone(zone).format(ymd)
}
