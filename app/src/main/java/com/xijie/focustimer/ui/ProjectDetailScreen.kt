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
    var showStart by remember { mutableStateOf(false) }

    LaunchedEffect(projectId, version, tab, tick / 30) {
        project = Repo.project(projectId)
        loaded = true
        val all = Repo.sessionsOf(projectId)
        allCount = all.size
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
    val accent = if (p.color == 0) MaterialTheme.colorScheme.primary else Color(p.color)
    val bars = when (tab) {
        0 -> Stats.weekBars(sessions)
        1 -> Stats.monthBars(sessions)
        else -> Stats.yearBars(sessions)
    }
    val total = Stats.sumMs(sessions)
    val days = when (tab) {
        0 -> 7
        1 -> LocalDate.now().dayOfMonth
        else -> LocalDate.now().dayOfYear
    }
    val avg = if (days <= 0) 0L else total / days
    val periodLabel = when (tab) {
        0 -> "本周"
        1 -> "本月"
        else -> "本年"
    }
    val weekDiff = thisWeek - lastWeek
    val weekDiffPct = if (lastWeek <= 0L) -1 else ((weekDiff * 100.0) / lastWeek).toInt()

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
            if (isRunning) {
                Button(onClick = { TimerService.stop(ctx) }) { Text("停止") }
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
                    Text("日均", style = MaterialTheme.typography.labelMedium)
                    Text(Format.duration(avg), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("次数", style = MaterialTheme.typography.labelMedium)
                    Text("" + sessions.size + " / 共 " + allCount, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        BarChart(bars = bars, color = accent, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))

        Text("明细", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(sessions) { s ->
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
