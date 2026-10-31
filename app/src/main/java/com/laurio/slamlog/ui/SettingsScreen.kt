package com.laurio.slamlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurio.slamlog.workout.WorkoutViewModel
import com.laurio.slamlog.workout.mmss

@Composable
fun SettingsScreen(vm: WorkoutViewModel, onRequestPermissions: () -> Unit, onAboutYou: () -> Unit, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val testing by vm.testing.collectAsState()
    val testCount by vm.testCount.collectAsState()
    val mic by vm.micLevel.collectAsState()
    var micMsg by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)
    DisposableEffect(Unit) { onDispose { vm.stopMicTest() } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SETTINGS", fontSize = 26.sp, fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }

        // ---- About you (own screen) ----
        SectionCard(Modifier.clickable(onClick = onAboutYou)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("About you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        (if (s.hasBirthday) "Age ${s.age}" else "Birthday not set") +
                            " · ${s.weightLbs} lb · max HR ${s.maxHr}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.hasBirthday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---- Recovery target ----
        SectionCard {
            Label("Recovery target")
            Stepper("Time from last slam to", "${s.recoveryTargetHr} bpm",
                onMinus = { vm.updateSettings(s.copy(recoveryTargetHr = (s.recoveryTargetHr - 5).coerceAtLeast(80))) },
                onPlus = { vm.updateSettings(s.copy(recoveryTargetHr = (s.recoveryTargetHr + 5).coerceAtMost(200))) })
            Text(
                "After every set, Slam Log times how long your heart rate takes to fall to this number. " +
                    "Trends only compare workouts measured against the same target, so pick one and stick with it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Baseline test ----
        SectionCard {
            Label("Baseline test")
            Stepper("Sets", "${s.baselineSets}",
                onMinus = { vm.updateSettings(s.copy(baselineSets = (s.baselineSets - 1).coerceAtLeast(1))) },
                onPlus = { vm.updateSettings(s.copy(baselineSets = (s.baselineSets + 1).coerceAtMost(30))) })
            Stepper("Reps per set", "${s.baselineReps}",
                onMinus = { vm.updateSettings(s.copy(baselineReps = (s.baselineReps - 1).coerceAtLeast(1))) },
                onPlus = { vm.updateSettings(s.copy(baselineReps = (s.baselineReps + 1).coerceAtMost(200))) })
            Stepper("Ball weight", "${s.baselineBallLbs} lb",
                onMinus = { vm.updateSettings(s.copy(baselineBallLbs = (s.baselineBallLbs - 5).coerceAtLeast(5))) },
                onPlus = { vm.updateSettings(s.copy(baselineBallLbs = (s.baselineBallLbs + 5).coerceAtMost(150))) })
            SwitchRow("Rest until HR ≤ ${s.recoveryTargetHr}", "Each set starts from the same heart rate, so every set gets timed", s.baselineRestUntilTarget) {
                vm.updateSettings(s.copy(baselineRestUntilTarget = it))
            }
            if (s.baselineRestUntilTarget) {
                Stepper("Minimum rest", mmss(s.baselineMinRestSec),
                    onMinus = { vm.updateSettings(s.copy(baselineMinRestSec = (s.baselineMinRestSec - 15).coerceAtLeast(15))) },
                    onPlus = { vm.updateSettings(s.copy(baselineMinRestSec = (s.baselineMinRestSec + 15).coerceAtMost(600))) })
            }
            Text(
                "Set this once and leave it alone. Only baseline tests that match these numbers are compared, " +
                    "so changing them starts a fresh comparison.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Slam detection ----
        SectionCard {
            Label("Slam detection")
            Text("Sensitivity: ${(s.sensitivity * 100).toInt()}%")
            Slider(value = s.sensitivity, onValueChange = { vm.updateSettings(s.copy(sensitivity = it)) })
            Text(
                "Too many extra reps (from music, other people, or the ball rolling)? Lower it. Missing slams? Raise it. " +
                    "Keep the phone a few feet from where the ball lands, mic side up.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (testing) {
                androidx.compose.foundation.layout.Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Slams heard: ", style = MaterialTheme.typography.titleMedium)
                        Text("$testCount", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Orange)
                    }
                    if (s.slamEffects) {
                        SlamBurst(testCount, Orange, false, Modifier.matchParentSize())
                    }
                }
                LevelBar(mic, color = Orange)
                OutlinedButton(onClick = { vm.stopMicTest() }) { Text("Stop test") }
            } else {
                FilledTonalButton(onClick = {
                    micMsg = null
                    if (!vm.hasMicPermission()) {
                        onRequestPermissions()
                    } else if (!vm.startMicTest()) {
                        micMsg = "Couldn't open the microphone."
                    }
                }) { Text("Test slam detection") }
                Text("Do 10 slams and check the count matches, then adjust sensitivity.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            micMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        // ---- Audio cues ----
        SectionCard {
            Label("Audio cues")
            SwitchRow("Voice callouts", "Set starts, rest countdown, recovery results", s.voice) {
                vm.updateSettings(s.copy(voice = it))
            }
            SwitchRow("Click on each counted rep", "Lets you hear that a slam registered", s.repTick) {
                vm.updateSettings(s.copy(repTick = it))
            }
            SwitchRow("Slam effects", "Shockwave burst and screen shake on every counted slam", s.slamEffects) {
                vm.updateSettings(s.copy(slamEffects = it))
            }
            Text(
                "During a set there's no talking, so the mic hears every slam: just a double beep " +
                    "when 3 reps are left.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Strap ----
        SectionCard {
            Label("Heart rate strap")
            val name by vm.hr.deviceName.collectAsState()
            Text(if (name != null) "Paired: $name" else "No strap paired")
            if (name != null) {
                OutlinedButton(onClick = { vm.hr.forgetStrap() }) { Text("Forget this strap") }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
