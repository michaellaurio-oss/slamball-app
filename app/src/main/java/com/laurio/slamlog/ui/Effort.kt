package com.laurio.slamlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Words for each point on the 1–10 effort scale. */
fun effortLabel(v: Int): String = when (v) {
    1, 2 -> "Easy"
    3, 4 -> "Moderate"
    5, 6 -> "Somewhat hard"
    7, 8 -> "Hard"
    9 -> "Very hard"
    else -> "All-out"
}

/** Green (easy) through amber to red (all-out). */
fun effortColor(v: Int): Color {
    val f = ((v.coerceIn(1, 10) - 1) / 9f)
    val green = Color(0xFF34D399)
    val amber = Color(0xFFFBBF24)
    val red = Color(0xFFEF4444)
    return if (f < 0.5f) lerp(green, amber, f * 2f) else lerp(amber, red, (f - 0.5f) * 2f)
}

/** Tap 1–10: how hard the workout felt. */
@Composable
fun EffortScale(selected: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (v in 1..10) {
                val c = effortColor(v)
                val on = selected == v
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (on) 52.dp else 42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) c else c.copy(alpha = 0.22f))
                        .clickable { onSelect(v) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$v",
                        fontWeight = FontWeight.Bold,
                        color = if (on) Color(0xFF111111) else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Easy", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected != null) {
                Text("$selected · ${effortLabel(selected)}", fontWeight = FontWeight.Bold, color = effortColor(selected))
            }
            Text("All-out", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
