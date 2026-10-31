package com.laurio.slamlog.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Voice callouts (text-to-speech) and beeps. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    } catch (e: RuntimeException) {
        null
    }
    private val ids = AtomicInteger()
    /** Utterances queued or speaking. Tracked by ID, so a cut-off one can't be counted twice. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var ready = false
    @Volatile private var busyUntil = 0L

    @Volatile var voiceEnabled = true

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.US
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
        })
        ready = true
    }

    private fun finished(id: String?) {
        if (id != null) inFlight.remove(id)
        markBusy(300)
    }

    fun say(text: String, interrupt: Boolean = false) {
        if (!voiceEnabled || !ready) return
        // A flush stops everything queued; their onStop callbacks then just remove their own IDs.
        if (interrupt) inFlight.clear()
        val id = "u${ids.incrementAndGet()}"
        inFlight.add(id)
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (tts.speak(text, mode, null, id) != TextToSpeech.SUCCESS) {
            inFlight.remove(id)
        }
    }

    /** Countdown beep. */
    fun beepShort() = play(ToneGenerator.TONE_DTMF_1, 150)

    /** Start / end of a set. */
    fun beepLong() = play(ToneGenerator.TONE_DTMF_9, 500)

    /**
     * 3 reps left in the set: a boxing-ring bell, three quick strikes ("ding-ding-ding") that ring out.
     * About 0.8 s, kept short because the mic needs a louder hit to count while it rings.
     */
    fun threeLeft() {
        val track = bell ?: return play(ToneGenerator.TONE_PROP_BEEP2, 200)
        markBusy(BELL_MS + 100L)
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    /**
     * The bell, synthesized once. Each strike is a few inharmonic partials (what makes it sound like
     * metal rather than a beep), higher ones dying away faster, plus a tiny click for the hammer hit.
     */
    private val bell: AudioTrack? = runCatching {
        val rate = 44_100
        val total = rate * BELL_MS / 1000
        val mix = DoubleArray(total)
        val f0 = 830.0
        val partials = doubleArrayOf(1.0, 2.0, 2.42, 2.93, 4.07, 5.41)
        val amps = doubleArrayOf(1.0, 0.55, 0.45, 0.3, 0.2, 0.12)
        val decay = doubleArrayOf(0.19, 0.14, 0.12, 0.09, 0.07, 0.05) // seconds to fall to ~37%
        val rnd = java.util.Random(7)
        for (k in 0 until STRIKES) {
            val start = rate * k * STRIKE_GAP_MS / 1000
            for (i in 0 until total - start) {
                val t = i / rate.toDouble()
                var v = 0.0
                for (j in partials.indices) {
                    v += amps[j] * Math.exp(-t / decay[j]) * Math.sin(2 * Math.PI * f0 * partials[j] * t)
                }
                if (i < rate / 200) v += (rnd.nextDouble() * 2 - 1) * 0.6 * (1 - i / (rate / 200.0)) // hammer click
                mix[start + i] += v
            }
        }
        // Fade the very end to silence and normalize to a loud but unclipped level.
        val tail = rate * 60 / 1000
        for (i in 0 until tail) mix[total - 1 - i] *= i / tail.toDouble()
        val peak = mix.maxOf { Math.abs(it) }.coerceAtLeast(1e-9)
        val pcm = ShortArray(total) { (mix[it] / peak * 0.92 * Short.MAX_VALUE).toInt().toShort() }
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    }.getOrNull()

    /** Quiet click when a rep is counted. */
    fun tick() = play(ToneGenerator.TONE_DTMF_5, 50)

    private fun play(type: Int, ms: Int) {
        markBusy(ms + 250L)
        tone?.startTone(type, ms)
    }

    private fun markBusy(ms: Long) {
        busyUntil = max(busyUntil, SystemClock.elapsedRealtime() + ms)
    }

    private fun max(a: Long, b: Long) = if (a > b) a else b

    /** True while the phone is (or just was) making sound. */
    fun isBusy(): Boolean = inFlight.isNotEmpty() || SystemClock.elapsedRealtime() < busyUntil

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        tone?.release()
        bell?.release()
    }

    private companion object {
        const val STRIKES = 3
        const val STRIKE_GAP_MS = 190
        const val BELL_MS = (STRIKES - 1) * STRIKE_GAP_MS + 420
    }
}
