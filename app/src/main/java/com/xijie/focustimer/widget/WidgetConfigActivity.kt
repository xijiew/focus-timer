package com.xijie.focustimer.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xijie.focustimer.data.Project
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.ui.AppTheme

/** 放置小部件 / 点小部件上的“设置”时打开：选项目 + 选时长来源 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val projects = Repo.projects()
        val curPid = TimerWidgetProvider.projectIdOf(this, widgetId)
        val curMode = TimerWidgetProvider.timeModeOf(this, widgetId)
        val curMin = TimerWidgetProvider.customMinutesOf(this, widgetId)

        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        ConfigScreen(
                            projects = projects,
                            initProjectId = curPid,
                            initMode = curMode,
                            initMinutes = curMin,
                            onSave = { pid, mode, minutes -> save(pid, mode, minutes) },
                            onCancel = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun save(pid: Long, mode: String, minutes: Int) {
        TimerWidgetProvider.setConfig(this, widgetId, pid, mode, minutes)
        TimerWidgetProvider.updateAll(this)
        val name = Repo.project(pid)?.name ?: ""
        Toast.makeText(this, "已绑定项目：" + name, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun ConfigScreen(
    projects: List<Project>,
    initProjectId: Long,
    initMode: String,
    initMinutes: Int,
    onSave: (Long, String, Int) -> Unit,
    onCancel: () -> Unit
) {
    var projectId by remember { mutableLongStateOf(initProjectId) }
    var mode by remember { mutableStateOf(initMode) }
    var minutes by remember { mutableIntStateOf(initMinutes) }
    var minutesText by remember { mutableStateOf(if (initMinutes > 0) initMinutes.toString() else "") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("配置桌面小部件", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("取消") }
        }
        Text(
            "先选项目，再选点小部件时用多少分钟。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888),
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )

        if (projects.isEmpty()) {
            Text("还没有项目，请先在 App 里新建一个项目。")
        } else {
            Text("选择项目", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(projects) { p ->
                    val selected = p.id == projectId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { projectId = p.id }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (selected) 14.dp else 10.dp)
                                .background(
                                    if (p.color == 0) MaterialTheme.colorScheme.primary else Color(p.color),
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                (if (p.negative) "负面" else "正面") +
                                    " · 默认 " + p.defaultMinutes + " 分 · 上次 " + p.widgetMinutes + " 分 · " + p.soundLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF888888)
                            )
                        }
                        if (selected) Text("已选", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Text("点小部件时用多少分钟", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        Row(modifier = Modifier.padding(top = 6.dp)) {
            ModeButton("上次 App 设的", mode == TimerWidgetProvider.MODE_LAST) { mode = TimerWidgetProvider.MODE_LAST }
            ModeButton("项目默认", mode == TimerWidgetProvider.MODE_DEFAULT) { mode = TimerWidgetProvider.MODE_DEFAULT }
            ModeButton("自己定", mode == TimerWidgetProvider.MODE_CUSTOM) { mode = TimerWidgetProvider.MODE_CUSTOM }
        }
        if (mode == TimerWidgetProvider.MODE_CUSTOM) {
            OutlinedTextField(
                value = minutesText,
                onValueChange = { v ->
                    minutesText = v.filter { it.isDigit() }.take(4)
                    minutes = minutesText.toIntOrNull() ?: 0
                },
                label = { Text("分钟数") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        Button(
            onClick = { if (projectId > 0L) onSave(projectId, mode, minutes) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) { Text("保存") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ModeButton(text: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Button(onClick = onClick, modifier = Modifier.padding(end = 8.dp)) { Text(text, style = MaterialTheme.typography.labelMedium) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.padding(end = 8.dp)) { Text(text, style = MaterialTheme.typography.labelMedium) }
    }
}
