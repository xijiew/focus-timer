package com.xijie.focustimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xijie.focustimer.data.Repo
import com.xijie.focustimer.util.Format
import com.xijie.focustimer.util.Stats
import kotlinx.coroutines.delay

/** 每秒自增的计数器，用来驱动"运行中"显示的刷新 */
@Composable
fun rememberTicker(periodMs: Long = 1000L): Long {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(periodMs)
            t += 1L
        }
    }
    return t
}

/**
 * 柱状图。
 * - showValues：在每根柱子上方标出该柱的时长（周视图用）
 * - selectedIndex：当前选中的柱子，其余柱子变淡
 * - onBarClick：点柱子的回调（传入下标）
 */
@Composable
fun BarChart(
    bars: List<Stats.Bar>,
    color: Color,
    modifier: Modifier = Modifier,
    showValues: Boolean = false,
    selectedIndex: Int? = null,
    onBarClick: ((Int) -> Unit)? = null
) {
    if (bars.isEmpty()) return
    val maxV = (bars.maxOfOrNull { it.millis } ?: 0L).toFloat()
    val safeMax = if (maxV <= 0f) 1f else maxV
    val measurer = rememberTextMeasurer()

    val tapMod = if (onBarClick == null) Modifier else Modifier.pointerInput(bars.size) {
        detectTapGestures { off ->
            val n = bars.size
            val gap = size.width / (n * 4f)
            val bw = ((size.width - gap * (n - 1).coerceAtLeast(1)) / n).coerceAtLeast(1f)
            val i = (off.x / (bw + gap)).toInt().coerceIn(0, n - 1)
            onBarClick(i)
        }
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showValues) 138.dp else 120.dp)
                .then(tapMod)
        ) {
            val labelH = 16.dp.toPx()
            val valueH = if (showValues) 16.dp.toPx() else 0f
            val usable = (size.height - labelH - valueH).coerceAtLeast(1f)
            val n = bars.size
            val gap = size.width / (n * 4f)
            val bw = ((size.width - gap * (n - 1).coerceAtLeast(1)) / n).coerceAtLeast(1f)
            bars.forEachIndexed { i, b ->
                val h = if (b.millis > 0L) {
                    (usable * (b.millis.toFloat() / safeMax)).coerceAtLeast(2.dp.toPx())
                } else {
                    1.dp.toPx()
                }
                val x = i * (bw + gap)
                val top = valueH + usable - h
                val dim = selectedIndex != null && selectedIndex != i
                val base = if (b.millis > 0L) color else color.copy(alpha = 0.15f)
                drawRoundRect(
                    color = if (dim) base.copy(alpha = base.alpha * 0.35f) else base,
                    topLeft = Offset(x, top),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                if (showValues && b.millis > 0L) {
                    val txt = Format.durationShort(b.millis)
                    if (txt.isNotEmpty()) {
                        val laid = measurer.measure(
                            text = txt,
                            style = TextStyle(
                                fontSize = 9.sp,
                                color = if (selectedIndex == i) color else Color(0xFF888888)
                            )
                        )
                        drawText(
                            textLayoutResult = laid,
                            topLeft = Offset(
                                x + (bw - laid.size.width) / 2f,
                                (top - laid.size.height - 1.dp.toPx()).coerceAtLeast(0f)
                            )
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            bars.forEachIndexed { i, b ->
                Text(
                    b.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedIndex == i) color else Color(0xFF888888)
                )
            }
        }
    }
}

/**
 * 停止按钮的统一把关（2026-10-29 第二次修订，阈值都读 Repo 里的常量）：
 * - 1 分 30 秒以下：直接停，服务端丢弃（不记录、不提示）
 * - 1 分 30 秒 ~ 3 分钟：弹窗「提前放弃专注？本次专注不足 3 分钟，记录将不会被保存。」→ 取消＝继续计时，放弃＝停止且不保存
 * - 3 分钟以上：直接停并保存
 *
 * 用法：`val stopGate = rememberStopGate(onSave = { TimerService.stop(ctx) }, onDiscard = { TimerService.stopDiscard(ctx) })`
 * 然后按钮写 `Button(onClick = { stopGate() })`。
 */
@Composable
fun rememberStopGate(onSave: () -> Unit, onDiscard: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    if (asking) {
        // 样式对齐滴答清单：大圆角白卡、标题加粗、正文深色宽松、右下角纯文字双按钮
        AlertDialog(
            onDismissRequest = { asking = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "提前放弃专注？",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            },
            text = {
                Text(
                    "本次专注不足 3 分钟，记录将不会被保存。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    onDiscard()
                }) {
                    Text(
                        "放弃",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { asking = false },
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
    return {
        val r = Repo.running()
        val ms = if (r == null) 0L else Repo.elapsedMs(r)
        if (ms < Repo.CONFIRM_UNDER_MS && ms >= Repo.DISCARD_UNDER_MS) {
            asking = true
        } else {
            onSave()
        }
    }
}
