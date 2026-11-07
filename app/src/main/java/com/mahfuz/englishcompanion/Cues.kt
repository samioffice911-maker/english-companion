package com.mahfuz.englishcompanion

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short sounds + vibrations so you know what the companion is doing without looking at the phone.
 *   heard   – soft tick when it notices you started talking (only with earbuds)
 *   sent    – two quick beeps when your sentence is sent
 *   paused / resumed / started / stopped / error
 */
object Cues {
    enum class Cue { HEARD, SENT, PAUSED, RESUMED, STARTED, STOPPED, ERROR }

    private lateinit var ctx: Context
    @Volatile var callRoute = false          // earbud mic in use -> play tones on the call stream
    @Volatile var headphones = false         // tones can't be picked up by the mic
    /** Until this time the mic should ignore sound (a tone is playing out of the phone speaker). */
    @Volatile var busyUntil = 0L
        private set

    private var tone: ToneGenerator? = null
    private var toneStream = -1

    fun init(context: Context) { ctx = context.applicationContext }

    fun play(cue: Cue) {
        if (!::ctx.isInitialized) return
        val p = Engine.prefs
        if (p.vibration) vibrate(cue)
        if (!p.soundCues) return
        if (cue == Cue.HEARD && !headphones) return   // would be recorded by the phone mic
        try {
            val stream = if (callRoute) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
            if (tone == null || toneStream != stream) {
                tone?.release()
                tone = ToneGenerator(stream, 55)
                toneStream = stream
            }
            val (type, ms) = when (cue) {
                Cue.HEARD -> ToneGenerator.TONE_PROP_BEEP to 60
                Cue.SENT -> ToneGenerator.TONE_PROP_BEEP2 to 180
                Cue.PAUSED -> ToneGenerator.TONE_PROP_NACK to 300
                Cue.RESUMED -> ToneGenerator.TONE_PROP_ACK to 250
                Cue.STARTED -> ToneGenerator.TONE_PROP_PROMPT to 300
                Cue.STOPPED -> ToneGenerator.TONE_PROP_NACK to 300
                Cue.ERROR -> ToneGenerator.TONE_SUP_ERROR to 350
            }
            if (!headphones) busyUntil = System.currentTimeMillis() + ms + 250
            tone?.startTone(type, ms)
            DebugLog.event("cue", "cue" to cue.name)
        } catch (_: Exception) { }
    }

    private fun vibrate(cue: Cue) {
        val pattern: LongArray = when (cue) {
            Cue.HEARD -> return                         // too frequent to vibrate
            Cue.SENT -> longArrayOf(0, 25)
            Cue.PAUSED, Cue.STOPPED -> longArrayOf(0, 40, 80, 40)
            Cue.RESUMED, Cue.STARTED -> longArrayOf(0, 60)
            Cue.ERROR -> longArrayOf(0, 120, 80, 120)
        }
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) { }
    }

    fun release() {
        tone?.release(); tone = null; toneStream = -1
    }
}
