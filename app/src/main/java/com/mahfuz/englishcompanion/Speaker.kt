package com.mahfuz.englishcompanion

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Speaks the AI's replies through the earbuds (or phone speaker). */
class Speaker(context: Context) {
    private var ready = false
    private val counter = AtomicInteger(0)
    private var pending: String? = null
    private var rate = 0.95f

    @Volatile var isSpeaking = false
        private set
    @Volatile var lastSpokeAt = 0L
        private set

    var lastText: String = ""
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            configure()
            pending?.let { speak(it) }
            pending = null
        } else {
            Bus.post(Kind.ERROR, "Text-to-speech is not available on this phone.")
        }
    }

    private fun configure() {
        val r = tts.setLanguage(Locale.US)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        applyAttributes()
        tts.setSpeechRate(rate)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                DebugLog.event("tts_start", "call_route" to callRoute)
            }
            override fun onDone(utteranceId: String?) { finished(); DebugLog.event("tts_done") }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { finished() }
            override fun onError(utteranceId: String?, errorCode: Int) { finished() }
        })
    }

    private fun finished() {
        isSpeaking = false
        lastSpokeAt = System.currentTimeMillis()
    }

    private var callRoute = false

    /** When the earbud mic is in use, speech must go through the "call" audio path to reach the earbuds. */
    fun setCallRoute(call: Boolean) {
        callRoute = call
        if (ready) applyAttributes()
    }

    private fun applyAttributes() {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (callRoute) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    fun setRate(r: Float) {
        rate = r
        if (ready) tts.setSpeechRate(r)
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        lastText = text
        if (!ready) { pending = text; return }
        isSpeaking = true
        tts.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), "u${counter.incrementAndGet()}")
    }

    fun repeatLast() {
        if (lastText.isNotBlank()) speak(lastText)
    }

    fun stop() {
        if (ready) tts.stop()
        finished()
    }

    /** The user started talking over the AI: stop speaking straight away. */
    fun interrupt() {
        if (!isSpeaking) return
        DebugLog.event("barge_in", "was_saying" to DebugLog.text(lastText))
        stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
