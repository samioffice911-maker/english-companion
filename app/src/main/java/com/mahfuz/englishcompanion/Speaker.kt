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
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setSpeechRate(rate)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { isSpeaking = true }
            override fun onDone(utteranceId: String?) { finished() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { finished() }
            override fun onError(utteranceId: String?, errorCode: Int) { finished() }
        })
    }

    private fun finished() {
        isSpeaking = false
        lastSpokeAt = System.currentTimeMillis()
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

    fun shutdown() {
        tts.shutdown()
    }
}
