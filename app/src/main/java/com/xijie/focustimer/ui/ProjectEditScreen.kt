package com.xijie.focustimer.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xijie.focustimer.data.Project
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.timer.ChimePlayer

private val PALETTE = listOf(
    0xFF3F51B5.toInt(), 0xFF00897B.toInt(), 0xFFEF6C00.toInt(),
    0xFFC62828.toInt(), 0xFF6A1B9A.toInt(), 0xFF546E7A.toInt()
)

@Composable
fun ProjectEditScreen(
    projectId: Long,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onDeleted: () -> Unit
) {
    val ctx = LocalContext.current
    val existing = remember(projectId) { if (projectId > 0L) Repo.project(projectId) else null }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: PALETTE[0]) }
    var negative by remember { mutableStateOf(existing?.negative ?: false) }
    var defaultMinutes by remember { mutableStateOf((existing?.defaultMinutes ?: 25).toString()) }
    var sound by remember { mutableStateOf(existing?.sound ?: "bell") }
    var soundUri by remember { mutableStateOf(existing?.soundUri ?: "") }
    var dailyLimit by remember { mutableStateOf((existing?.dailyLimitMinutes ?: 0).toString()) }
    var weeklyLimit by remember { mutableStateOf((existing?.weeklyLimitMinutes ?: 0).toString()) }

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (t: Throwable) {
                // 有些文件提供方不支持持久授权，本次仍可用
            }
            soundUri = uri.toString()
            sound = "custom"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (existing == null) "新建项目" else "编辑项目",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("取消") }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { Text("项目名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Text("颜色", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.padding(top = 6.dp)) {
            PALETTE.forEach { c ->
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(if (c == color) 34.dp else 28.dp)
                        .background(Color(c), CircleShape)
                        .clickable { color = c }
                )
            }
        }

        OutlinedTextField(
            value = defaultMinutes,
            onValueChange = { v -> defaultMinutes = v.filter { it.isDigit() }.take(4) },
            label = { Text("默认时长（分钟）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
        Text(
            "桌面小部件点一下会按你上次在 App 里设的时长计时；没设过才用这个默认值。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888),
            modifier = Modifier.padding(top = 4.dp)
        )

        Text("到点提示音", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.padding(top = 6.dp)) {
            SoundButton("叮", sound == "ding") { sound = "ding" }
            SoundButton("钟", sound == "bell") { sound = "bell" }
            SoundButton("钵", sound == "bowl") { sound = "bowl" }
        }
        Row(modifier = Modifier.padding(top = 6.dp)) {
            SoundButton("木鱼", sound == "muyu") { sound = "muyu" }
            SoundButton("磬", sound == "qing") { sound = "qing" }
            SoundButton("哔", sound == "beep") { sound = "beep" }
        }
        Row(modifier = Modifier.padding(top = 6.dp)) {
            SoundButton("自定义", sound == "custom") { sound = "custom" }
            SoundButton("关闭", sound == "none") { sound = "none" }
        }

        if (sound == "custom") {
            OutlinedButton(
                onClick = { pickAudio.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(if (soundUri.isEmpty()) "选择音频文件" else "已选：" + fileName(soundUri) + "（点击重选）") }
        }

        OutlinedButton(
            onClick = { ChimePlayer.play(ctx, sound, soundUri) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("试听这个提示音") }

        Text(
            "只响一声，响完继续计时不停止。走闹钟通道，静音模式下也会响；音量取“闹钟音量”。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888),
            modifier = Modifier.padding(top = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("这是负面项目", style = MaterialTheme.typography.bodyLarge)
                Text("负面项目超出上限会提醒你", style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
            }
            Switch(checked = negative, onCheckedChange = { negative = it })
        }

        if (negative) {
            OutlinedTextField(
                value = dailyLimit,
                onValueChange = { v -> dailyLimit = v.filter { it.isDigit() }.take(4) },
                label = { Text("单日上限（分钟，0＝不提醒）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            OutlinedTextField(
                value = weeklyLimit,
                onValueChange = { v -> weeklyLimit = v.filter { it.isDigit() }.take(5) },
                label = { Text("单周上限（分钟，0＝不提醒）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }

        Button(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) {
                    val p = Project(
                        id = existing?.id ?: 0L,
                        name = trimmed,
                        color = color,
                        negative = negative,
                        defaultMinutes = (defaultMinutes.toIntOrNull() ?: 25).coerceAtLeast(1),
                        sound = sound,
                        soundUri = if (sound == "custom") soundUri else "",
                        dailyLimitMinutes = dailyLimit.toIntOrNull() ?: 0,
                        weeklyLimitMinutes = weeklyLimit.toIntOrNull() ?: 0,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        lastMinutes = existing?.lastMinutes ?: 0
                    )
                    Repo.saveProject(p)
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) { Text("保存") }

        if (existing != null) {
            OutlinedButton(
                onClick = {
                    Repo.deleteProject(existing.id)
                    onDeleted()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text("删除这个项目（含它的记录）") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun fileName(uri: String): String {
    val cut = uri.lastIndexOf('/')
    val raw = if (cut >= 0) uri.substring(cut + 1) else uri
    return try {
        java.net.URLDecoder.decode(raw, "UTF-8").take(24)
    } catch (t: Throwable) {
        raw.take(24)
    }
}

@Composable
private fun SoundButton(text: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Button(onClick = onClick, modifier = Modifier.padding(end = 8.dp)) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.padding(end = 8.dp)) { Text(text) }
    }
}
