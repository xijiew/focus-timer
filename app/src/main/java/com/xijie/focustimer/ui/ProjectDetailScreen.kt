package com.xijie.focustimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xijie.focustimer.data.FocusSession
import com.xijie.focustimer.data.Project
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.timer.TimerService
import com.xijie.focustimer.util.Format
import com.xijie.focustimer.util.Stats
import java.time.LocalDate

@Composable
fun ProjectDetailScreen(
    projectId: Long,
    version: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val ctx = LocalContext.current
    val tick = rememberTicker(1000L)
    var project by remember { mutableStateOf<Project?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var sessions by remember { mutableStateOf<List<FocusSession>>(emptyList()) }
    var allCount by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var thisWeek by remember { mutableLongStateOf(0L) }
    var lastWeek by remember { mutableLongStateOf(0L) }
    var checkinDays by remember { mutableIntStateOf(0) }
    var totalAllMs by remember { mutableLongStateOf(0L) }
    var showStart by remember { mutableStateOf(false) }
    // 点柱状图后的筛选下标（null＝不筛选）
    var sel by remember { mutableStateOf<Int?>(null) }

    // 切换周／月／年时清掉筛选
    LaunchedEffect(tab) { sel = null }

    LaunchedEffect(projectId, version, tab, tick / 30) {
        project = Repo.project(projectId)
        loaded = true
        val all = Repo.sessionsOf(projectId)
        allCount = all.size
        checkinDays = Stats.byDate(all).size
        totalAllMs = Stats.sumMs(all)
        streak = Stats.streakDays(all)
        val ws = Stats.weekStart()
        val lws = Stats.weekStartOf(1)
        val now = System.currentTimeMillis() + 1
        thisWeek = Stats.sumMs(all.filter { it.startAt >= ws && it.startAt < now })
        lastWeek = Stats.sumMs(all.filter { it.startAt >= lws && it.startAt < ws })
        val from = when (tab) {
            0 -> ws
            1 -> Stats.monthStart()
            else -> Stats.yearStart()
        }
        sessions = all.filter { it.startAt >= from }
    }

    val p = project
    if (p == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(if (loaded) "这个项目已经被删除了。" else "加载中…", modifier = Modifier.padding(top = 12.dp))
        }
        return
    }

    val running = Repo.running()
    val isRunning = running != null && running.projectId == p.id
    // 停止把关：<2 分直接丢、2~5 分弹窗确认、≥5 分直接存
    val stopGate = rememberStopGate(
        onSave = { TimerService.stop(ctx) },
        onDiscard = { TimerService.stopDiscard(ctx) }
    )
    val accent = if (p.color == 0) MaterialTheme.colorScheme.primary else Color(p.color)
    val bars = when (tab) {
        0 -> Stats.weekBars(sessions)
        1 -> Stats.monthBars(sessions)
        else -> Stats.yearBars(sessions)
    }
    val total = Stats.sumMs(sessions)
    // 日均＝该期间合计 ÷ 该期间「实际有专注的天数」（用户 2026-10-29 定：不用 7／30／365 做分母）
    val activeDays = Stats.byDate(sessions).size
    val avg = if (activeDays <= 0) 0L else total / activeDays
    val periodLabel = when (tab) {
        0 -> "本周"
        1 -> "本月"
        else -> "本年"
    }
    val weekDiff = thisWeek - lastWeek
    val weekDiffPct = if (lastWeek <= 0L) -1 else ((weekDiff * 100.0) / lastWeek).toInt()

    // ---- 点柱状图后的联动筛选：周/月＝看某一天，年＝看某个月（按天合计）----
    val selBar = sel
    val selDate: LocalDate? = if (selBar == null) null else bars.getOrNull(selBar)?.date
    val selMonth: Int? = if (tab == 2 && selBar != null) selBar + 1 else null
    val detailSessions =
        if (selDate != null) sessions.filter { Stats.dateOf(it.startAt) == selDate } else sessions
    val dayTotals = if (selMonth != null) Stats.dayTotalsInMonth(sessions, selMonth) else emptyList()
    val detailTitle = when {
        selDate != null -> "明细 · 只看 " + selDate.monthValue + "月" + selDate.dayOfMonth + "日（再点一次柱子取消）"
        selMonth != null -> "明细 · " + selMonth + "月每天的合计（再点一次柱子取消）"
        else -> "明细"
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("编辑") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(accent, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (isRunning && running != null) {
                TextButton(onClick = {
                    if (running.paused) TimerService.resume(ctx) else TimerService.pause(ctx)
                }) { Text(if (running.paused) "继续" else "暂停") }
                Button(onClick = { stopGate() }) { Text("停止") }
            } else {
                Button(onClick = { showStart = true }) { Text("开始") }
            }
        }
        Text(
            (if (p.negative) "负面项目" else "正面项目") +
                (if (p.negative && (p.dailyLimitMinutes > 0 || p.weeklyLimitMinutes > 0))
                    " · 单日上限 " + p.dailyLimitMinutes + " 分 / 单周上限 " + p.weeklyLimitMinutes + " 分"
                else ""),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF777777)
        )

        if (isRunning && running != null) {
            Text(
                "计时中 " + Format.clock(Repo.elapsedMs(running)) + " / 计划 " + running.plannedMinutes + " 分钟",
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("连续打卡", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (streak > 0) streak.toString() + " 天" else "还没开始",
                        style = MaterialTheme.typography.titleLarge,
                        color = accent
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("本周", style = MaterialTheme.typography.labelMedium)
                    Text(Format.duration(thisWeek), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("上周", style = MaterialTheme.typography.labelMedium)
                    Text(Format.duration(lastWeek), style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            lastWeek <= 0L && thisWeek <= 0L -> "—"
                            lastWeek <= 0L -> "本周新增"
                            weekDiffPct > 0 -> "+" + weekDiffPct + "%"
                            weekDiffPct < 0 -> weekDiffPct.toString() + "%"
                            else -> "持平"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (weekDiff > 0) Color(0xFF2E7D32) else if (weekDiff < 0) Color(0xFFC62828) else Color(0xFF888888)
                    )
                }
            }
        }

        // ---- 总统计（全部时间，不随上面的周/月/年切换）----
        Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text("总统计 · 全部时间", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("累计打卡", style = MaterialTheme.typography.labelMedium)
                        Text(
                            if (checkinDays > 0) checkinDays.toString() + " 天" else "还没开始",
                            style = MaterialTheme.typography.titleLarge,
                            color = accent
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("累计时长", style = MaterialTheme.typography.labelMedium)
                        Text(Format.duration(totalAllMs), style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("平均每天", style = MaterialTheme.typography.labelMedium)
                        Text(
                            Format.duration(if (checkinDays > 0) totalAllMs / checkinDays else 0L),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(top = 10.dp)) {
            TabButton("周", tab == 0) { tab = 0 }
            TabButton("月", tab == 1) { tab = 1 }
            TabButton("年", tab == 2) { tab = 2 }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(periodLabel + "合计", style = MaterialTheme.typography.labelMedium)
                    Text(Format.duration(total), style = MaterialTheme.typography.titleLarge, color = accent)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("日均 · " + activeDays + " 天", style = MaterialTheme.typography.labelMedium)
                    Text(Format.duration(avg), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("次数", style = MaterialTheme.typography.labelMedium)
                    Text("" + sessions.size + " / 共 " + allCount, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        BarChart(
            bars = bars,
            color = accent,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            showValues = tab == 0 || tab == 2,
            selectedIndex = sel,
            onBarClick = { i -> sel = if (sel == i) null else i }
        )

        Text(detailTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (selMonth != null) {
                // 年视图点了某个月：只显示按天合计（不显示具体时段）
                if (dayTotals.isEmpty()) {
                    item {
                        Text(
                            "这个月还没有记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                items(dayTotals) { pair ->
                    val (d, ms) = pair
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            d.monthValue.toString() + "月" + d.dayOfMonth + "日",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(Format.duration(ms), style = MaterialTheme.typography.titleSmall, color = accent)
                    }
                }
            } else {
                if (detailSessions.isEmpty()) {
                    item {
                        Text(
                            "这段时间还没有记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                items(detailSessions) { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                Format.fullDate(s.startAt) + "  " + Format.time(s.startAt) + " - " + Format.time(s.endAt),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "计划 " + s.plannedMinutes + " 分钟" + (if (s.chimeFired) " · 已提示" else " · 未到点"),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF888888)
                            )
                        }
                        Text(Format.duration(s.durationMs), style = MaterialTheme.typography.titleSmall, color = accent)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showStart) {
        StartDialog2(p, onDismiss = { showStart = false }) { minutes ->
            TimerService.start(ctx, p.id, minutes)
            showStart = false
        }
    }
}

@Composable
private fun TabButton(text: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Button(onClick = onClick, modifier = Modifier.padding(end = 8.dp)) { Text(text) }
    } else {
        TextButton(onClick = onClick, modifier = Modifier.padding(end = 8.dp)) { Text(text) }
    }
}

@Composable
private fun StartDialog2(project: Project, onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    var minutes by remember {
        mutableStateOf(
            (if (project.lastMinutes in 1..1440) project.lastMinutes else project.defaultMinutes).toString()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开始专注 · " + project.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(4) },
                    label = { Text("本次时长（分钟）") },
                    singleLine = true
                )
                Text(
                    "到点只响一声（" + project.soundLabel + "），然后继续计时，直到你手动停止。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = minutes.toIntOrNull() ?: 0
                if (m > 0) onStart(m)
            }) { Text("开始") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
