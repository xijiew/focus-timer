package com.xijie.focustimer.ui

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.xijie.focustimer.notify.Notifier
import com.xijie.focustimer.util.Backup

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var message by remember { mutableStateOf("") }

    val createJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            message = try {
                Backup.exportJson(ctx, uri)
            } catch (t: Throwable) {
                "导出失败：" + t.message
            }
        }
    }
    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            message = try {
                Backup.exportCsv(ctx, uri)
            } catch (t: Throwable) {
                "导出失败：" + t.message
            }
        }
    }
    val openJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            message = try {
                Backup.importJson(ctx, uri)
            } catch (t: Throwable) {
                "导入失败：" + t.message
            }
        }
    }

    val nm = ctx.getSystemService(NotificationManager::class.java)
    val enabled = nm?.areNotificationsEnabled() ?: false
    val ch = nm?.getNotificationChannel(Notifier.CH_ONGOING)
    val importanceText = when (ch?.importance) {
        NotificationManager.IMPORTANCE_HIGH -> "高"
        NotificationManager.IMPORTANCE_DEFAULT -> "普通（推荐）"
        NotificationManager.IMPORTANCE_LOW -> "低（会被静默，锁屏可能不显示）"
        NotificationManager.IMPORTANCE_MIN -> "最低（不显示）"
        null -> "渠道未创建"
        else -> "其他"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
        }
        Text("通知自检 / 导出导入", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("通知自检", style = MaterialTheme.typography.titleSmall)
                Text(
                    "通知总开关：" + (if (enabled) "已开启" else "已关闭"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "计时通知渠道重要性：" + importanceText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "小米/HyperOS 的正确设置（实测有效）：设置 → 通知与控制中心 → 应用通知 → 专注计时 → " +
                        "通知类别 → 计时中 → 锁定屏幕 → 选“显示通知及其内容”。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(top = 6.dp)
                )
                OutlinedButton(
                    onClick = {
                        try {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (t: Throwable) {
                            message = "打不开系统设置：" + t.message
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) { Text("打开本应用的通知设置") }
                OutlinedButton(
                    onClick = {
                        try {
                            ctx.startActivity(
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CH_ONGOING)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (t: Throwable) {
                            message = "打不开渠道设置：" + t.message
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("打开“计时中”这个渠道的设置") }
                OutlinedButton(
                    onClick = { Notifier.testOngoing(ctx) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("发一条测试通知（常驻形态，用于验证锁屏）") }
                OutlinedButton(
                    onClick = { Notifier.cancelTest(ctx) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("清掉测试通知") }
            }
        }

        Text("导出 / 导入", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 22.dp))
        Text(
            "换手机时用：先导出 JSON，新手机装好 App 再导入。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888),
            modifier = Modifier.padding(top = 4.dp)
        )

        Button(
            onClick = { createJson.launch("focus-timer-backup.json") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("导出全量备份（JSON）") }

        OutlinedButton(
            onClick = { createCsv.launch("focus-timer-sessions.csv") },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) { Text("导出明细表（CSV，Excel 可看）") }

        OutlinedButton(
            onClick = { openJson.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) { Text("从 JSON 备份导入") }

        if (message.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text(
            "导入时同名项目会合并，重复记录（同项目同开始时间）不会重复写入。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888),
            modifier = Modifier.padding(top = 16.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}
