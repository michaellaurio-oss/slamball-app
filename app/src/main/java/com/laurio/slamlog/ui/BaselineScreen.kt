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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.laurio.slamlog.workout.Progress
import com.laurio.slamlog.workout.WorkoutViewModel
import com.laurio.slamlog.workout.mmss
import kotlin.math.roundToInt

/** The baseline test: what it is, how it's going, and the button to run it. Opened from Home. */
@Composable
fun BaselineScreen(
    vm: WorkoutViewModel,
    onStartBaseline: () -> Unit,
    onTrends: () -> Unit,
    onBaselineSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val state by vm.hr.state.collectAsState()

    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BASELINE", fontSize = 26.sp, fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }

        val target = settings.baselineTargetHr
        val baselines = Progress.baselines(sessions, settings)
        val verdict = Progress.verdict(baselines, target)
        SectionCard {
            Label("The test")
            Text(
                "${settings.baselineSets} × ${settings.baselineReps} · ${settings.baselineBallLbs} lb" +
                    if (settings.baselineRestUntilTarget) " · rest ${mmss(settings.baselineMinRestSec)}+ until HR ≤ $target" else "",
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
        }

        Button(
            onClick = onStartBaseline,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text("START BASELINE  ·  ${settings.baselineSets} × ${settings.baselineReps}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (state != State.CONNECTED) {
            Text(
                "Heart rate isn't connected. A baseline without heart rate can't be timed, so connect the strap first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(onClick = onTrends, modifier = Modifier.fillMaxWidth()) { Text("Trends") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBaselineSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("BASELINE SETTINGS", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
    }
}
