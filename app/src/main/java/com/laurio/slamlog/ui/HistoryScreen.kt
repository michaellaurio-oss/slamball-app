package com.laurio.slamlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurio.slamlog.data.Session
import com.laurio.slamlog.workout.mmss
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val dayFmt = SimpleDateFormat("EEE, MMM d, yyyy · h:mm a", Locale.US)

@Composable
fun HistoryScreen(
    sessions: List<Session>,
    onOpen: (Session) -> Unit,
    onTrends: () -> Unit,
    onExport: () -> String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HISTORY", fontSize = 26.sp, fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        if (sessions.isEmpty()) {
            item { Text("No workouts yet. Your first one will show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item {
                SectionCard {
                    Label("All time")
                    Row {
                        Stat("workouts", "${sessions.size}", Modifier.weight(1f))
                        Stat("slams", "%,d".format(sessions.sumOf { it.totalReps }), Modifier.weight(1f), color = Orange)
                        Stat("lb moved", "%,d".format(sessions.sumOf { it.totalReps * it.ballWeightLbs }), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onTrends, modifier = Modifier.weight(1f)) { Text("See trends") }
                        OutlinedButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Slam Log workouts (CSV)")
                                putExtra(Intent.EXTRA_TEXT, onExport())
                            }
                            context.startActivity(Intent.createChooser(send, "Export workouts"))
                        }, modifier = Modifier.weight(1f)) { Text("Export") }
                    }
                    Text(
                        "Export sends every set as a spreadsheet (CSV): email it to yourself or save it to Drive, " +
                            "then you can open it in Excel or share it with Claude.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(sessions, key = { it.id }) { s ->
            SectionCard(Modifier.clickable { onOpen(s) }) {
                Text(dayFmt.format(Date(s.startMillis)), fontWeight = FontWeight.SemiBold)
                Row {
                    Column(Modifier.weight(1f)) {
                        if (s.baseline) {
                            Text("BASELINE TEST", color = Sky, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        Text("${s.totalReps} slams · ${s.sets.size} × ${s.targetReps} · ${s.ballWeightLbs} lb")
                        val hr = if (s.avgHr > 0) " · avg ${s.avgHr} bpm · ${s.calories.roundToInt()} cal" else ""
                        Text("${mmss(s.durationSec)}$hr", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        s.rpe?.let {
                            Text("Felt $it/10 · ${effortLabel(it)}", style = MaterialTheme.typography.bodySmall,
                                color = effortColor(it), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    val target = s.sets.firstOrNull { it.targetHr > 0 }?.targetHr
                    val avg = target?.let { s.avgTimeToTarget(it) }
                    if (target != null && avg != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(mmss(avg.roundToInt()), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Sky)
                            Text("to $target", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
