package com.laurio.slamlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurio.slamlog.data.Session
import com.laurio.slamlog.workout.mmss
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val dateFmt = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US)

@Composable
fun SummaryScreen(session: Session, maxHr: Int, onRate: (Int) -> Unit, onToggleBaseline: (Boolean) -> Unit, onToggleLock: (Boolean) -> Unit, onDelete: () -> Unit, onDone: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onDone)
    val hasHr = session.avgHr > 0
    val target = session.sets.firstOrNull { it.targetHr > 0 }?.targetHr ?: 0
    val measured = session.setsWithTarget(target)
    val times = measured.mapNotNull { it.timeToTargetSec }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("WORKOUT", fontSize = 26.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(dateFmt.format(Date(session.startMillis)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                (if (session.baseline) "Baseline test · " else "Training · ") + session.shape +
                    if (!session.completed) " · ended early" else "",
                color = if (session.baseline) Sky else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            if (session.baseline && !session.completed) {
                Text("Baseline tests only count when every set is done.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionCard {
            Label(if (session.rpe == null) "How hard was it?" else "How hard it felt")
            EffortScale(session.rpe, onRate)
        }

        if (hasHr && target > 0) {
            SectionCard {
                Label("Last slam → $target bpm")
                if (times.isEmpty()) {
                    Text("Heart rate didn't get down to $target during the rests.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row {
                        Stat("average", mmss(times.average().roundToInt()), Modifier.weight(1f), color = Sky)
                        Stat("fastest", mmss(times.min()), Modifier.weight(1f), color = Mint)
                        Stat("slowest", mmss(times.max()), Modifier.weight(1f))
                    }
                    Text("Reached $target after ${times.size} of ${measured.size} sets.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        session.halves(target)?.let { h ->
            SectionCard {
                Label("First half vs second half")
                HalfTable(h, target)
            }
        }

        SectionCard {
            Row {
                Stat("slams", "${session.totalReps}", Modifier.weight(1f), color = Orange)
                Stat("sets", "${session.sets.size}", Modifier.weight(1f))
                Stat("time", mmss(session.durationSec), Modifier.weight(1f))
            }
            Row {
                Stat("ball", "${session.ballWeightLbs} lb", Modifier.weight(1f))
                Stat("lb moved", "%,d".format(session.totalReps * session.ballWeightLbs), Modifier.weight(1f))
                Stat("calories", if (hasHr) "${session.calories.roundToInt()}" else "—", Modifier.weight(1f))
            }
            if (hasHr) {
                Row {
                    Stat("avg HR", "${session.avgHr}", Modifier.weight(1f))
                    Stat("max HR", "${session.maxHr}", Modifier.weight(1f), color = zoneColor(5))
                    val recs = session.sets.mapNotNull { it.recoveryBpm }
                    Stat("avg recovery", if (recs.isEmpty()) "—" else "↓${recs.average().roundToInt()}", Modifier.weight(1f), color = Mint)
                }
            }
        }

        if (hasHr) {
            SectionCard {
                Label("Heart rate")
                HrChart(session.hrSamples, maxHr)
            }
            SectionCard {
                Label("Time in zones")
                ZoneBars(session.zoneSec)
            }
        }

        SectionCard {
            Label("Sets")
            Row {
                Header("Set", 0.6f); Header("Reps", 0.8f); Header("Time", 1f)
                if (hasHr) { Header("Peak", 0.9f); Header("1-min ↓", 1f); Header(if (target > 0) "To $target" else "To target", 1.1f) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            session.sets.forEach { s ->
                Row {
                    Cell("${s.number}", 0.6f); Cell("${s.reps}", 0.8f); Cell(mmss(s.durationSec), 1f)
                    if (hasHr) {
                        Cell(if (s.peakHr > 0) "${s.peakHr}" else "—", 0.9f)
                        Cell(s.recoveryBpm?.let { if (s.recoverySec >= 60) "↓$it" else "↓$it/${s.recoverySec}s" } ?: "—", 1f)
                        Cell(s.timeToTargetSec?.let { mmss(it) } ?: if (s.targetHr > 0) "not hit" else "—", 1.1f)
                    }
                }
            }
            if (hasHr) {
                Text(
                    "To $target: time from the last slam of the set until heart rate fell to $target. " +
                        "1-min ↓: how far heart rate dropped in the first minute (or over a shorter rest). " +
                        "Faster times and bigger drops mean better conditioning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Done") }
        TextButton(onClick = { onToggleBaseline(!session.baseline) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (session.baseline) "Don't count this as a baseline test" else "Count this as a baseline test")
        }
        SwitchRow(
            "Lock this workout",
            if (session.isLocked) "Locked: it can't be deleted until you unlock it."
            else "Locking keeps it from being deleted by accident.",
            session.isLocked,
        ) { onToggleLock(it) }
        if (!session.isLocked) {
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete this workout") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this workout?") },
            text = { Text("It will be removed from your history for good.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Header(text: String, weight: Float) {
    Text(text, Modifier.weight(weight), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float) {
    Text(text, Modifier.weight(weight), style = MaterialTheme.typography.bodyMedium)
}
