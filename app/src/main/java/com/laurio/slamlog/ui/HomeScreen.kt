package com.laurio.slamlog.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurio.slamlog.ble.HeartRateManager.State
import com.laurio.slamlog.workout.Physiology
import com.laurio.slamlog.workout.Progress
import com.laurio.slamlog.workout.WorkoutViewModel
import com.laurio.slamlog.workout.mmss
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    vm: WorkoutViewModel,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStartBaseline: () -> Unit,
    onHistory: () -> Unit,
    onTrends: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.hr.state.collectAsState()
    val bpm by vm.hr.heartRate.collectAsState()
    val devices by vm.hr.devices.collectAsState()
    val name by vm.hr.deviceName.collectAsState()
    val error by vm.hr.error.collectAsState()
    val cfg by vm.config.collectAsState()
    val settings by vm.settings.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val perms by vm.permissions.collectAsState()
    val bluetoothOn by vm.hr.bluetoothOn.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("SLAM LOG", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)

        if (!settings.hasBirthday) {
            SectionCard {
                Text("Add your birthday", fontWeight = FontWeight.Bold)
                Text(
                    "Heart rate zones and max heart rate are based on your age. Until you add your birthday, " +
                        "they assume you're ${settings.age}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSettings) { Text("Go to Settings") }
            }
        }

        // ---- Heart rate strap ----
        SectionCard {
            Label("Heart rate")
            Row(verticalAlignment = Alignment.CenterVertically) {
                val status = when (state) {
                    State.CONNECTED -> "Connected · ${name ?: "strap"}"
                    State.CONNECTING -> "Connecting to ${name ?: "strap"}…"
                    State.WAITING -> "Waiting for ${name ?: "your strap"}. Put it on to connect."
                    State.SCANNING -> "Searching… wet the pads and put the strap on."
                    State.IDLE -> if (vm.hr.hasSavedStrap) "Not connected" else "No strap paired yet"
                }
                Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                val current = bpm
                if (state == State.CONNECTED && current != null) {
                    val zone = Physiology.zone(current, settings.maxHr)
                    Text("$current", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = zoneColor(zone))
                    Text(" bpm", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!perms.bluetooth) {
                Text("Slam Log needs the Nearby devices permission to reach your strap.", color = MaterialTheme.colorScheme.error)
                Button(onClick = onRequestPermissions) { Text("Allow permissions") }
            } else if (!bluetoothOn) {
                Text("Bluetooth is off. Turn it on in your phone's quick settings.", color = MaterialTheme.colorScheme.error)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    State.CONNECTED, State.CONNECTING, State.WAITING ->
                        OutlinedButton(onClick = { vm.hr.disconnect() }) { Text("Disconnect") }
                    State.SCANNING ->
                        OutlinedButton(onClick = { vm.hr.stopScan() }) { Text("Stop searching") }
                    State.IDLE -> {
                        Button(onClick = { vm.hr.startScan() }) { Text("Find strap") }
                        if (vm.hr.hasSavedStrap) {
                            OutlinedButton(onClick = { vm.hr.autoConnect() }) { Text("Reconnect") }
                        }
                    }
                }
            }

            if (state == State.SCANNING || (state == State.IDLE && devices.isNotEmpty())) {
                devices.forEach { d ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.name, Modifier.weight(1f))
                        TextButton(onClick = { vm.hr.connect(d) }) { Text("Connect") }
                    }
                }
                if (state == State.SCANNING && devices.isEmpty()) {
                    Text("Nothing yet. The H808S wakes up when it touches skin.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ---- Baseline test: the fair yardstick for recovery ----
        val target = settings.recoveryTargetHr
        val baselines = Progress.baselines(sessions, settings)
        val verdict = Progress.verdict(baselines, target)
        SectionCard {
            Label("Baseline test")
            Text(
                "${settings.baselineSets} × ${settings.baselineReps} · ${settings.baselineBallLbs} lb" +
                    if (settings.baselineRestUntilTarget) " · rest until HR ≤ $target" else "",
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
            )
            Text(
                "The same workout every time, so your time from last slam to $target can be compared fairly " +
                    "while your training workouts get bigger. Repeat it every couple of weeks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (baselines.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Stat("latest", mmss(baselines.last().avgTimeToTarget(target)!!.roundToInt()), Modifier.weight(1f), color = Sky)
                    Stat("first", mmss(baselines.first().avgTimeToTarget(target)!!.roundToInt()), Modifier.weight(1f))
                    Stat("tests", "${baselines.size}", Modifier.weight(1f))
                }
                Text(
                    verdict.headline,
                    fontWeight = FontWeight.SemiBold,
                    color = when (verdict.improving) { true -> Mint; false -> Amber; null -> MaterialTheme.colorScheme.onSurface },
                    modifier = Modifier.clickable(onClick = onTrends),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartBaseline, modifier = Modifier.weight(1f)) { Text("Start baseline test") }
                OutlinedButton(onClick = onTrends) { Text("Trends") }
            }
        }

        // ---- Training workout setup ----
        SectionCard {
            Label("Training workout")
            Stepper("Sets", "${cfg.sets}",
                onMinus = { vm.updateConfig(cfg.copy(sets = (cfg.sets - 1).coerceAtLeast(1))) },
                onPlus = { vm.updateConfig(cfg.copy(sets = (cfg.sets + 1).coerceAtMost(30))) })
            Stepper("Reps per set", "${cfg.targetReps}",
                onMinus = { vm.updateConfig(cfg.copy(targetReps = (cfg.targetReps - 1).coerceAtLeast(1))) },
                onPlus = { vm.updateConfig(cfg.copy(targetReps = (cfg.targetReps + 1).coerceAtMost(200))) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rest until HR ≤ ${settings.recoveryTargetHr}")
                    Text(
                        "Next set starts 5 s after you hit ${settings.recoveryTargetHr}, so every set gets timed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = cfg.restUntilTarget, onCheckedChange = { vm.updateConfig(cfg.copy(restUntilTarget = it)) })
            }
            Stepper(if (cfg.restUntilTarget) "Rest if no HR" else "Rest", mmss(cfg.restSec),
                onMinus = { vm.updateConfig(cfg.copy(restSec = (cfg.restSec - 15).coerceAtLeast(15))) },
                onPlus = { vm.updateConfig(cfg.copy(restSec = (cfg.restSec + 15).coerceAtMost(600))) })
            Stepper("Ball weight", "${cfg.ballWeightLbs} lb",
                onMinus = { vm.updateConfig(cfg.copy(ballWeightLbs = (cfg.ballWeightLbs - 5).coerceAtLeast(5))) },
                onPlus = { vm.updateConfig(cfg.copy(ballWeightLbs = (cfg.ballWeightLbs + 5).coerceAtMost(150))) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Count reps by listening")
                    Text(
                        "Uses the mic to hear each slam. You can still tap +1 / −1.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = cfg.autoCount, onCheckedChange = {
                    vm.updateConfig(cfg.copy(autoCount = it))
                    if (it && !perms.mic) onRequestPermissions()
                })
            }
        }

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text("START TRAINING  ·  ${cfg.sets} × ${cfg.targetReps}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (state != State.CONNECTED) {
            Text(
                "Heart rate isn't connected. The workout will still run, but without HR, zones, recovery or calories.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) {
                Text(if (sessions.isEmpty()) "History" else "History (${sessions.size})")
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
