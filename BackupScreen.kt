package com.xijie.focustimer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
        }
        Text("导出 / 导入", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "换手机时用：先把 JSON 导出到文件，新手机上装好 App 再导入。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888),
            modifier = Modifier.padding(top = 4.dp)
        )

        Button(
            onClick = { createJson.launch("focus-timer-backup.json") },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
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

        Spacer(Modifier.height(24.dp))
        Text(
            "说明：导入时同名项目会合并，重复记录（同项目同开始时间）不会重复写入。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888)
        )
    }
}
