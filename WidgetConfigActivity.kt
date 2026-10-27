package com.xijie.focustimer.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xijie.focustimer.data.Project
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.ui.AppTheme

/** 放置小部件时先选项目，否则不知道哪个项目在计时 */
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
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        Picker(projects) { p -> choose(p) }
                    }
                }
            }
        }
    }

    private fun choose(p: Project) {
        val name = p.name
        TimerWidgetProvider.bindProject(this, widgetId, p.id)
        TimerWidgetProvider.updateAll(this)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, result)
        android.widget.Toast.makeText(this, "已绑定项目：$name", android.widget.Toast.LENGTH_SHORT).show()
        finish()
    }

    @Composable
    private fun Picker(projects: List<Project>, onPick: (Project) -> Unit) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("给这个小部件选一个项目", style = MaterialTheme.typography.titleMedium)
            Text(
                "点小部件开始计时，再点一下停止；时长用这个项目上次设定的分钟数。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            if (projects.isEmpty()) {
                Text("还没有项目，请先打开 App 新建一个项目。")
            } else {
                LazyColumn {
                    items(projects) { p ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(p) }
                                .padding(vertical = 14.dp)
                        ) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                (if (p.negative) "负面" else "正面") + " · 默认 " + p.defaultMinutes + " 分钟 · " + p.soundLabel,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
