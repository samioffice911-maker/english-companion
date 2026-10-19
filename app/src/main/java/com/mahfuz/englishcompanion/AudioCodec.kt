package com.mahfuz.englishcompanion

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Audio packed for upload: bytes + its type. */
class EncodedAudio(val bytes: ByteArray, val mime: String)

/**
 * Turns raw microphone audio into a small file for upload.
 * AAC is about 10x smaller than WAV, which matters a lot on slow internet.
 */
object AudioCodec {
    private const val SAMPLE_RATE = 16000
    private const val BIT_RATE = 24000          // 24 kbps: clear speech, ~3 KB per second

    fun encode(pcm: ByteArray): EncodedAudio {
        val aac = try { aac(pcm) } catch (e: Exception) { null }
        return if (aac != null && aac.size > 200) EncodedAudio(aac, "audio/aac")
        else EncodedAudio(wav(pcm), "audio/wav")
    }

    private fun aac(pcm: ByteArray): ByteArray? {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val out = ByteArrayOutputStream(pcm.size / 8)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var offset = 0
            var inputDone = false
            var guard = 0
            while (guard++ < 20000) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx) ?: return null
                        buf.clear()
                        val n = minOf(buf.remaining(), pcm.size - offset)
                        if (n > 0) buf.put(pcm, offset, n)
                        val pts = (offset / 2).toLong() * 1_000_000L / SAMPLE_RATE
                        offset += maxOf(n, 0)
                        inputDone = offset >= pcm.size
                        codec.queueInputBuffer(
                            inIdx, 0, maxOf(n, 0), pts,
                            if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        )
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (!isConfig && info.size > 0) {
                        val ob = codec.getOutputBuffer(outIdx) ?: return null
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val frame = ByteArray(info.size)
                        ob.get(frame)
                        out.write(adtsHeader(info.size + 7))
                        out.write(frame)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
            return out.toByteArray()
        } finally {
            try { codec.stop() } catch (_: Exception) { }
            codec.release()
        }
    }

    /** 7-byte ADTS header so each AAC frame is self-describing (AAC-LC, 16 kHz, mono). */
    private fun adtsHeader(packetLen: Int): ByteArray {
        val profile = 2      // AAC LC
        val freqIdx = 8      // 16000 Hz
        val chanCfg = 1      // mono
        return byteArrayOf(
            0xFF.toByte(),
            0xF9.toByte(),
            (((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte(),
            (((chanCfg and 3) shl 6) + (packetLen shr 11)).toByte(),
            ((packetLen and 0x7FF) shr 3).toByte(),
            (((packetLen and 7) shl 5) + 0x1F).toByte(),
            0xFC.toByte()
        )
    }

    fun wav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray()); header.put("fmt ".toByteArray())
        header.putInt(16); header.putShort(1); header.putShort(1)
        header.putInt(SAMPLE_RATE); header.putInt(SAMPLE_RATE * 2)
        header.putShort(2); header.putShort(16)
        header.put("data".toByteArray()); header.putInt(pcm.size)
        return header.array() + pcm
    }
}
