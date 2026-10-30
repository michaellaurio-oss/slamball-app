package com.laurio.slamlog.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
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
    private val pending = AtomicInteger()

    @Volatile private var ready = false
    @Volatile private var busyUntil = 0L

    @Volatile var voiceEnabled = true

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.US
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finished()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()
            override fun onError(utteranceId: String?, errorCode: Int) = finished()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished()
        })
        ready = true
    }

    private fun finished() {
        if (pending.decrementAndGet() < 0) pending.set(0)
        markBusy(300)
    }

    fun say(text: String, interrupt: Boolean = false) {
        if (!voiceEnabled || !ready) return
        if (interrupt) pending.set(0)
        pending.incrementAndGet()
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (tts.speak(text, mode, null, "u${ids.incrementAndGet()}") != TextToSpeech.SUCCESS) {
            pending.decrementAndGet()
        }
    }

    /** Countdown beep. */
    fun beepShort() = play(ToneGenerator.TONE_DTMF_1, 150)

    /** Start / end of a set. */
    fun beepLong() = play(ToneGenerator.TONE_DTMF_9, 500)

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
    fun isBusy(): Boolean = pending.get() > 0 || SystemClock.elapsedRealtime() < busyUntil

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        tone?.release()
    }
}
