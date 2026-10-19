package com.mahfuz.englishcompanion

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Lets the screen's mic button control the listener: start now / send now. */
object MicControl {
    @Volatile var forceStart = false
    @Volatile var forceEnd = false
}

/**
 * Listens to the microphone, notices when the user starts and stops talking,
 * and hands each finished sentence (as WAV audio) to [onUtterance].
 * Audio is kept only in memory and thrown away after it is sent.
 *
 * Voice detection adapts to background noise (fans, traffic) so it doesn't get stuck "listening".
 */
class AudioListener(
    private val sensitivity: () -> Int,
    private val pauseMs: () -> Int,
    private val shouldIgnore: () -> Boolean,
    private val onUtterance: (pcm: ByteArray, seconds: Float) -> Unit
) {
    private val sampleRate = 16000
    private val frameSamples = 320            // 20 ms
    private val frameMs = 20
    private val maxMs = 12_000                // long clips are slow -> send every 12 s at most
    private val preRollFrames = 15            // keep 300 ms before speech starts

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "mic").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(1500)
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, frameSamples * 2 * 10)
            )
        } catch (e: Exception) {
            Bus.post(Kind.ERROR, "Cannot open microphone: ${e.message}")
            running = false
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Bus.post(Kind.ERROR, "Microphone is busy or not allowed.")
            recorder.release()
            running = false
            return
        }

        val frame = ShortArray(frameSamples)
        val preRoll = ArrayDeque<ShortArray>()
        val speech = ByteArrayOutputStream()
        var noiseFloor = 150.0
        var speechLevel = 0.0
        var inSpeech = false
        var forced = false
        var recentLoud = 0                     // loud frames in the last 5 frames (approx.)
        var silentMs = 0
        var voicedMs = 0
        var totalMs = 0
        var frameCount = 0

        recorder.startRecording()
        try {
            while (running) {
                val n = recorder.read(frame, 0, frameSamples)
                if (n <= 0) continue
                frameCount++

                val rms = rms(frame, n)
                if (frameCount % 4 == 0) Bus.level(min(1f, (rms / 2500.0).toFloat()))

                // While the companion itself is talking, don't listen (avoids hearing itself)
                if (shouldIgnore()) {
                    if (inSpeech) { speech.reset(); inSpeech = false; Bus.capturing(false) }
                    preRoll.clear(); recentLoud = 0
                    MicControl.forceStart = false; MicControl.forceEnd = false
                    continue
                }

                val minLevel = when (sensitivity()) { 0 -> 900.0; 2 -> 250.0; else -> 450.0 }

                if (!inSpeech) {
                    // Learn the background noise: drop quickly, rise slowly
                    noiseFloor = if (rms < noiseFloor) noiseFloor * 0.9 + rms * 0.1
                                 else noiseFloor * 0.995 + rms * 0.005
                    noiseFloor = max(noiseFloor, 40.0)

                    preRoll.addLast(frame.copyOf(n))
                    if (preRoll.size > preRollFrames) preRoll.removeFirst()

                    val startThreshold = max(minLevel, noiseFloor * 3.0)
                    recentLoud = if (rms > startThreshold) min(recentLoud + 1, 5) else max(recentLoud - 1, 0)

                    val tapped = MicControl.forceStart
                    if (recentLoud >= 3 || tapped) {
                        MicControl.forceStart = false
                        MicControl.forceEnd = false
                        inSpeech = true
                        forced = tapped
                        speech.reset()
                        preRoll.forEach { writeShorts(speech, it, it.size) }
                        preRoll.clear()
                        silentMs = 0
                        voicedMs = if (tapped) 0 else 60
                        totalMs = 0
                        speechLevel = max(rms, minLevel)
                        Bus.capturing(true)
                        Bus.status("Hearing you… (tap mic when done)")
                    }
                } else {
                    writeShorts(speech, frame, n)
                    totalMs += frameMs

                    // End threshold follows both the noise and how loud the user is speaking
                    val endThreshold = maxOf(minLevel * 0.5, noiseFloor * 2.0, speechLevel * 0.15)
                    if (rms > endThreshold) {
                        silentMs = 0
                        voicedMs += frameMs
                        speechLevel = speechLevel * 0.95 + rms * 0.05
                    } else {
                        silentMs += frameMs
                    }

                    val tapEnd = MicControl.forceEnd
                    // When the user tapped to start, wait a little longer for silence
                    val pause = if (forced) pauseMs() + 600 else pauseMs()
                    if (tapEnd || silentMs >= pause || totalMs >= maxMs) {
                        MicControl.forceEnd = false
                        inSpeech = false
                        recentLoud = 0
                        Bus.capturing(false)
                        if (voicedMs >= 300 || (tapEnd && totalMs >= 600)) {
                            val pcm = trimTrailingSilence(speech.toByteArray(), if (tapEnd) 0 else silentMs)
                            onUtterance(pcm, pcm.size / (sampleRate * 2f))
                        } else {
                            Bus.status("Listening…")
                        }
                        speech.reset()
                        // a long, continuous speaker: keep listening straight away
                        if (totalMs >= maxMs && !tapEnd) MicControl.forceStart = true
                    }
                }
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) { }
            recorder.release()
            Bus.level(0f)
            Bus.capturing(false)
        }
    }

    /** Cut most of the silence at the end so less audio is uploaded (keep 250 ms). */
    private fun trimTrailingSilence(pcm: ByteArray, silentMs: Int): ByteArray {
        val cutMs = silentMs - 250
        if (cutMs <= 0) return pcm
        val cutBytes = (cutMs * sampleRate / 1000) * 2
        val keep = pcm.size - cutBytes
        return if (keep > sampleRate) pcm.copyOf(keep) else pcm
    }

    private fun rms(buf: ShortArray, n: Int): Double {
        var sum = 0.0
        for (i in 0 until n) { val v = buf[i].toDouble(); sum += v * v }
        return sqrt(sum / n)
    }

    private fun writeShorts(out: ByteArrayOutputStream, data: ShortArray, n: Int) {
        val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) bb.putShort(data[i])
        out.write(bb.array(), 0, n * 2)
    }
}
