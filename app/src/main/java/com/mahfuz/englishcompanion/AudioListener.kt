package com.mahfuz.englishcompanion

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Listens to the microphone all the time, notices when the user starts and stops
 * talking, and hands each finished sentence (as WAV audio) to [onUtterance].
 * Audio is kept only in memory and thrown away after it is sent.
 */
class AudioListener(
    private val sensitivity: () -> Int,
    private val pauseMs: () -> Int,
    private val shouldIgnore: () -> Boolean,
    private val onUtterance: (wav: ByteArray, seconds: Float) -> Unit
) {
    private val sampleRate = 16000
    private val frameSamples = 320            // 20 ms
    private val maxSeconds = 30
    private val preRollFrames = 15            // keep 300 ms before speech starts

    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
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
                maxOf(minBuf, frameSamples * 2 * 10)
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
        var noiseFloor = 200.0
        var inSpeech = false
        var loudFrames = 0
        var silentMs = 0
        var speechMs = 0

        recorder.startRecording()
        try {
            while (running) {
                val n = recorder.read(frame, 0, frameSamples)
                if (n <= 0) continue

                // While the companion itself is talking, don't listen (avoids hearing itself)
                if (shouldIgnore()) {
                    if (inSpeech) { speech.reset(); inSpeech = false }
                    preRoll.clear(); loudFrames = 0
                    continue
                }

                val rms = rms(frame, n)
                val minLevel = when (sensitivity()) { 0 -> 1100.0; 2 -> 300.0; else -> 600.0 }
                val threshold = maxOf(minLevel, noiseFloor * 3.0)
                val loud = rms > threshold

                if (!inSpeech) {
                    // Slowly learn background noise level
                    noiseFloor = noiseFloor * 0.97 + rms * 0.03
                    preRoll.addLast(frame.copyOf(n))
                    if (preRoll.size > preRollFrames) preRoll.removeFirst()
                    loudFrames = if (loud) loudFrames + 1 else 0
                    if (loudFrames >= 4) {          // 80 ms of voice -> speech started
                        inSpeech = true
                        speech.reset()
                        preRoll.forEach { writeShorts(speech, it, it.size) }
                        preRoll.clear()
                        silentMs = 0
                        speechMs = 80
                        Bus.status("Hearing you…")
                    }
                } else {
                    writeShorts(speech, frame, n)
                    if (loud) { silentMs = 0; speechMs += 20 } else silentMs += 20
                    val totalSec = speech.size() / (sampleRate * 2f)
                    if (silentMs >= pauseMs() || totalSec >= maxSeconds) {
                        inSpeech = false
                        loudFrames = 0
                        if (speechMs >= 400) {
                            val pcm = speech.toByteArray()
                            onUtterance(wav(pcm), pcm.size / (sampleRate * 2f))
                        } else {
                            Bus.status("Listening…")
                        }
                        speech.reset()
                    }
                }
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) { }
            recorder.release()
        }
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

    private fun wav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray()); header.put("fmt ".toByteArray())
        header.putInt(16); header.putShort(1); header.putShort(1)
        header.putInt(sampleRate); header.putInt(sampleRate * 2)
        header.putShort(2); header.putShort(16)
        header.put("data".toByteArray()); header.putInt(pcm.size)
        return header.array() + pcm
    }
}
