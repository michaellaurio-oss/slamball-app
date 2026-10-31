package com.laurio.slamlog.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.laurio.slamlog.data.UserSettings
import com.laurio.slamlog.workout.Progress
import com.laurio.slamlog.workout.WorkoutViewModel
import com.laurio.slamlog.workout.mmss

/** Definition of the baseline test (sets, reps, ball, rest). Opened from the Baseline screen. */
@Composable
fun BaselineSettingsScreen(vm: WorkoutViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val sessions by vm.sessions.collectAsState()
    // Baseline tests that match the current definition: the ones a change would stop comparing.
    val matching = Progress.baselines(sessions, s).size
    // Warn once per visit; after confirming, further tweaks on this screen go straight through.
    var confirmed by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<UserSettings?>(null) }
    fun change(next: UserSettings) {
        if (matching > 0 && !confirmed) pending = next else vm.updateSettings(next)
    }

    BackHandler(onBack = onBack)

    pending?.let { next ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Change the baseline?") },
            text = {
                Text(
                    "You have $matching baseline test${if (matching == 1) "" else "s"} on the current settings. " +
                        "Changing them starts a fresh comparison: \"Is my recovery improving?\" will only compare " +
                        "tests done on the new settings. Your earlier tests stay in History, and changing the " +
                        "settings back brings them into the comparison again."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmed = true; vm.updateSettings(next); pending = null }) { Text("Change it") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Keep current") }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BASELINE SETTINGS", fontSize = 26.sp, fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }

        if (matching > 0) {
            Text(
                "$matching baseline test${if (matching == 1) " uses" else "s use"} these settings. Changing them starts a fresh comparison.",
                color = Amber, fontWeight = FontWeight.SemiBold,
            )
        }

        SectionCard {
            Label("The baseline test")
            Stepper("Sets", "${s.baselineSets}",
                onMinus = { change(s.copy(baselineSets = (s.baselineSets - 1).coerceAtLeast(1))) },
                onPlus = { change(s.copy(baselineSets = (s.baselineSets + 1).coerceAtMost(30))) })
            Stepper("Reps per set", "${s.baselineReps}",
                onMinus = { change(s.copy(baselineReps = (s.baselineReps - 1).coerceAtLeast(1))) },
                onPlus = { change(s.copy(baselineReps = (s.baselineReps + 1).coerceAtMost(200))) })
            Stepper("Ball weight", "${s.baselineBallLbs} lb",
                onMinus = { change(s.copy(baselineBallLbs = (s.baselineBallLbs - 5).coerceAtLeast(5))) },
                onPlus = { change(s.copy(baselineBallLbs = (s.baselineBallLbs + 5).coerceAtMost(150))) })
            SwitchRow("Rest until HR ≤ ${s.recoveryTargetHr}", "Each set starts from the same heart rate, so every set gets timed", s.baselineRestUntilTarget) {
                change(s.copy(baselineRestUntilTarget = it))
            }
            if (s.baselineRestUntilTarget) {
                Stepper("Minimum rest", mmss(s.baselineMinRestSec),
                    onMinus = { change(s.copy(baselineMinRestSec = (s.baselineMinRestSec - 15).coerceAtLeast(15))) },
                    onPlus = { change(s.copy(baselineMinRestSec = (s.baselineMinRestSec + 15).coerceAtMost(600))) })
            }
            Text(
                "Set this once and leave it alone. Only baseline tests that match these numbers are compared, " +
                    "so changing them starts a fresh comparison.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

    }
}
