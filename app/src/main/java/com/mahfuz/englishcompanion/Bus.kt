package com.mahfuz.englishcompanion

import android.os.Handler
import android.os.Looper

enum class Kind { USER, AI, CORRECTION, INFO, ERROR }

data class LogEntry(val kind: Kind, val text: String, val time: Long = System.currentTimeMillis())

/** Passes messages from the background companion to the screen. */
object Bus {
    private val main = Handler(Looper.getMainLooper())
    val log = ArrayList<LogEntry>()

    var onEntry: ((LogEntry) -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null
    var onRunning: ((Boolean) -> Unit)? = null

    @Volatile var status: String = "Ready"
        private set
    @Volatile var running: Boolean = false
        private set

    fun post(kind: Kind, text: String) {
        val e = LogEntry(kind, text)
        main.post {
            log.add(e)
            if (log.size > 300) log.removeAt(0)
            onEntry?.invoke(e)
        }
    }

    fun status(s: String) {
        status = s
        main.post { onStatus?.invoke(s) }
    }

    fun running(r: Boolean) {
        running = r
        main.post { onRunning?.invoke(r) }
    }
}
