package com.mahfuz.englishcompanion

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Diagnostic log ("flight recorder"). One JSON object per line, kept only on the phone.
 * The user exports it as a .zip and uploads it so problems can be analysed.
 */
object DebugLog {
    private lateinit var ctx: Context
    private val writer = Executors.newSingleThreadExecutor()
    private const val MAX_BYTES = 3_000_000L
    private var clipCounter = 0

    val enabled get() = ::ctx.isInitialized && Engine.prefs.debugLog
    private val withText get() = Engine.prefs.logTranscripts

    fun init(context: Context) { ctx = context.applicationContext }

    private fun logFile() = File(ctx.filesDir, "debug.jsonl")
    private fun oldFile() = File(ctx.filesDir, "debug.old.jsonl")
    private fun clipDir() = File(ctx.filesDir, "clips").apply { mkdirs() }

    /** Log an event. [fields] are pairs of name -> value. */
    fun event(type: String, vararg fields: Pair<String, Any?>) {
        if (!enabled) return
        val o = JSONObject()
        o.put("t", System.currentTimeMillis())
        o.put("time", SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()))
        o.put("ev", type)
        for ((k, v) in fields) o.put(k, v ?: JSONObject.NULL)
        val line = o.toString() + "\n"
        writer.execute {
            try {
                val f = logFile()
                if (f.length() > MAX_BYTES) { oldFile().delete(); f.renameTo(oldFile()) }
                FileOutputStream(logFile(), true).use { it.write(line.toByteArray()) }
            } catch (_: Exception) { }
        }
    }

    /** Text the user said: only logged if they allowed transcripts. */
    fun text(s: String?): String? = if (withText) s else s?.let { "[${it.length} chars hidden]" }

    /** Optionally keep the audio clip that was sent (off by default). Returns the clip name. */
    fun saveClip(audio: EncodedAudio): String? {
        if (!enabled || !Engine.prefs.saveClips) return null
        return try {
            val dir = clipDir()
            val ext = if (audio.mime == "audio/aac") "aac" else "wav"
            val name = "clip_${SimpleDateFormat("HHmmss", Locale.US).format(Date())}_${clipCounter++}.$ext"
            File(dir, name).writeBytes(audio.bytes)
            // keep only the newest 30 clips
            dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(30)?.forEach { it.delete() }
            name
        } catch (e: Exception) { null }
    }

    fun network(): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
            val kind = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                else -> "other"
            }
            "$kind ↓${caps.linkDownstreamBandwidthKbps}kbps ↑${caps.linkUpstreamBandwidthKbps}kbps"
        } catch (e: Exception) { "unknown" }
    }

    fun sizeText(): String {
        if (!::ctx.isInitialized) return "0 KB"
        val bytes = logFile().length() + oldFile().length()
        val clips = clipDir().listFiles()?.size ?: 0
        return "${bytes / 1024} KB" + if (clips > 0) " · $clips audio clips" else ""
    }

    fun clear() {
        writer.execute {
            logFile().delete(); oldFile().delete()
            clipDir().listFiles()?.forEach { it.delete() }
        }
    }

    /** Header with phone + app info, written at the top of every export. */
    private fun summary(): String {
        val p = Engine.prefs
        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "?" }
        return JSONObject()
            .put("app_version", version)
            .put("phone", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("android", Build.VERSION.RELEASE)
            .put("exported", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            .put("mode", p.mode)
            .put("professional", p.professional)
            .put("model", p.model)
            .put("sensitivity", p.sensitivity)
            .put("pause_ms", p.pauseMs)
            .put("earbud_mic", p.useEarbudMic)
            .put("barge_in", p.bargeIn)
            .put("transcripts", p.logTranscripts)
            .put("network_now", network())
            .toString(2)
    }

    /** Builds the zip. Returns the file in the app's cache, ready to share. */
    fun buildZip(): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val out = File(ctx.cacheDir, "english-companion-log-$stamp.zip")
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry("summary.json")); zip.write(summary().toByteArray()); zip.closeEntry()
            for (f in listOf(oldFile(), logFile())) {
                if (f.exists()) { zip.putNextEntry(ZipEntry(f.name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
            }
            clipDir().listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry("clips/${f.name}")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
        return out
    }

    /** Saves the zip into the phone's Downloads folder (Android 10+). Returns a message for the user. */
    fun saveToDownloads(zip: File): String {
        if (Build.VERSION.SDK_INT < 29) return "Use Share instead (needs Android 10+ to save to Downloads)."
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, zip.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "Couldn't save the file."
            ctx.contentResolver.openOutputStream(uri)?.use { os -> zip.inputStream().use { it.copyTo(os) } }
            "Saved to Downloads: ${zip.name}"
        } catch (e: Exception) { "Couldn't save: ${e.message}" }
    }
}
