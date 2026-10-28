package com.xijie.focustimer.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 版本更新日志（新版本在最上面） */
private val CHANGELOG: List<Pair<String, String>> = listOf(
    "2.8" to "· 2×1 小部件第一行显示项目名，下面才是时长与按钮",
    "2.7" to "· 2×1 小部件计时中只保留「停止」胶囊，时长显示更宽松\n" +
        "· 2×1 运行态胶囊加大、字号回到 20sp",
    "2.6" to "· 新增独立的 2×2 小部件，桌面小部件面板可直接选，不用再手动拉大\n" +
        "· 小部件只有胶囊按钮有功能，点时长/项目名等空白处直接打开 App\n" +
        "· 详情页新增总统计：累计打卡天数、累计时长、平均每天\n" +
        "· 通知第二行改为实时计时时长，更大更清楚\n" +
        "· 新增「关于」页（本页）\n" +
        "· 修复换版本安装提示签名不一致（改用固定签名）",
    "2.5" to "· 修复每次装新版都提示签名不对、必须卸载重装\n" +
        "· 关掉小部件每 30 分钟的系统定时唤醒（空闲不再被唤醒）\n" +
        "· 计时中息屏不再每秒刷新小部件，改为 30 秒一次",
    "2.4" to "· 空闲与计时改用两套布局，彻底修好「深色模式下计时只剩暂停」「切回浅色后空闲多出停止」\n" +
        "· 计时数字改小（40sp → 32sp）\n" +
        "· 胶囊改矮（46dp → 40dp）、图标改大（16dp → 20dp）",
    "2.3" to "· 停止胶囊在深色模式下看得清了\n" +
        "· 状态切换后多刷一次，避免按钮状态错位",
    "2.2" to "· 计时数字改用普通文本由服务刷新（修小米桌面改不动字号/颜色）\n" +
        "· 胶囊只留图标，「计划 X 分」移到标题行",
    "2.1" to "· 真暂停/继续：暂停定格、取消到点闹钟，继续按剩余时间重排\n" +
        "· 小部件计时态双胶囊，通知栏与 App 内同步暂停按钮",
    "2.0" to "· 按钮改矢量图标（修播放符变橙色方块）\n" +
        "· 深色模式跟随系统（黑卡、浅色数字）",
    "1.9" to "· 修小部件「载入窗口小部件时出现问题」（去掉非白名单控件）\n" +
        "· 编译产物文件名自动带版本号",
    "1.8" to "· 修开始新计时后 App 自动退回桌面\n" +
        "· 首页计时中按钮变红色「停止」",
    "1.7" to "· 2×1 与 2×2 合并成一套布局，运行时切换字号与显隐",
    "1.5" to "· 首页长按项目可编辑/删除\n" +
        "· 统计页加连续打卡天数、本周与上周对比\n" +
        "· 内置音效增加到 8 种（新增木鱼/磬/哔）\n" +
        "· 小部件项目被删除后可点一下重新选择",
    "1.3" to "· 新增通知自检页、记住上次设置的时长",
    "1.2" to "· 调整通知重要性与渠道，锁屏可显示",
    "1.1" to "· 修边到边显示、钵声、锁屏与到点响铃",
    "1.0" to "· 首个可用版本：项目、后台计时、到点响一声并继续计时、周月年统计、桌面小部件"
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        } catch (t: Throwable) {
            null
        }
    }
    val verName = info?.versionName ?: "?"
    @Suppress("DEPRECATION")
    val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info?.longVersionCode ?: 0L
    } else {
        (info?.versionCode ?: 0).toLong()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
        }
        Text("关于", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "专注计时 · 自用版",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888888),
            modifier = Modifier.padding(top = 2.dp)
        )

        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("当前版本", style = MaterialTheme.typography.labelMedium)
                Text(
                    "v" + verName + "（versionCode " + verCode + "）",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "不联网、不读位置、不读写手机存储；只用一个前台服务在计时，到点响一声后继续计时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Text(
            "更新日志",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(CHANGELOG) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            "v" + item.first,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            item.second,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
