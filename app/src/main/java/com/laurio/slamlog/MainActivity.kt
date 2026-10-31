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
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.laurio.slamlog.data.Session
import com.laurio.slamlog.ui.AboutYouScreen
import com.laurio.slamlog.ui.BaselineScreen
import com.laurio.slamlog.ui.BaselineSettingsScreen
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

private enum class Screen { HOME, WORKOUT, SUMMARY, HISTORY, TRENDS, SETTINGS, ABOUT_YOU, BASELINE, BASELINE_SETTINGS }

private fun neededPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    add(Manifest.permission.RECORD_AUDIO)
    // The "workout in progress" notification (Android 13+)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun AppRoot(vm: WorkoutViewModel, modifier: Modifier) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    // Where Trends' Back goes (Home, or Baseline when opened from there).
    var trendsBack by rememberSaveable { mutableStateOf(Screen.HOME) }
    // Where About you's Back goes (Settings, or Home when opened from the birthday reminder).
    var aboutBack by rememberSaveable { mutableStateOf(Screen.SETTINGS) }
    var shown by remember { mutableStateOf<Session?>(null) }
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.collectAsState()
    val sessions by vm.sessions.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsResult()
    }
    val requestPermissions = { launcher.launch(neededPermissions()) }

    LaunchedEffect(Unit) {
        vm.refreshPermissions()
        if (!vm.hasBluetoothPermission() || !vm.hasMicPermission() || !vm.hasNotificationPermission()) requestPermissions()
    }

    // Permissions can be changed in system settings while the app is in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refreshPermissions() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
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
                onBaseline = { screen = Screen.BASELINE },
                onHistory = { screen = Screen.HISTORY },
                onTrends = { trendsBack = Screen.HOME; screen = Screen.TRENDS },
                onSettings = { screen = Screen.SETTINGS },
                onAboutYou = { aboutBack = Screen.HOME; screen = Screen.ABOUT_YOU },
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
                onTrends = { trendsBack = Screen.HOME; screen = Screen.TRENDS },
                onExport = { vm.exportCsvFile() },
                onBack = { screen = Screen.HOME },
            )
            Screen.TRENDS -> TrendsScreen(
                sessions = sessions,
                settings = settings,
                target = settings.recoveryTargetHr,
                onOpen = { shown = it; screen = Screen.SUMMARY },
                onBack = { screen = trendsBack },
            )
            Screen.SETTINGS -> SettingsScreen(
                vm,
                onRequestPermissions = requestPermissions,
                onAboutYou = { aboutBack = Screen.SETTINGS; screen = Screen.ABOUT_YOU },
                onBack = { screen = Screen.HOME },
            )
            Screen.ABOUT_YOU -> AboutYouScreen(vm, onBack = { screen = aboutBack })
            Screen.BASELINE -> BaselineScreen(
                vm,
                onStartBaseline = { vm.startBaseline(); screen = Screen.WORKOUT },
                onTrends = { trendsBack = Screen.BASELINE; screen = Screen.TRENDS },
                onBaselineSettings = { screen = Screen.BASELINE_SETTINGS },
                onBack = { screen = Screen.HOME },
            )
            Screen.BASELINE_SETTINGS -> BaselineSettingsScreen(vm, onBack = { screen = Screen.BASELINE })
        }
    }
}
