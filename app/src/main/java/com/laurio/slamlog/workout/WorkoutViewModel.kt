package com.laurio.slamlog.workout

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laurio.slamlog.audio.SlamDetector
import com.laurio.slamlog.audio.Speaker
import com.laurio.slamlog.ble.HeartRateManager
import com.laurio.slamlog.data.Session
import com.laurio.slamlog.data.SessionStore
import com.laurio.slamlog.data.SetResult
import com.laurio.slamlog.data.SettingsStore
import com.laurio.slamlog.data.UserSettings
import com.laurio.slamlog.data.WorkoutConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class Phase { IDLE, GET_READY, WORK, REST, RECOVERY, DONE }

data class WorkoutUi(
    val phase: Phase = Phase.IDLE,
    val setNumber: Int = 0,
    val totalSets: Int = 0,
    val reps: Int = 0,
    val targetReps: Int = 0,
    /** Countdown for get-ready, rest and the final recovery check. */
    val secondsLeft: Int = 0,
    val elapsedSec: Int = 0,
    val setElapsedSec: Int = 0,
    val calories: Double = 0.0,
    val completedSets: List<SetResult> = emptyList(),
    /** How far heart rate has dropped since the set ended (during rest). */
    val recoveryNow: Int? = null,
    /** During rest: seconds since the last slam of the set. */
    val sinceSlamSec: Int = 0,
    /** HR target for this rest, and how long it took to reach it (null = not yet). */
    val targetHr: Int = 0,
    val timeToTarget: Int? = null,
    /** Rest-until-target mode: the rest clock is paused until HR reaches the target. */
    val holding: Boolean = false,
    val baseline: Boolean = false,
)

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val sessionStore = SessionStore(app)
    private val main = Handler(Looper.getMainLooper())

    private val _settings = MutableStateFlow(settingsStore.loadSettings())
    val settings: StateFlow<UserSettings> = _settings

    private val _config = MutableStateFlow(settingsStore.loadConfig())
    val config: StateFlow<WorkoutConfig> = _config

    val hr = HeartRateManager(app, settingsStore)
    private val speaker = Speaker(app).also { it.voiceEnabled = _settings.value.voice }
    private val detector = SlamDetector(
        onSlam = { main.post { onSlam() } },
        isSuppressed = { speaker.isBusy() },
    )
    val micLevel: StateFlow<Float> = detector.level

    private val _ui = MutableStateFlow(WorkoutUi())
    val ui: StateFlow<WorkoutUi> = _ui

    private val _sessions = MutableStateFlow(sessionStore.load())
    val sessions: StateFlow<List<Session>> = _sessions

    /** The session that just finished, for the summary screen. */
    var lastSession: Session? = null
        private set

    private val _micError = MutableStateFlow<String?>(null)
    val micError: StateFlow<String?> = _micError

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing
    private val _testCount = MutableStateFlow(0)
    val testCount: StateFlow<Int> = _testCount

    // ---- Per-workout bookkeeping ----
    private var cfg = WorkoutConfig()
    private var ticker: Job? = null
    private var startMillis = 0L
    private val hrSamples = ArrayList<Int>()
    private val zoneSec = IntArray(6)
    private val setHr = ArrayList<Int>()
    private var calories = 0.0
    private var restStartHr = 0
    private var restElapsed = 0
    private var recovery60: Int? = null
    /** When the latest rep counted from the mic happened (the slam itself, not when it was processed). */
    private var lastMicRepAt = 0L
    /** When +1 was last tapped. */
    private var lastTapAt = 0L
    private var setStartedAt = 0L
    private var lastSlamAt = 0L
    private var waitingForTarget = false
    /** Seconds in a row with no heart rate reading during rest (strap dropped out). */
    private var noHrSec = 0

    // ---------- Settings ----------

    fun updateSettings(s: UserSettings) {
        _settings.value = s
        settingsStore.saveSettings(s)
        speaker.voiceEnabled = s.voice
        detector.sensitivity = s.sensitivity
    }

    fun updateConfig(c: WorkoutConfig) {
        _config.value = c
        settingsStore.saveConfig(c)
    }

    fun hasMicPermission() = granted(Manifest.permission.RECORD_AUDIO)

    /** Android 13+: needed to show the workout notification (the workout is protected either way). */
    fun hasNotificationPermission() =
        Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)

    fun hasBluetoothPermission() =
        if (Build.VERSION.SDK_INT >= 31) {
            granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(getApplication(), p) == PackageManager.PERMISSION_GRANTED

    data class Permissions(val bluetooth: Boolean, val mic: Boolean)

    private val _permissions = MutableStateFlow(Permissions(false, false))
    /** Current permission state; refreshed after the permission prompt and whenever the app resumes. */
    val permissions: StateFlow<Permissions> = _permissions

    /** Re-check permissions (e.g. after the user changed them in system settings). */
    fun refreshPermissions() {
        val p = Permissions(hasBluetoothPermission(), hasMicPermission())
        val newlyBluetooth = p.bluetooth && !_permissions.value.bluetooth
        _permissions.value = p
        hr.canConnect = p.bluetooth
        if (newlyBluetooth) hr.autoConnect()
    }

    fun onPermissionsResult() = refreshPermissions()

    // ---------- Mic test (Settings screen) ----------

    fun startMicTest(): Boolean {
        if (_ui.value.phase != Phase.IDLE || !hasMicPermission()) return false
        detector.sensitivity = _settings.value.sensitivity
        _testCount.value = 0
        if (!detector.start()) return false
        detector.armed = true
        _testing.value = true
        return true
    }

    fun stopMicTest() {
        if (!_testing.value) return
        detector.armed = false
        detector.stop()
        _testing.value = false
    }

    // ---------- Workout flow ----------

    /** Start the fixed baseline test (same workout every time, for fair recovery comparisons). */
    fun startBaseline() {
        val s = _settings.value
        startWorkout(
            WorkoutConfig(
                sets = s.baselineSets,
                targetReps = s.baselineReps,
                restSec = _config.value.restSec,
                ballWeightLbs = s.baselineBallLbs,
                autoCount = _config.value.autoCount,
                restUntilTarget = s.baselineRestUntilTarget,
                minRestSec = s.baselineMinRestSec,
                targetHr = s.baselineTargetHr,
            ),
            baseline = true,
        )
    }

    private var isBaseline = false

    fun startWorkout(config: WorkoutConfig = _config.value, baseline: Boolean = false) {
        stopMicTest()
        cfg = config
        isBaseline = baseline
        ticker?.cancel()
        hrSamples.clear()
        zoneSec.fill(0)
        setHr.clear()
        calories = 0.0
        restStartHr = 0
        restElapsed = 0
        noHrSec = 0
        recovery60 = null
        startMillis = System.currentTimeMillis()
        lastSession = null

        detector.armed = false
        detector.sensitivity = _settings.value.sensitivity
        _micError.value = null
        if (cfg.autoCount) {
            when {
                !hasMicPermission() -> _micError.value = "Microphone permission is off. Tap +1 to count reps."
                !detector.start() -> _micError.value = "Couldn't open the microphone. Tap +1 to count reps."
            }
        }

        _ui.value = WorkoutUi(
            phase = Phase.GET_READY,
            totalSets = cfg.sets,
            targetReps = cfg.targetReps,
            secondsLeft = GET_READY_SEC,
            baseline = baseline,
        )
        speaker.say("Get ready${if (baseline) " for your baseline test" else ""}. ${cfg.sets} sets of ${cfg.targetReps}.", interrupt = true)

        ticker = viewModelScope.launch {
            var next = SystemClock.elapsedRealtime()
            while (isActive) {
                next += 1000
                delay(max(0L, next - SystemClock.elapsedRealtime()))
                tick()
                pushStatus()
            }
        }
        // Keep the workout alive if the app goes to the background (switching apps, screen off).
        WorkoutService.start(
            getApplication(),
            mic = cfg.autoCount && detector.isRunning,
            bluetooth = hasBluetoothPermission(),
            text = statusText(_ui.value),
        )
    }

    /** Ongoing notification text for the foreground service. */
    private fun statusText(u: WorkoutUi): String = when (u.phase) {
        Phase.GET_READY -> "Get ready · ${u.secondsLeft} s"
        Phase.WORK -> "Set ${u.setNumber} of ${u.totalSets} · ${u.reps}/${u.targetReps} reps"
        Phase.REST ->
            if (u.holding) "Rest · waiting for ${u.targetHr} bpm · ${mmss(u.sinceSlamSec)} since last slam"
            else "Rest · ${mmss(u.secondsLeft)} · next: set ${u.setNumber + 1} of ${u.totalSets}"
        Phase.RECOVERY -> "Recovery check · stand still"
        else -> "Workout in progress"
    }

    private var lastStatus = ""

    private fun pushStatus() {
        val text = statusText(_ui.value)
        if (text != lastStatus) {
            lastStatus = text
            WorkoutService.update(getApplication(), text)
        }
    }

    private fun stopService() {
        lastStatus = ""
        WorkoutService.stop(getApplication())
    }

    private fun tick() {
        val u = _ui.value
        if (u.phase == Phase.IDLE || u.phase == Phase.DONE) return
        val s = _settings.value
        val bpm = hr.currentBpm()

        hrSamples += bpm ?: 0
        if (bpm != null) {
            zoneSec[Physiology.zone(bpm, s.maxHr)]++
            calories += Physiology.kcalPerSecond(bpm, s)
        }
        val base = u.copy(elapsedSec = u.elapsedSec + 1, calories = calories)

        when (u.phase) {
            Phase.GET_READY -> {
                val left = u.secondsLeft - 1
                _ui.value = base.copy(secondsLeft = left)
                if (left in 1..3) speaker.beepShort()
                if (left <= 0) startSet()
            }

            Phase.WORK -> {
                if (bpm != null) setHr += bpm
                _ui.value = base.copy(setElapsedSec = u.setElapsedSec + 1)
            }

            Phase.REST, Phase.RECOVERY -> {
                restElapsed++
                // Strap dropped out? Stop waiting for the target after NO_HR_GIVE_UP_SEC with no reading,
                // so the rest (or final check) doesn't hold for minutes on a number that can't arrive.
                noHrSec = if (bpm == null) noHrSec + 1 else 0
                if (waitingForTarget && noHrSec >= NO_HR_GIVE_UP_SEC) {
                    waitingForTarget = false
                    speaker.say("Lost heart rate.")
                }
                // Rest-until-target: once the minimum rest is down to the 5-second go countdown, hold
                // there until HR reaches the target (rest = minimum or target, whichever is longer).
                val holding = u.phase == Phase.REST && cfg.restUntilTarget && waitingForTarget &&
                    u.secondsLeft <= GO_AFTER_TARGET_SEC && restElapsed < MAX_RECOVERY_SEC
                val left = if (holding) u.secondsLeft else u.secondsLeft - 1
                val drop = if (bpm != null && restStartHr > 0) restStartHr - bpm else null
                if (restElapsed == 60 && drop != null) {
                    recovery60 = drop
                    if (u.phase == Phase.REST && (left > 5 || holding)) speaker.say("One minute recovery, $drop beats.")
                }
                val sinceSlam = ((SystemClock.elapsedRealtime() - lastSlamAt) / 1000).toInt()
                var next = base.copy(secondsLeft = left, recoveryNow = drop, sinceSlamSec = sinceSlam, holding = holding)
                if (waitingForTarget && bpm != null && bpm <= u.targetHr) {
                    waitingForTarget = false
                    next = next.copy(
                        timeToTarget = sinceSlam,
                        completedSets = setLastSet(next.completedSets) { it.copy(timeToTargetSec = sinceSlam) },
                    )
                    speaker.say("${u.targetHr} in ${spokenTime(sinceSlam)}.")
                    // Rest carries on to the end of the minimum; if it was already held, 5 s to go.
                    if (u.phase == Phase.REST) next = next.copy(holding = false)
                }
                _ui.value = next
                if (u.phase == Phase.REST) {
                    if (left == 10 && !(cfg.restUntilTarget && waitingForTarget)) speaker.say("10 seconds.")
                    if (left in 1..3) speaker.beepShort()
                    if (left <= 0) startSet()
                } else if ((left <= 0 && !waitingForTarget) || restElapsed >= MAX_RECOVERY_SEC) {
                    // The final check runs at least a minute, and on until HR reaches the target (5 min cap).
                    finish()
                }
            }

            else -> Unit
        }
    }

    private fun startSet() {
        waitingForTarget = false
        closeRecoveryForLastSet()
        val u = _ui.value
        val number = u.setNumber + 1
        setHr.clear()
        _ui.value = u.copy(
            phase = Phase.WORK,
            setNumber = number,
            reps = 0,
            setElapsedSec = 0,
            secondsLeft = 0,
            recoveryNow = null,
            sinceSlamSec = 0,
            timeToTarget = null,
            holding = false,
        )
        lastMicRepAt = 0L
        lastTapAt = 0L
        setStartedAt = SystemClock.elapsedRealtime()
        detector.armed = cfg.autoCount && detector.isRunning
        speaker.beepLong()
        speaker.say(if (number == cfg.sets && cfg.sets > 1) "Last set. Go!" else "Set $number. Go!")
    }

    private fun onSlam() {
        if (_testing.value) {
            _testCount.value += 1
            speaker.tick()
            return
        }
        if (_ui.value.phase == Phase.WORK && cfg.autoCount) addRep(1, fromMic = true)
    }

    /** +1 / -1 buttons, and automatic counting. */
    fun addRep(delta: Int, fromMic: Boolean = false) {
        val u = _ui.value
        if (u.phase != Phase.WORK) return
        val reps = max(0, u.reps + delta)
        _ui.value = u.copy(reps = reps)
        if (delta <= 0) return
        if (fromMic) {
            lastMicRepAt = detector.lastHitAt.takeIf { it > 0 } ?: SystemClock.elapsedRealtime()
        } else {
            lastTapAt = SystemClock.elapsedRealtime()
        }

        // No speech while slamming: the phone's own voice makes the mic harder to trigger.
        // Just a boxing-ring bell when 3 reps are left, and the optional click per rep.
        val left = cfg.targetReps - reps
        when {
            left <= 0 -> endSet()
            left == 3 && cfg.targetReps > 3 -> speaker.threeLeft()
            _settings.value.repTick -> speaker.tick()
        }
        pushStatus()
    }

    private fun currentSetResult(u: WorkoutUi) = SetResult(
        number = u.setNumber,
        reps = u.reps,
        durationSec = u.setElapsedSec,
        avgHr = if (setHr.isEmpty()) 0 else setHr.average().roundToInt(),
        peakHr = setHr.maxOrNull() ?: 0,
    )

    /** Finish the current set (target reached or "End set" tapped). */
    fun endSet() {
        val u = _ui.value
        if (u.phase != Phase.WORK) return
        detector.armed = false
        val now = SystemClock.elapsedRealtime()
        val target = targetHr()
        val bpmNow = hr.currentBpm()
        restStartHr = max(bpmNow ?: 0, setHr.takeLast(3).maxOrNull() ?: 0)
        restElapsed = 0
        noHrSec = 0
        recovery60 = null
        // Clock starts at the last slam (or now, if no reps were counted).
        lastSlamAt = lastSlamTime(now)
        val sinceSlam = ((now - lastSlamAt) / 1000).toInt()
        // Already at or below the target? Then it took no time. No HR at all? Can't measure.
        val already = bpmNow != null && bpmNow <= target
        waitingForTarget = !already && (bpmNow != null || hr.state.value == HeartRateManager.State.CONNECTED)
        val result = currentSetResult(u).copy(targetHr = target, timeToTargetSec = if (already) sinceSlam else null)
        val sets = u.completedSets + result
        val common = u.copy(
            completedSets = sets,
            recoveryNow = null,
            sinceSlamSec = sinceSlam,
            targetHr = target,
            timeToTarget = result.timeToTargetSec,
        )
        speaker.beepLong()
        if (u.setNumber >= cfg.sets) {
            _ui.value = common.copy(phase = Phase.RECOVERY, secondsLeft = 60)
            speaker.say("Workout complete. Stand still for the recovery check.")
        } else {
            // Rest until target: the minimum rest counts down, then holds if HR isn't there yet.
            val restLen = if (cfg.restUntilTarget) cfg.minRestSec else cfg.restSec
            _ui.value = common.copy(phase = Phase.REST, secondsLeft = restLen, holding = false)
            speaker.say("Set ${u.setNumber} done. ${u.reps} reps. Rest.")
        }
    }

    /** This workout's recovery target: the baseline's own, or the training target. */
    private fun targetHr(): Int = if (cfg.targetHr > 0) cfg.targetHr else _settings.value.recoveryTargetHr

    fun skipRest() {
        when (_ui.value.phase) {
            Phase.REST -> startSet()
            Phase.RECOVERY -> finish()
            Phase.GET_READY -> startSet()
            else -> Unit
        }
    }

    /** Effort rating for a saved workout (summary screen). Returns the updated workout. */
    fun rateSession(session: Session, value: Int): Session {
        val updated = session.copy(rpe = value.coerceIn(1, 10))
        sessionStore.save(updated)
        _sessions.value = sessionStore.load()
        if (lastSession?.id == updated.id) lastSession = updated
        return updated
    }

    fun addRestTime(sec: Int) {
        val u = _ui.value
        if (u.phase == Phase.REST) _ui.value = u.copy(secondsLeft = u.secondsLeft + sec)
    }

    /**
     * The moment of the set's last slam, which starts the recovery clock.
     *
     * - Normally the last rep the mic counted.
     * - If the mic heard a slam it didn't count (the final slam missed, then fixed with +1 or
     *   "End set now"), that slam is used instead, but only if it came within [MISSED_SLAM_WINDOW_MS]
     *   of the last counted one, so setting the ball down afterwards doesn't count.
     * - With mic counting off, the last +1 tap is the best available.
     * - With nothing at all, the moment the set ended.
     */
    private fun lastSlamTime(now: Long): Long {
        val micOn = cfg.autoCount && detector.isRunning
        val counted = if (micOn) lastMicRepAt.takeIf { it > 0 } ?: lastTapAt else lastTapAt
        val heard = detector.lastHitAt.takeIf { micOn && it > setStartedAt && it <= now }
        return when {
            heard != null && counted == 0L -> heard
            heard != null && heard > counted && heard - counted <= MISSED_SLAM_WINDOW_MS -> heard
            counted > 0L -> counted
            else -> now
        }
    }

    private fun setLastSet(sets: List<SetResult>, change: (SetResult) -> SetResult): List<SetResult> =
        if (sets.isEmpty()) sets else sets.dropLast(1) + change(sets.last())

    private fun spokenTime(sec: Int): String =
        if (sec < 60) "$sec seconds" else if (sec % 60 == 0) "${sec / 60} minutes".replace("1 minutes", "1 minute")
        else "${sec / 60} ${if (sec / 60 == 1) "minute" else "minutes"} ${sec % 60}"

    /** Fill in heart rate recovery for the set that just ended. */
    private fun closeRecoveryForLastSet() {
        val u = _ui.value
        val last = u.completedSets.lastOrNull()
        if (restElapsed > 0 && last != null && last.recoveryBpm == null) {
            val bpm = hr.currentBpm()
            val rec = recovery60 ?: if (bpm != null && restStartHr > 0) restStartHr - bpm else null
            val recSec = if (recovery60 != null) 60 else restElapsed
            val updated = last.copy(recoveryBpm = rec, recoverySec = if (rec != null) recSec else 0)
            _ui.value = u.copy(completedSets = u.completedSets.dropLast(1) + updated)
        }
        restElapsed = 0
        recovery60 = null
        restStartHr = 0
    }

    /** "End workout" button. Saves whatever was done, or discards if nothing was. */
    fun endWorkout() {
        val u = _ui.value
        if (u.phase == Phase.WORK && u.reps > 0) {
            _ui.value = u.copy(completedSets = u.completedSets + currentSetResult(u))
        }
        if (_ui.value.completedSets.isEmpty()) cancel() else finish()
    }

    private fun cancel() {
        ticker?.cancel()
        stopService()
        detector.armed = false
        detector.stop()
        _ui.value = WorkoutUi()
        speaker.say("Workout cancelled.", interrupt = true)
    }

    private fun finish() {
        closeRecoveryForLastSet()
        ticker?.cancel()
        stopService()
        detector.armed = false
        detector.stop()
        val u = _ui.value
        val valid = hrSamples.filter { it > 0 }
        val session = Session(
            id = startMillis,
            startMillis = startMillis,
            durationSec = u.elapsedSec,
            ballWeightLbs = cfg.ballWeightLbs,
            targetReps = cfg.targetReps,
            sets = u.completedSets,
            avgHr = if (valid.isEmpty()) 0 else valid.average().roundToInt(),
            maxHr = valid.maxOrNull() ?: 0,
            calories = calories,
            zoneSec = zoneSec.toList(),
            hrSamples = hrSamples.toList(),
            baseline = isBaseline,
            plannedSets = cfg.sets,
            restUntilTarget = cfg.restUntilTarget,
            minRestSec = if (cfg.restUntilTarget) cfg.minRestSec else 0,
        )
        sessionStore.save(session)
        _sessions.value = sessionStore.load()
        lastSession = session
        _ui.value = u.copy(phase = Phase.DONE)

        val target = targetHr()
        val avgToTarget = session.avgTimeToTarget(target)
        val cal = if (valid.isEmpty()) "" else " ${calories.roundToInt()} calories."
        val rec = if (avgToTarget != null) " Average time to $target, ${spokenTime(avgToTarget.roundToInt())}." else ""
        waitingForTarget = false
        speaker.say("Done. ${session.totalReps} slams in ${session.sets.size} sets.$rec$cal How hard was it? Rate it on screen.", interrupt = true)
    }

    /** Called once the summary screen is showing. */
    fun acknowledgeDone() {
        if (_ui.value.phase == Phase.DONE) _ui.value = WorkoutUi()
    }

    /** Mark or unmark a saved workout as a baseline test. Returns the updated workout. */
    fun setBaseline(session: Session, value: Boolean): Session {
        val updated = session.copy(baseline = value)
        sessionStore.save(updated)
        _sessions.value = sessionStore.load()
        if (lastSession?.id == updated.id) lastSession = updated
        return updated
    }

    /**
     * Writes all workouts to a CSV file (one row per set) in the app's cache and returns it,
     * ready to share. Older exports are removed so the cache doesn't grow.
     */
    fun exportCsvFile(): File {
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return File(dir, "slam-log-workouts-$stamp.csv").apply { writeText(buildCsv(_sessions.value)) }
    }

    fun setLocked(session: Session, value: Boolean): Session {
        val updated = session.copy(locked = value)
        sessionStore.save(updated)
        _sessions.value = sessionStore.load()
        if (lastSession?.id == updated.id) lastSession = updated
        return updated
    }

    /** Deletes a workout, unless it's locked. */
    fun deleteSession(id: Long) {
        if (_sessions.value.firstOrNull { it.id == id }?.isLocked == true) return
        sessionStore.delete(id)
        _sessions.value = sessionStore.load()
    }

    override fun onCleared() {
        ticker?.cancel()
        stopService()
        detector.stop()
        hr.release()
        speaker.shutdown()
    }

    companion object {
        private const val GET_READY_SEC = 10
        private const val MAX_RECOVERY_SEC = 300
        private const val GO_AFTER_TARGET_SEC = 5
        private const val NO_HR_GIVE_UP_SEC = 15
        /** A missed final slam comes within a few seconds of the one before it. */
        private const val MISSED_SLAM_WINDOW_MS = 4_000L
    }
}
