package com.laurio.slamlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurio.slamlog.workout.Phase
import com.laurio.slamlog.workout.Physiology
import com.laurio.slamlog.workout.WorkoutViewModel
import com.laurio.slamlog.workout.mmss
import kotlin.math.roundToInt

@Composable
fun WorkoutScreen(vm: WorkoutViewModel) {
    val ui by vm.ui.collectAsState()
    val bpm by vm.hr.heartRate.collectAsState()
    val settings by vm.settings.collectAsState()
    val cfg by vm.config.collectAsState()
    val mic by vm.micLevel.collectAsState()
    val micError by vm.micError.collectAsState()
    var confirmEnd by remember { mutableStateOf(false) }

    // Keep the screen on for the whole workout.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { confirmEnd = true }

    // Slam effects: fire on every counted rep, and a bigger one when a set is finished.
    var burst by remember { mutableStateOf(0) }
    var bigBurst by remember { mutableStateOf(false) }
    var lastReps by remember { mutableStateOf(ui.reps) }
    var lastSets by remember { mutableStateOf(ui.completedSets.size) }
    LaunchedEffect(ui.reps, ui.completedSets.size) {
        val setDone = ui.completedSets.size > lastSets
        if (settings.slamEffects && (ui.reps > lastReps || setDone)) {
            bigBurst = setDone
            burst++
        }
        lastReps = ui.reps
        lastSets = ui.completedSets.size
    }
    val pop = rememberPop(burst, bigBurst)
    val shake = rememberShake(burst)
    val shakePx = with(LocalDensity.current) { (if (bigBurst) 14.dp else 8.dp).toPx() }

    val (phaseText, phaseColor) = when (ui.phase) {
        Phase.GET_READY -> "GET READY" to Amber
        Phase.WORK -> "SLAM" to Orange
        Phase.REST -> "REST" to Mint
        Phase.RECOVERY -> "RECOVERY CHECK" to Sky
        else -> "" to MaterialTheme.colorScheme.onSurface
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = shake * shakePx }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(phaseText, color = phaseColor, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text(mmss(ui.elapsedSec), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            (if (ui.baseline) "Baseline test · " else "") +
                (if (ui.setNumber == 0) "Set 1 of ${ui.totalSets} up next" else "Set ${ui.setNumber} of ${ui.totalSets}"),
            style = MaterialTheme.typography.titleMedium,
            color = if (ui.baseline) Sky else MaterialTheme.colorScheme.onSurface,
        )

        // ---- Big number, with the slam burst drawn over it ----
        Box {
        SectionCard {
            when (ui.phase) {
                Phase.WORK -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        Text("${ui.reps}", fontSize = 120.sp, fontWeight = FontWeight.Black, color = Orange,
                            modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop })
                        Text(" / ${ui.targetReps}", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 22.dp))
                    }
                    Text("reps · set time ${mmss(ui.setElapsedSec)}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    val resting = ui.phase == Phase.REST || ui.phase == Phase.RECOVERY
                    val waitingOnHr = resting && ui.timeToTarget == null && ui.targetHr > 0 &&
                        (ui.holding || (ui.phase == Phase.RECOVERY && ui.secondsLeft <= 0))
                    if (waitingOnHr) {
                        // Count up from the last slam until HR reaches the target.
                        Text(mmss(ui.sinceSlamSec), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            fontSize = 96.sp, fontWeight = FontWeight.Black, color = Sky)
                        Text("since last slam · waiting for ${ui.targetHr} bpm",
                            Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(mmss(ui.secondsLeft), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            fontSize = 96.sp, fontWeight = FontWeight.Black, color = phaseColor)
                    }
                    if (resting && ui.targetHr > 0) {
                        val reached = ui.timeToTarget
                        if (reached != null) {
                            Text("Down to ${ui.targetHr} in ${mmss(reached)}",
                                Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Mint)
                        } else if (!waitingOnHr && bpm != null) {
                            Text("${mmss(ui.sinceSlamSec)} since last slam · to ${ui.targetHr}…",
                                Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Sky)
                        }
                    }
                    val rec = ui.recoveryNow
                    if (resting && rec != null) {
                        Text("Heart rate down $rec bpm since the set ended",
                            Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
            SlamBurst(burst, if (bigBurst) Mint else Orange, bigBurst, Modifier.matchParentSize())
        }

        // ---- Heart rate ----
        SectionCard {
            val current = bpm
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current != null) {
                    val zone = Physiology.zone(current, settings.maxHr)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$current", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = zoneColor(zone))
                            Text(" bpm", modifier = Modifier.padding(bottom = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${Physiology.zoneNames[zone]} · ${(100.0 * current / settings.maxHr).roundToInt()}% of max",
                            color = zoneColor(zone))
                    }
                } else {
                    Text("No heart rate", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Stat("calories", "${ui.calories.roundToInt()}")
            }
        }

        // ---- Mic ----
        if (cfg.autoCount) {
            val err = micError
            if (err != null) {
                Text(err, color = MaterialTheme.colorScheme.error)
            } else if (ui.phase == Phase.WORK) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Listening for slams", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LevelBar(mic, color = Orange)
                }
            }
        }

        // ---- Controls ----
        when (ui.phase) {
            Phase.WORK -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { vm.addRep(-1) }, modifier = Modifier.weight(1f).height(72.dp)) {
                        Text("−1", fontSize = 26.sp)
                    }
                    Button(onClick = { vm.addRep(1) }, modifier = Modifier.weight(2f).height(72.dp)) {
                        Text("+1", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = { vm.endSet() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("End set now")
                }
            }
            Phase.REST -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!ui.holding) {
                    OutlinedButton(onClick = { vm.addRestTime(15) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("+15 s") }
                }
                Button(onClick = { vm.skipRest() }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Start next set") }
            }
            Phase.GET_READY -> Button(onClick = { vm.skipRest() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Start now")
            }
            Phase.RECOVERY -> OutlinedButton(onClick = { vm.skipRest() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Skip recovery check and save")
            }
            else -> Unit
        }

        // ---- Finished sets ----
        if (ui.completedSets.isNotEmpty()) {
            SectionCard {
                Label("Sets done")
                ui.completedSets.forEach { s ->
                    val hrPart = if (s.avgHr > 0) " · avg ${s.avgHr} · peak ${s.peakHr}" else ""
                    val recPart = s.recoveryBpm?.let { " · ↓$it" } ?: ""
                    val toTarget = s.timeToTargetSec?.let { " · ${s.targetHr} in ${mmss(it)}" } ?: ""
                    Text("Set ${s.number}: ${s.reps} reps in ${mmss(s.durationSec)}$hrPart$recPart$toTarget",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { confirmEnd = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("End workout") }
    }

    if (confirmEnd) {
        val anything = ui.completedSets.isNotEmpty() || (ui.phase == Phase.WORK && ui.reps > 0)
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End workout?") },
            text = { Text(if (anything) "What you've done so far will be saved." else "Nothing has been done yet, so nothing will be saved.") },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; vm.endWorkout() }) {
                    Text("End", color = Color(0xFFEF4444))
                }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } },
        )
    }
}
