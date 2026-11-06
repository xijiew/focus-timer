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

    /** 某个相对周的周一 00:00（offsetWeeks=0 本周，1 上周） */
    fun weekStartOf(offsetWeeks: Int): Long =
        LocalDate.now(zone)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(offsetWeeks.toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** 某个相对周的区间 [start, end)：offsetWeeks=0 本周，1 上周 */
    fun weekRange(offsetWeeks: Int): Pair<Long, Long> =
        weekStartOf(offsetWeeks) to weekStartOf(offsetWeeks - 1)

    /** 某个相对月的区间 [start, end)：offsetMonths=0 本月，1 上个月 */
    fun monthStartOf(offsetMonths: Int): Long =
        YearMonth.from(LocalDate.now(zone)).minusMonths(offsetMonths.toLong())
            .atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthRange(offsetMonths: Int): Pair<Long, Long> =
        monthStartOf(offsetMonths) to monthStartOf(offsetMonths - 1)

    /** 某个相对年的区间 [start, end)：offsetYears=0 本年，1 去年 */
    fun yearStartOf(offsetYears: Int): Long =
        LocalDate.now(zone).withDayOfYear(1).minusYears(offsetYears.toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun yearRange(offsetYears: Int): Pair<Long, Long> =
        yearStartOf(offsetYears) to yearStartOf(offsetYears - 1)

    /** 连续打卡天数：今天或昨天有记录才算在连着；没有记录返回 0 */
    fun streakDays(sessions: List<FocusSession>): Int {
        val days = byDate(sessions).keys
        if (days.isEmpty()) return 0
        val today = LocalDate.now(zone)
        var d = when {
            days.contains(today) -> today
            days.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var n = 0
        while (days.contains(d)) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun byDate(list: List<FocusSession>): Map<LocalDate, Long> {
        val map = HashMap<LocalDate, Long>()
        list.forEach { s ->
            val d = Instant.ofEpochMilli(s.startAt).atZone(zone).toLocalDate()
            map[d] = (map[d] ?: 0L) + s.durationMs
        }
        return map
    }

    data class Bar(val label: String, val millis: Long, val date: LocalDate? = null)

    fun weekBars(list: List<FocusSession>, offsetWeeks: Int = 0): List<Bar> {
        val start = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(offsetWeeks.toLong())
        val map = byDate(list)
        val names = arrayOf("一", "二", "三", "四", "五", "六", "日")
        return (0..6).map { i ->
            val d = start.plusDays(i.toLong())
            Bar(names[i], map[d] ?: 0L, d)
        }
    }

    fun monthBars(list: List<FocusSession>, offsetMonths: Int = 0): List<Bar> {
        val today = LocalDate.now(zone)
        val ym = YearMonth.from(today).minusMonths(offsetMonths.toLong())
        val map = byDate(list)
        return (1..ym.lengthOfMonth()).map { day ->
            val d = ym.atDay(day)
            Bar(if (day % 5 == 0 || day == 1) day.toString() else "", map[d] ?: 0L, d)
        }
    }

    fun yearBars(list: List<FocusSession>, offsetYears: Int = 0): List<Bar> {
        val year = LocalDate.now(zone).year - offsetYears
        val byMonth = LongArray(12)
        list.forEach { s ->
            val d = Instant.ofEpochMilli(s.startAt).atZone(zone).toLocalDate()
            if (d.year == year) byMonth[d.monthValue - 1] += s.durationMs
        }
        return (1..12).map { m -> Bar("${m}月", byMonth[m - 1]) }
    }

    fun dateOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

    /** 某月里每一天的合计（只保留有记录的日期，按日期升序）——年视图点柱子后的明细用 */
    fun dayTotalsInMonth(list: List<FocusSession>, month: Int, offsetYears: Int = 0): List<Pair<LocalDate, Long>> {
        val year = LocalDate.now(zone).year - offsetYears
        return byDate(list)
            .filter { (d, v) -> d.year == year && d.monthValue == month && v > 0L }
            .entries.sortedBy { it.key }
            .map { it.key to it.value }
    }
}
