package com.laurio.slamlog.audio

import android.content.Context
import android.media.AudioManager
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

    /** Short double beep: 3 reps left in the set. */
    fun threeLeft() = play(ToneGenerator.TONE_PROP_BEEP2, 200)

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
    }
}
