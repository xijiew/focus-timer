package com.xijie.focustimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

@Composable
fun BarChart(bars: List<Stats.Bar>, color: Color, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val maxV = (bars.maxOfOrNull { it.millis } ?: 0L).toFloat()
    val safeMax = if (maxV <= 0f) 1f else maxV
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val labelH = 16.dp.toPx()
            val usable = (size.height - labelH).coerceAtLeast(1f)
            val n = bars.size
            val gap = size.width / (n * 4f)
            val bw = ((size.width - gap * (n - 1).coerceAtLeast(1)) / n).coerceAtLeast(1f)
            bars.forEachIndexed { i, b ->
                val h = if (b.millis > 0L) (usable * (b.millis.toFloat() / safeMax)).coerceAtLeast(2.dp.toPx()) else 1.dp.toPx()
                val x = i * (bw + gap)
                drawRoundRect(
                    color = if (b.millis > 0L) color else color.copy(alpha = 0.15f),
                    topLeft = Offset(x, usable - h),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            bars.forEach { b ->
                Text(b.label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF888888))
            }
        }
    }
}
