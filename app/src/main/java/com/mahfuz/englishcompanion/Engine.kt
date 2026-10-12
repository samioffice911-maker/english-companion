package com.mahfuz.englishcompanion

import android.content.Context
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/**
 * The brain: sends what the user said to Gemini, decides what to say back,
 * updates mistake memory and progress. Shared by the screen and the background service.
 */
object Engine {
    private lateinit var appContext: Context
    lateinit var prefs: Prefs
        private set
    lateinit var speaker: Speaker
        private set

    private val client = GeminiClient()
    private val worker = Executors.newSingleThreadExecutor()
    private val history = ArrayList<String>()   // short text log sent to the AI for context
    @Volatile var paused = false
    private val busyCount = java.util.concurrent.atomic.AtomicInteger(0)
    var onStopRequested: (() -> Unit)? = null

    val isBusy: Boolean get() = busyCount.get() > 0

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        prefs = Prefs(appContext)
        speaker = Speaker(appContext)
        speaker.setRate(prefs.speechRate)
        if (prefs.saveHistory) loadHistory()
    }

    fun submitAudio(wav: ByteArray, seconds: Float) {
        submit(seconds) { key, model, prompt -> client.analyzeAudio(key, model, prompt, wav) }
    }

    fun submitText(text: String) {
        Bus.post(Kind.USER, text)
        submit(0f, typed = true) { key, model, prompt -> client.analyzeText(key, model, prompt, text) }
    }

    private fun submit(
        seconds: Float,
        typed: Boolean = false,
        call: (String, String, String) -> AiResult
    ) {
        val key = prefs.apiKey
        if (key.isBlank()) {
            Bus.post(Kind.ERROR, "No API key yet. Tap ⚙ Settings and paste your Gemini API key.")
            speaker.speak("Please add your Gemini API key in settings first.")
            return
        }
        busyCount.incrementAndGet()
        worker.execute {
            Bus.status("Thinking…")
            try {
                val prompt = Prompt.build(prefs.mode, prefs.memorySummary(), historyText(), paused)
                val r = call(key, prefs.model, prompt)
                handle(r, seconds, typed)
            } catch (e: ApiException) {
                val msg = when (e.code) {
                    429 -> "Free limit reached for now. Wait a minute (or try again tomorrow if it keeps happening)."
                    400 -> if (e.message?.contains("API key", true) == true)
                        "Your API key looks wrong. Check it in Settings." else "Request problem: ${e.message}"
                    403 -> "API key not allowed. Check it in Settings. (${e.message})"
                    404 -> "Model '${prefs.model}' not found. Change it in Settings."
                    else -> "AI error ${e.code}: ${e.message}"
                }
                Bus.post(Kind.ERROR, msg)
            } catch (e: java.net.UnknownHostException) {
                Bus.post(Kind.ERROR, "No internet connection.")
            } catch (e: java.net.SocketTimeoutException) {
                Bus.post(Kind.ERROR, "The AI took too long to answer. Try again.")
            } catch (e: Exception) {
                Bus.post(Kind.ERROR, "Error: ${e.message}")
            } finally {
                busyCount.decrementAndGet()
                Bus.status(if (Bus.running) (if (paused) "Paused — say \"continue\"" else "Listening…") else "Ready")
            }
        }
    }

    private fun handle(r: AiResult, seconds: Float, typed: Boolean) {
        if (r.language == "noise" || (r.transcript.isBlank() && !typed)) return

        if (!typed) Bus.post(Kind.USER, r.transcript)

        val hasCorrection = r.corrected.isNotBlank() &&
            normalize(r.corrected) != normalize(r.transcript)
        if (hasCorrection) {
            val label = if (r.language == "english") "✔ Better" else "🇬🇧 In English"
            Bus.post(Kind.CORRECTION, "$label: ${r.corrected}" +
                if (r.mistakeNote.isNotBlank()) "\n   (${r.mistakeNote})" else "")
        }
        if (r.newWord.isNotBlank()) Bus.post(Kind.INFO, "📘 New word: ${r.newWord}")

        // Commands
        when (r.command) {
            "pause" -> { paused = true; Bus.status("Paused — say \"continue\"") }
            "continue" -> { paused = false; Bus.status("Listening…") }
            "slower" -> { prefs.speechRate = prefs.speechRate - 0.15f; speaker.setRate(prefs.speechRate) }
            "faster" -> { prefs.speechRate = prefs.speechRate + 0.15f; speaker.setRate(prefs.speechRate) }
            "repeat" -> speaker.repeatLast()
        }

        if (r.speak && r.reply.isNotBlank()) {
            Bus.post(Kind.AI, r.reply)
            speaker.speak(r.reply)
        }

        if (r.command == "stop") onStopRequested?.invoke()

        // Memory + progress
        if (r.language == "english" && hasCorrection) prefs.recordMistake(r.mistakeType, r.mistakeNote)
        val words = r.transcript.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        prefs.addTurn(r.language, words, seconds, hasCorrection, r.newWord.isNotBlank())

        addHistory("User: ${r.transcript}" +
            (if (hasCorrection) "  [correct form: ${r.corrected}]" else "") +
            (if (r.speak && r.reply.isNotBlank()) "\nYou: ${r.reply}" else "\nYou: (stayed silent)"))
    }

    private fun normalize(s: String) =
        s.lowercase().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim()

    // ---------- short-term conversation history ----------
    @Synchronized
    private fun addHistory(line: String) {
        history.add(line)
        while (history.size > 12) history.removeAt(0)
        if (prefs.saveHistory) saveHistory()
    }

    @Synchronized
    private fun historyText(): String = history.joinToString("\n")

    @Synchronized
    fun clearHistory() {
        history.clear()
        historyFile().delete()
    }

    private fun historyFile() = File(appContext.filesDir, "history.json")

    private fun saveHistory() {
        try {
            val arr = JSONArray()
            history.forEach { arr.put(it) }
            historyFile().writeText(JSONObject().put("h", arr).toString())
        } catch (_: Exception) { }
    }

    private fun loadHistory() {
        try {
            val f = historyFile()
            if (!f.exists()) return
            val arr = JSONObject(f.readText()).getJSONArray("h")
            for (i in 0 until arr.length()) history.add(arr.getString(i))
        } catch (_: Exception) { }
    }
}
