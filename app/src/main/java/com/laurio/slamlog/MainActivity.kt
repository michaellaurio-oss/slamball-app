package com.laurio.slamlog

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.laurio.slamlog.data.Session
import com.laurio.slamlog.ui.HistoryScreen
import com.laurio.slamlog.ui.HomeScreen
import com.laurio.slamlog.ui.SettingsScreen
import com.laurio.slamlog.ui.SlamTheme
import com.laurio.slamlog.ui.SummaryScreen
import com.laurio.slamlog.ui.TrendsScreen
import com.laurio.slamlog.ui.WorkoutScreen
import com.laurio.slamlog.workout.Phase
import com.laurio.slamlog.workout.WorkoutViewModel

class MainActivity : ComponentActivity() {
    private val vm: WorkoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SlamTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm, Modifier.safeDrawingPadding())
                }
            }
        }
    }
}

private enum class Screen { HOME, WORKOUT, SUMMARY, HISTORY, TRENDS, SETTINGS }

private fun neededPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    add(Manifest.permission.RECORD_AUDIO)
}.toTypedArray()

@Composable
private fun AppRoot(vm: WorkoutViewModel, modifier: Modifier) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var shown by remember { mutableStateOf<Session?>(null) }
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.collectAsState()
    val sessions by vm.sessions.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsResult()
    }
    val requestPermissions = { launcher.launch(neededPermissions()) }

    LaunchedEffect(Unit) {
        if (vm.hasBluetoothPermission() && vm.hasMicPermission()) vm.onPermissionsResult() else requestPermissions()
    }

    // Move between screens as the workout progresses.
    LaunchedEffect(ui.phase) {
        when (ui.phase) {
            Phase.DONE -> {
                shown = vm.lastSession
                screen = if (shown != null) Screen.SUMMARY else Screen.HOME
                vm.acknowledgeDone()
            }
            Phase.IDLE -> if (screen == Screen.WORKOUT) screen = Screen.HOME
            else -> if (screen != Screen.WORKOUT) screen = Screen.WORKOUT
        }
    }

    androidx.compose.foundation.layout.Box(modifier) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                vm = vm,
                onRequestPermissions = requestPermissions,
                onStart = { vm.startWorkout(); screen = Screen.WORKOUT },
                onStartBaseline = { vm.startBaseline(); screen = Screen.WORKOUT },
                onHistory = { screen = Screen.HISTORY },
                onTrends = { screen = Screen.TRENDS },
                onSettings = { screen = Screen.SETTINGS },
            )
            Screen.WORKOUT -> WorkoutScreen(vm)
            Screen.SUMMARY -> {
                val s = shown
                if (s == null) {
                    LaunchedEffect(Unit) { screen = Screen.HOME }
                } else {
                    SummaryScreen(
                        session = s,
                        maxHr = settings.maxHr,
                        onRate = { v -> shown = vm.rateSession(s, v) },
                        onToggleBaseline = { v -> shown = vm.setBaseline(s, v) },
                        onDelete = { vm.deleteSession(s.id); shown = null; screen = Screen.HISTORY },
                        onDone = { shown = null; screen = if (sessions.any { it.id == s.id } && s !== vm.lastSession) Screen.HISTORY else Screen.HOME },
                    )
                }
            }
            Screen.HISTORY -> HistoryScreen(
                sessions = sessions,
                onOpen = { shown = it; screen = Screen.SUMMARY },
                onTrends = { screen = Screen.TRENDS },
                onExport = { vm.exportCsv() },
                onBack = { screen = Screen.HOME },
            )
            Screen.TRENDS -> TrendsScreen(
                sessions = sessions,
                settings = settings,
                target = settings.recoveryTargetHr,
                onOpen = { shown = it; screen = Screen.SUMMARY },
                onBack = { screen = Screen.HOME },
            )
            Screen.SETTINGS -> SettingsScreen(vm, onRequestPermissions = requestPermissions, onBack = { screen = Screen.HOME })
        }
    }
}
