package com.laurio.slamlog.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Counts slams by listening through the microphone for loud, sudden thuds.
 *
 * Every 20 ms it measures how loud the sound is and compares it with the room's
 * background noise (which it keeps learning). A slam is a short burst that is many
 * times louder than the background and above a minimum loudness. After a slam it
 * ignores sound for a moment so one slam isn't counted twice.
 */
class SlamDetector(
    private val onSlam: () -> Unit,
    /** True while the phone itself is beeping or talking, so its own sound isn't counted. */
    private val isSuppressed: () -> Boolean,
) {
    /** 0 = only very loud slams count, 1 = very sensitive. */
    @Volatile var sensitivity = 0.5f

    /** Only report slams when armed (during a set or a mic test). */
    @Volatile var armed = false

    private val _level = MutableStateFlow(0f)
    /** Current loudness 0..1, for the on-screen meter. */
    val level: StateFlow<Float> = _level

    /** Each listening run gets its own flag, so a quick stop/start can't cross wires. */
    @Volatile private var runFlag: AtomicBoolean? = null
    private var thread: Thread? = null

    val isRunning: Boolean get() = runFlag?.get() == true

    @SuppressLint("MissingPermission") // Caller checks RECORD_AUDIO first.
    fun start(): Boolean {
        if (isRunning) return true
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val recorder = try {
            AudioRecord(
                // Voice recognition input has little automatic gain control, so thuds keep their punch.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, FRAME * 2 * 8),
            )
        } catch (e: Exception) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        val flag = AtomicBoolean(true)
        runFlag = flag
        thread = Thread({ loop(recorder, flag) }, "SlamDetector").apply { start() }
        return true
    }

    fun stop() {
        runFlag?.set(false)
        runFlag = null
        thread?.join(500)
        thread = null
        _level.value = 0f
    }

    private fun loop(recorder: AudioRecord, running: AtomicBoolean) {
        val buf = ShortArray(FRAME)
        var noiseFloor = 300.0
        var lastHit = 0L
        try {
            recorder.startRecording()
            while (running.get()) {
                val n = recorder.read(buf, 0, FRAME)
                if (n <= 0) continue

                var peak = 0
                var sumSq = 0.0
                for (i in 0 until n) {
                    val v = abs(buf[i].toInt())
                    if (v > peak) peak = v
                    sumSq += v.toDouble() * v
                }
                val rms = sqrt(sumSq / n)
                _level.value = (peak / 32768f).coerceIn(0f, 1f)

                val s = sensitivity.coerceIn(0f, 1f)
                var rmsThreshold = noiseFloor * (14.0 - 10.0 * s) // 14x (low) .. 4x (high) the background
                var peakThreshold = 14_000.0 - 11_000.0 * s       // 14000 .. 3000 out of 32768
                if (isSuppressed()) {
                    // The phone is making sound: require a much louder hit instead of going deaf.
                    rmsThreshold *= 2.5
                    peakThreshold = min(30_000.0, peakThreshold * 2.0)
                }

                val now = SystemClock.elapsedRealtime()
                val hit = rms > rmsThreshold && peak > peakThreshold
                if (hit && now - lastHit > REFRACTORY_MS) {
                    lastHit = now
                    if (armed) onSlam()
                }
                // Keep learning the background level, but not from slams or their echoes.
                if (!hit && now - lastHit > 400) {
                    noiseFloor = (noiseFloor * 0.98 + rms * 0.02).coerceIn(50.0, 5_000.0)
                }
            }
        } catch (_: Exception) {
            // Mic taken by another app or permission revoked: just stop listening.
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            running.set(false)
            _level.value = 0f
        }
    }

    companion object {
        private const val RATE = 16_000
        private const val FRAME = 320          // 20 ms at 16 kHz
        private const val REFRACTORY_MS = 700L // fastest counted pace: ~85 slams/minute
    }
}
