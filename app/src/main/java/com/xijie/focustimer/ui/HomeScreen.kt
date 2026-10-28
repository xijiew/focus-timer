package com.xijie.focustimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xijie.focustimer.data.Project
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.timer.TimerService
import com.xijie.focustimer.util.Format
import com.xijie.focustimer.util.Stats

@Composable
fun HomeScreen(
    version: Int,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit
) {
    val ctx = LocalContext.current
    val tick = rememberTicker(1000L)
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var posWeek by remember { mutableLongStateOf(0L) }
    var negWeek by remember { mutableLongStateOf(0L) }
    var refresh by remember { mutableIntStateOf(0) }
    var startTarget by remember { mutableStateOf<Project?>(null) }
    var menuTarget by remember { mutableStateOf<Project?>(null) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(version, refresh, tick / 30) {
        val ps = Repo.projects()
        projects = ps
        val now = System.currentTimeMillis()
        val ws = Stats.weekStart()
        var pos = 0L
        var neg = 0L
        ps.forEach { p ->
            val s = Stats.sumMs(Repo.sessionsBetween(p.id, ws, now + 1))
            if (p.negative) neg += s else pos += s
        }
        posWeek = pos
        negWeek = neg
    }

    val running = Repo.running()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("专注计时", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAbout) { Text("关于") }
                TextButton(onClick = onBackup) { Text("自检 / 备份") }
            }
        }

        if (running != null) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("正在专注 · " + running.projectName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        Format.clock(Repo.elapsedMs(running)) + "   /   计划 " + running.plannedMinutes + " 分钟",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (running.paused) {
                        Text("已暂停，时间已定格", style = MaterialTheme.typography.bodySmall)
                    } else if (running.chimeFired) {
                        Text("已响过提示，正在继续计时", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = {
                            if (running.paused) TimerService.resume(ctx) else TimerService.pause(ctx)
                        }) { Text(if (running.paused) "继续" else "暂停") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { TimerService.stop(ctx) }) { Text("停止并保存") }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("本周正面", style = MaterialTheme.typography.labelMedium)
                    Text(
                        Format.duration(posWeek),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("本周负面", style = MaterialTheme.typography.labelMedium)
                    Text(Format.duration(negWeek), style = MaterialTheme.typography.titleMedium, color = Color(0xFFC62828))
                }
            }
        }

        Text(
            "点项目开始计时 · 长按可编辑或删除",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF999999),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(projects) { p ->
                ProjectRow(
                    p = p,
                    running = running != null && running.projectId == p.id,
                    onClick = { onOpen(p.id) },
                    onStart = { startTarget = p },
                    onStop = { TimerService.stop(ctx) },
                    onLongClick = { menuTarget = p }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) { Text("＋ 新建项目") }
    }

    val target = startTarget
    if (target != null) {
        StartDialog(
            project = target,
            onDismiss = { startTarget = null },
            onStart = { minutes ->
                // 不再先 stop 再 start：服务内部 begin() 会自动结算上一段；
                // 连打 stop→start 会让系统报“前台服务未及时 startForeground”把进程杀掉（就是自动回桌面的原因）
                TimerService.start(ctx, target.id, minutes)
                startTarget = null
                refresh++
            }
        )
    }

    val menu = menuTarget
    if (menu != null) {
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            title = { Text(menu.name) },
            text = { Text("要做什么？") },
            confirmButton = {
                TextButton(onClick = {
                    menuTarget = null
                    onEdit(menu.id)
                }) { Text("编辑项目") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        menuTarget = null
                        deleteTarget = menu
                    }) { Text("删除", color = Color(0xFFC62828)) }
                    TextButton(onClick = { menuTarget = null }) { Text("取消") }
                }
            }
        )
    }

    val del = deleteTarget
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「" + del.name + "」？") },
            text = { Text("这个项目的所有计时记录也会一起删掉，删了找不回来。") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.deleteProject(del.id)
                    deleteTarget = null
                    refresh++
                    // 让桌面上绑了这个项目的小部件立刻变成“项目已删除，点一下重新选择”
                    com.xijie.focustimer.widget.TimerWidgetProvider.notifyChanged(ctx)
                }) { Text("删除", color = Color(0xFFC62828)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun ProjectRow(
    p: Project,
    running: Boolean,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(p.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(if (p.color == 0) MaterialTheme.colorScheme.primary else Color(p.color), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    (if (p.negative) "负面" else "正面") + " · 上次 " + p.widgetMinutes + " 分钟 · " + p.soundLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF777777)
                )
            }
            if (running) {
                // 计时中：主按钮变成“停止”，不能再点出时长输入框重复开始
                TextButton(onClick = onStop) { Text("停止", color = Color(0xFFC62828)) }
            } else {
                TextButton(onClick = onStart) { Text("开始") }
            }
        }
    }
}

@Composable
private fun StartDialog(project: Project, onDismiss: () -> Unit, onStart: (Int) -> Unit) {
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
                    "到 " + minutes + " 分钟时只响一声（" + project.soundLabel + "），然后继续计时，直到你手动停止。",
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
