package com.mahfuz.englishcompanion

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Kind { USER, AI, CORRECTION, WORD, INFO, ERROR }

data class LogEntry(
    val kind: Kind,
    val text: String,
    /** USER: language tag ("Bangla detected" etc.). CORRECTION: the short note. WORD: meaning. */
    val meta: String = "",
    /** USER: seconds the AI took to answer */
    val seconds: Float = 0f,
    val id: Long = nextId(),
) {
    companion object {
        private var counter = 0L
        @Synchronized fun nextId(): Long = ++counter
    }
}

/** Live state shared between the background companion and the screens. Always updated on the main thread. */
object Bus {
    private val main = Handler(Looper.getMainLooper())

    val log = mutableStateListOf<LogEntry>()
    var status by mutableStateOf("Ready")
        private set
    var running by mutableStateOf(false)
        private set
    /** 0..1 microphone loudness, for the orb animation */
    var level by mutableFloatStateOf(0f)
        private set
    /** true while the user is speaking (between speech start and send) */
    var capturing by mutableStateOf(false)
        private set
    /** bumps whenever stats change, so screens re-read them */
    var statsVersion by mutableStateOf(0)
        private set

    @Volatile var isRunning = false
        private set

    fun post(kind: Kind, text: String, meta: String = "", seconds: Float = 0f) {
        val e = LogEntry(kind, text, meta, seconds)
        main.post {
            log.add(e)
            if (log.size > 200) log.removeAt(0)
        }
    }

    fun status(s: String) = main.post { status = s }

    fun running(r: Boolean) {
        isRunning = r
        main.post { running = r; if (!r) { level = 0f; capturing = false } }
    }

    fun level(v: Float) = main.post { level = v }

    fun capturing(c: Boolean) = main.post { capturing = c }

    fun statsChanged() = main.post { statsVersion++ }

    fun clearLog() = main.post { log.clear() }
}
