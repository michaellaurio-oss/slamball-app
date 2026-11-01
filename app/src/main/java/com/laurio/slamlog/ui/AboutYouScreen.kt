package com.laurio.slamlog.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurio.slamlog.data.UserSettings
import com.laurio.slamlog.workout.WorkoutViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val bdayFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** Personal details (birthday, weight, calorie formula, max heart rate), opened from Settings. */
@Composable
fun AboutYouScreen(vm: WorkoutViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ABOUT YOU", fontSize = 26.sp, fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }

        SectionCard {
            Label("Used for heart rate zones and calories")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Birthday")
                    val bday = s.birthday
                    Text(
                        if (bday != null) "${bdayFmt.format(bday)} · age ${s.age}" else "Not set. Zones are using age ${s.age}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bday != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                FilledTonalButton(onClick = {
                    val start = s.birthday ?: LocalDate.now().minusYears(UserSettings.DEFAULT_AGE.toLong())
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> vm.updateSettings(s.copy(birthDate = LocalDate.of(y, m + 1, d).toString())) },
                        start.year, start.monthValue - 1, start.dayOfMonth,
                    ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }) { Text(if (s.hasBirthday) "Change" else "Set") }
            }
            Stepper("Body weight", "${s.weightLbs} lb",
                onMinus = { vm.updateSettings(s.copy(weightLbs = (s.weightLbs - 1).coerceAtLeast(80))) },
                onPlus = { vm.updateSettings(s.copy(weightLbs = (s.weightLbs + 1).coerceAtMost(400))) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calorie formula", Modifier.weight(1f))
                if (s.male) Button(onClick = {}) { Text("Male") } else OutlinedButton(onClick = { vm.updateSettings(s.copy(male = true)) }) { Text("Male") }
                if (!s.male) Button(onClick = {}) { Text("Female") } else OutlinedButton(onClick = { vm.updateSettings(s.copy(male = false)) }) { Text("Female") }
            }
            Stepper(
                "Max heart rate",
                if (s.maxHrOverride > 0) "${s.maxHrOverride}" else "Auto ${s.estimatedMaxHr}",
                onMinus = {
                    vm.updateSettings(s.copy(maxHrOverride = (s.maxHr - 1).coerceAtLeast(120)))
                },
                onPlus = {
                    vm.updateSettings(s.copy(maxHrOverride = (s.maxHr + 1).coerceAtMost(230)))
                },
            )
            if (s.maxHrOverride > 0) {
                TextButton(onClick = { vm.updateSettings(s.copy(maxHrOverride = 0)) }) { Text("Use the age estimate (${s.estimatedMaxHr})") }
            }
            Text(
                "Auto uses 220 minus your age, so your zones shift as you get older. If you know your real max " +
                    "(the highest number you've seen on the strap during an all-out effort), enter it instead. " +
                    "That's more accurate than any age formula.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Label("Heart rate alarm")
            SwitchRow(
                "Max heart rate warning",
                "A siren sounds if your heart rate reaches the limit during a workout, and again every " +
                    "15 seconds while it stays there.",
                s.hrAlarm,
            ) { vm.updateSettings(s.copy(hrAlarm = it)) }
            if (s.hrAlarm) {
                Stepper(
                    "Warn at",
                    "${s.alarmBpm} bpm",
                    onMinus = { vm.updateSettings(s.copy(hrAlarmBpm = (s.alarmBpm - 1).coerceAtLeast(100))) },
                    onPlus = { vm.updateSettings(s.copy(hrAlarmBpm = (s.alarmBpm + 1).coerceAtMost(230))) },
                )
                Text(
                    "Your max heart rate is ${s.maxHr}" +
                        if (s.alarmBpm == s.maxHr) "." else " (warning at ${s.alarmBpm * 100 / s.maxHr}% of it).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { vm.testSiren() }) { Text("Test the siren") }
            }
        }
    }
}
