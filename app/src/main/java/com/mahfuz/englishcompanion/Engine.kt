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
    @Volatile var paused = false          // paused by voice ("pause") — still listens for "continue"
    @Volatile private var practiceEndsAt = 0L

    /** Paused by button / earbud / lock screen: microphone ignored completely. */
    @Volatile var micPaused = false
        set(v) {
            field = v
            Bus.paused(v)
            Bus.status(idleStatus())
            onPauseChanged?.invoke()
        }
    var onPauseChanged: (() -> Unit)? = null
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

    fun submitAudio(pcm: ByteArray, seconds: Float) {
        // compress on the worker thread (AAC is ~10x smaller than WAV -> faster on slow internet)
        submit(seconds) { key, model, prompt -> client.analyzeAudio(key, model, prompt, AudioCodec.encode(pcm)) }
    }

    /** Daily 5-minute guided practice: the AI starts a topic and keeps the conversation going. */
    fun startPractice() {
        practiceEndsAt = System.currentTimeMillis() + 5 * 60_000L
        Bus.post(Kind.INFO, "🎯 5-minute practice started. Answer out loud!")
        submit(0f, typed = true, system = true) { key, model, prompt ->
            client.instruct(key, model, prompt,
                "Start a 5-minute speaking practice now. Greet the user in one short sentence, pick an " +
                "everyday topic (daily routine, food, work, study, family, weekend plans, hobbies, travel) " +
                "that suits his weak areas, and ask the first simple open question. speak=true.")
        }
    }

    private fun practiceRule(): String {
        if (practiceEndsAt == 0L) return ""
        val leftMs = practiceEndsAt - System.currentTimeMillis()
        return if (leftMs > 0) {
            "PRACTICE SESSION ACTIVE (about ${(leftMs / 60_000L) + 1} min left) — this overrides the MODE rules: act like a friendly speaking coach. " +
                "Always reply (speak=true) and ask the next short question to keep him talking. " +
                "Gently correct important mistakes by recasting. Steer towards his weak areas."
        } else {
            practiceEndsAt = 0L
            "PRACTICE TIME IS UP: after responding briefly, wrap up the practice with speak=true: " +
                "one thing he did well and one thing to practise next time, in 2 short sentences."
        }
    }

    fun submitText(text: String) {
        Bus.post(Kind.USER, text, "Typed")
        submit(0f, typed = true) { key, model, prompt -> client.analyzeText(key, model, prompt, text) }
    }

    private fun idleStatus() = when {
        !Bus.isRunning -> "Ready"
        micPaused -> "Paused — tap ▶ to resume"
        paused -> "Paused — say \"continue\""
        else -> "Listening…"
    }

    private fun submit(
        seconds: Float,
        typed: Boolean = false,
        system: Boolean = false,
        call: (String, String, String) -> AiResult
    ) {
        val key = prefs.apiKey
        if (key.isBlank()) {
            Bus.post(Kind.ERROR, "No API key yet. Open Settings (⚙ on Home) and paste your Gemini API key.")
            speaker.speak("Please add your Gemini API key in settings first.")
            return
        }
        busyCount.incrementAndGet()
        if (!typed) Bus.status(String.format(java.util.Locale.US, "Understanding %.1fs of speech…", seconds))
        worker.execute {
            val t0 = System.currentTimeMillis()
            if (typed) Bus.status("Thinking…")
            try {
                val prompt = Prompt.build(prefs.mode, prefs.professional, prefs.memorySummary(), historyText(), paused, practiceRule())
                val r = call(key, prefs.model, prompt)
                handle(r, seconds, typed, system, (System.currentTimeMillis() - t0) / 1000f)
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
                Bus.post(Kind.ERROR, "The AI took too long to answer (slow internet?). Try again.")
            } catch (e: Exception) {
                Bus.post(Kind.ERROR, "Error: ${e.message}")
            } finally {
                busyCount.decrementAndGet()
                Bus.status(idleStatus())
            }
        }
    }

    private fun handle(r: AiResult, seconds: Float, typed: Boolean, system: Boolean, took: Float) {
        if (system) {
            if (r.reply.isNotBlank()) {
                Bus.post(Kind.AI, r.reply)
                speaker.speak(r.reply)
                addHistory("You (starting practice): ${r.reply}")
            }
            return
        }
        if (r.language == "noise") return
        if (r.language == "unclear" || (r.transcript.isBlank() && !typed)) {
            if (seconds >= 1.2f) Bus.post(Kind.INFO, "🤔 Couldn't hear that clearly — try again a bit closer to the mic.")
            return
        }

        if (!typed) {
            val tag = when (r.language) {
                "bangla" -> "Bangla detected"
                "mixed" -> "Bangla + English"
                else -> "English"
            }
            Bus.post(Kind.USER, r.transcript, tag, took)
        }

        val hasCorrection = r.corrected.isNotBlank() &&
            normalize(r.corrected) != normalize(r.transcript)
        if (hasCorrection) {
            val label = if (r.language == "english") "More natural way" else "In English"
            Bus.post(Kind.CORRECTION, r.corrected, if (r.mistakeNote.isNotBlank()) "$label · ${r.mistakeNote}" else label)
        }
        if (r.newWord.isNotBlank()) {
            val parts = r.newWord.split("|").map { it.trim() }
            Bus.post(Kind.WORD, parts[0], parts.drop(1).filter { it.isNotBlank() }.joinToString(" — "))
            prefs.addWord(r.newWord)
        }

        // Commands
        when (r.command) {
            "pause" -> paused = true
            "continue" -> paused = false
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
        Bus.statsChanged()

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
