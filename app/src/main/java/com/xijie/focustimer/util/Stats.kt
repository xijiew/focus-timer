package com.xijie.focustimer.util

import com.xijie.focustimer.data.FocusSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object Stats {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun todayStart(): Long =
        LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    fun weekStart(): Long =
        LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthStart(): Long =
        LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun yearStart(): Long =
        LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun sumMs(list: List<FocusSession>): Long = list.sumOf { it.durationMs }

    fun byDate(list: List<FocusSession>): Map<LocalDate, Long> {
        val map = HashMap<LocalDate, Long>()
        list.forEach { s ->
            val d = Instant.ofEpochMilli(s.startAt).atZone(zone).toLocalDate()
            map[d] = (map[d] ?: 0L) + s.durationMs
        }
        return map
    }

    data class Bar(val label: String, val millis: Long)

    fun weekBars(list: List<FocusSession>): List<Bar> {
        val start = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val map = byDate(list)
        val names = arrayOf("一", "二", "三", "四", "五", "六", "日")
        return (0..6).map { i ->
            val d = start.plusDays(i.toLong())
            Bar(names[i], map[d] ?: 0L)
        }
    }

    fun monthBars(list: List<FocusSession>): List<Bar> {
        val today = LocalDate.now(zone)
        val ym = YearMonth.from(today)
        val map = byDate(list)
        return (1..ym.lengthOfMonth()).map { day ->
            val d = ym.atDay(day)
            Bar(if (day % 5 == 0 || day == 1) day.toString() else "", map[d] ?: 0L)
        }
    }

    fun yearBars(list: List<FocusSession>): List<Bar> {
        val year = LocalDate.now(zone).year
        val byMonth = LongArray(12)
        list.forEach { s ->
            val d = Instant.ofEpochMilli(s.startAt).atZone(zone).toLocalDate()
            if (d.year == year) byMonth[d.monthValue - 1] += s.durationMs
        }
        return (1..12).map { m -> Bar("${m}月", byMonth[m - 1]) }
    }
}
