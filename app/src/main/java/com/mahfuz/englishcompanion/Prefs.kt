package com.mahfuz.englishcompanion

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** All saved settings, mistake memory and progress numbers. Stored only on the phone. */
class Prefs(context: Context) {
    private val p: SharedPreferences =
        context.getSharedPreferences("companion", Context.MODE_PRIVATE)

    // ---------- Settings ----------
    var apiKey: String
        get() = p.getString("api_key", "") ?: ""
        set(v) = p.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = p.getString("model", DEFAULT_MODEL)?.ifBlank { DEFAULT_MODEL } ?: DEFAULT_MODEL
        set(v) = p.edit().putString("model", v.trim()).apply()

    var mode: String
        get() = p.getString("mode", Modes.COMPANION) ?: Modes.COMPANION
        set(v) = p.edit().putString("mode", v).apply()

    var speechRate: Float
        get() = p.getFloat("speech_rate", 0.95f)
        set(v) = p.edit().putFloat("speech_rate", v.coerceIn(0.5f, 1.6f)).apply()

    /** 0 = low (needs louder voice), 1 = medium, 2 = high (picks up quiet voice) */
    var sensitivity: Int
        get() = p.getInt("sensitivity", 1)
        set(v) = p.edit().putInt("sensitivity", v).apply()

    /** How long you must be silent before the app treats your sentence as finished. */
    var pauseMs: Int
        get() = p.getInt("pause_ms", 1300)
        set(v) = p.edit().putInt("pause_ms", v).apply()

    /** Extra switch: focus on workplace / formal English in any mode. */
    var professional: Boolean
        get() = p.getBoolean("professional", false)
        set(v) = p.edit().putBoolean("professional", v).apply()

    var saveHistory: Boolean
        get() = p.getBoolean("save_history", false)
        set(v) = p.edit().putBoolean("save_history", v).apply()

    var useMemory: Boolean
        get() = p.getBoolean("use_memory", true)
        set(v) = p.edit().putBoolean("use_memory", v).apply()

    // ---------- Mistake memory ----------
    fun recordMistake(type: String, note: String) {
        if (!useMemory) return
        val t = type.trim().lowercase(Locale.US)
        if (t.isEmpty() || t == "none") return
        val counts = JSONObject(p.getString("mistake_counts", "{}") ?: "{}")
        counts.put(t, counts.optInt(t, 0) + 1)
        val notes = JSONArray(p.getString("mistake_notes", "[]") ?: "[]")
        if (note.isNotBlank()) notes.put(note.trim())
        val trimmed = JSONArray()
        val start = maxOf(0, notes.length() - 30)
        for (i in start until notes.length()) trimmed.put(notes.getString(i))
        p.edit()
            .putString("mistake_counts", counts.toString())
            .putString("mistake_notes", trimmed.toString())
            .apply()
    }

    fun mistakeCounts(): List<Pair<String, Int>> {
        val counts = JSONObject(p.getString("mistake_counts", "{}") ?: "{}")
        val list = ArrayList<Pair<String, Int>>()
        val keys = counts.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            list.add(k to counts.optInt(k, 0))
        }
        return list.sortedByDescending { it.second }
    }

    fun recentMistakeNotes(n: Int): List<String> {
        val notes = JSONArray(p.getString("mistake_notes", "[]") ?: "[]")
        val out = ArrayList<String>()
        val start = maxOf(0, notes.length() - n)
        for (i in start until notes.length()) out.add(notes.getString(i))
        return out
    }

    fun memorySummary(): String {
        if (!useMemory) return ""
        val top = mistakeCounts().take(5)
        if (top.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("Mistake types this user makes most (type: count): ")
        sb.append(top.joinToString(", ") { "${it.first}: ${it.second}" })
        val notes = recentMistakeNotes(6)
        if (notes.isNotEmpty()) {
            sb.append("\nRecent mistakes: ")
            sb.append(notes.joinToString(" | "))
        }
        return sb.toString()
    }

    // ---------- Progress ----------
    fun addTurn(language: String, words: Int, seconds: Float, corrected: Boolean, newWord: Boolean) {
        val e = p.edit()
        e.putInt("turns", p.getInt("turns", 0) + 1)
        e.putInt("words", p.getInt("words", 0) + words)
        e.putFloat("seconds", p.getFloat("seconds", 0f) + seconds)
        when (language) {
            "english" -> e.putInt("lang_en", p.getInt("lang_en", 0) + 1)
            "bangla" -> e.putInt("lang_bn", p.getInt("lang_bn", 0) + 1)
            "mixed" -> e.putInt("lang_mix", p.getInt("lang_mix", 0) + 1)
        }
        if (corrected) e.putInt("corrections", p.getInt("corrections", 0) + 1)
        if (newWord) e.putInt("new_words", p.getInt("new_words", 0) + 1)

        // Daily streak
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val last = p.getString("last_day", "") ?: ""
        if (last != today) {
            val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Date(System.currentTimeMillis() - 24L * 3600 * 1000))
            val streak = if (last == yesterday) p.getInt("streak", 0) + 1 else 1
            e.putInt("streak", streak)
            e.putString("last_day", today)
        }
        e.apply()
    }

    fun statsText(): String {
        val en = p.getInt("lang_en", 0)
        val bn = p.getInt("lang_bn", 0)
        val mix = p.getInt("lang_mix", 0)
        val total = en + bn + mix
        val enPct = if (total == 0) 0 else (en * 100 / total)
        val mins = (p.getFloat("seconds", 0f) / 60f)
        return String.format(
            Locale.US,
            "🔥 %d-day streak · %.0f min spoken · %d words · %d%% English · %d corrections · %d new words",
            p.getInt("streak", 0), mins, p.getInt("words", 0), enPct,
            p.getInt("corrections", 0), p.getInt("new_words", 0)
        )
    }

    fun clearAllData() {
        val savedKey = apiKey
        val savedModel = model
        p.edit().clear().commit()
        apiKey = savedKey   // keep the key so the app still works
        model = savedModel
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    }
}

object Modes {
    const val COMPANION = "companion"
    const val TEACHER = "teacher"
    const val CONVERSATION = "conversation"

    val ALL = listOf(COMPANION, TEACHER, CONVERSATION)
    val LABELS = listOf(
        "Companion — quiet, helps only when needed",
        "Teacher — more corrections + short explanations",
        "Conversation — chats with you, asks questions"
    )
}
