package com.laurio.slamlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurio.slamlog.workout.Physiology
import com.laurio.slamlog.workout.mmss

fun zoneColor(zone: Int): Color = Color(Physiology.zoneColors[zone.coerceIn(0, 5)])

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
    )
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(onClick = onMinus, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) {
            Text("−", fontSize = 22.sp)
        }
        Text(
            value,
            Modifier.widthIn(min = 84.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        FilledTonalButton(onClick = onPlus, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) {
            Text("+", fontSize = 22.sp)
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Thin horizontal meter, used for mic loudness. */
@Composable
fun LevelBar(level: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondary) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(level.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color),
        )
    }
}

/** Heart rate over the workout, drawn over colored zone bands. */
@Composable
fun HrChart(samples: List<Int>, maxHr: Int, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .fillMaxWidth()
            .height(170.dp),
    ) {
        val valid = samples.filter { it > 0 }
        if (valid.size < 2 || samples.size < 2) return@Canvas
        val lo = (valid.min() - 10).coerceAtLeast(40).toFloat()
        val hi = maxOf(valid.max() + 10, (maxHr * 0.6f).toInt()).toFloat()
        fun y(v: Float) = size.height * (1f - (v - lo) / (hi - lo))

        for (z in 1..5) {
            val bottom = maxHr * (0.4f + 0.1f * z)
            val top = if (z == 5) hi else maxHr * (0.5f + 0.1f * z)
            val yTop = y(top).coerceIn(0f, size.height)
            val yBottom = y(bottom).coerceIn(0f, size.height)
            if (yBottom > yTop) {
                drawRect(zoneColor(z).copy(alpha = 0.13f), Offset(0f, yTop), Size(size.width, yBottom - yTop))
            }
        }

        val path = Path()
        var penDown = false
        val step = size.width / (samples.size - 1)
        samples.forEachIndexed { i, v ->
            if (v <= 0) {
                penDown = false
            } else {
                val x = i * step
                if (penDown) path.lineTo(x, y(v.toFloat())) else path.moveTo(x, y(v.toFloat()))
                penDown = true
            }
        }
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
    }
}

/** Time spent in each heart rate zone. */
@Composable
fun ZoneBars(zoneSec: List<Int>) {
    val most = (zoneSec.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (z in 5 downTo 1) {
            val sec = zoneSec.getOrElse(z) { 0 }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Physiology.zoneNames[z], Modifier.width(112.dp), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f)) {
                    LevelBar(sec.toFloat() / most, color = zoneColor(z))
                }
                Text(mmss(sec), Modifier.width(52.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
