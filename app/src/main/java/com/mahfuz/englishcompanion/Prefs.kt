package com.mahfuz.englishcompanion

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Word(val word: String, val meaning: String, val example: String, val time: Long)

data class Stats(
    val seconds: Float = 0f,
    val words: Int = 0,
    val english: Int = 0,
    val bangla: Int = 0,
    val mixed: Int = 0,
    val corrections: Int = 0,
    val newWords: Int = 0,
    val turns: Int = 0,
) {
    val totalLang get() = english + bangla + mixed
    val englishPct get() = if (totalLang == 0) 0 else (english * 100 + mixed * 50) / totalLang
    val banglaPct get() = if (totalLang == 0) 0 else 100 - englishPct
    operator fun plus(o: Stats) = Stats(
        seconds + o.seconds, words + o.words, english + o.english, bangla + o.bangla,
        mixed + o.mixed, corrections + o.corrections, newWords + o.newWords, turns + o.turns
    )
}

/** All saved settings, mistake memory, vocabulary and progress. Stored only on the phone. */
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

    var userName: String
        get() = p.getString("user_name", "") ?: ""
        set(v) = p.edit().putString("user_name", v.trim()).apply()

    var dailyGoalMin: Int
        get() = p.getInt("daily_goal", 15)
        set(v) = p.edit().putInt("daily_goal", v.coerceIn(5, 120)).apply()

    var mode: String
        get() = p.getString("mode", Modes.COMPANION)?.takeIf { it in Modes.ALL } ?: Modes.COMPANION
        set(v) = p.edit().putString("mode", v).apply()

    var professional: Boolean
        get() = p.getBoolean("professional", false)
        set(v) = p.edit().putBoolean("professional", v).apply()

    var speechRate: Float
        get() = p.getFloat("speech_rate", 0.95f)
        set(v) = p.edit().putFloat("speech_rate", v.coerceIn(0.5f, 1.6f)).apply()

    /** 0 = low (noisy places), 1 = medium, 2 = high (quiet voice) */
    var sensitivity: Int
        get() = p.getInt("sensitivity", 1)
        set(v) = p.edit().putInt("sensitivity", v.coerceIn(0, 2)).apply()

    /** Silence (ms) after which a sentence counts as finished. */
    var pauseMs: Int
        get() = p.getInt("pause_ms2", 900)
        set(v) = p.edit().putInt("pause_ms2", v).apply()

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
        val counts = obj("mistake_counts")
        counts.put(t, counts.optInt(t, 0) + 1)
        val notes = arr("mistake_notes")
        if (note.isNotBlank()) notes.put(note.trim())
        p.edit()
            .putString("mistake_counts", counts.toString())
            .putString("mistake_notes", lastN(notes, 30).toString())
            .apply()
    }

    fun mistakeCounts(): List<Pair<String, Int>> {
        val counts = obj("mistake_counts")
        val list = ArrayList<Pair<String, Int>>()
        val keys = counts.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            list.add(k to counts.optInt(k, 0))
        }
        return list.sortedByDescending { it.second }
    }

    fun recentMistakeNotes(n: Int): List<String> {
        val notes = arr("mistake_notes")
        val out = ArrayList<String>()
        for (i in maxOf(0, notes.length() - n) until notes.length()) out.add(notes.getString(i))
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
        if (notes.isNotEmpty()) sb.append("\nRecent mistakes: ").append(notes.joinToString(" | "))
        val words = vocabulary().take(8).map { it.word }
        if (words.isNotEmpty()) sb.append("\nWords already taught (don't repeat; reuse naturally): ")
            .append(words.joinToString(", "))
        return sb.toString()
    }

    // ---------- Vocabulary ----------
    fun addWord(raw: String) {
        val parts = raw.split("|").map { it.trim() }
        val word = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return
        val list = arr("vocab")
        for (i in 0 until list.length()) {
            if (list.getJSONObject(i).optString("w").equals(word, true)) return
        }
        list.put(JSONObject()
            .put("w", word)
            .put("m", parts.getOrNull(1) ?: "")
            .put("e", parts.getOrNull(2) ?: "")
            .put("t", System.currentTimeMillis()))
        p.edit().putString("vocab", lastN(list, 300).toString()).apply()
    }

    /** Newest first */
    fun vocabulary(): List<Word> {
        val list = arr("vocab")
        val out = ArrayList<Word>()
        for (i in list.length() - 1 downTo 0) {
            val o = list.getJSONObject(i)
            out.add(Word(o.optString("w"), o.optString("m"), o.optString("e"), o.optLong("t")))
        }
        return out
    }

    fun deleteWord(word: String) {
        val list = arr("vocab")
        val out = JSONArray()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (!o.optString("w").equals(word, true)) out.put(o)
        }
        p.edit().putString("vocab", out.toString()).apply()
    }

    // ---------- Progress (kept per day) ----------
    fun addTurn(language: String, words: Int, seconds: Float, corrected: Boolean, newWord: Boolean) {
        val days = obj("days")
        val key = dayKey(0)
        val d = days.optJSONObject(key) ?: JSONObject()
        d.put("s", d.optDouble("s", 0.0) + seconds)
        d.put("w", d.optInt("w", 0) + words)
        d.put("t", d.optInt("t", 0) + 1)
        when (language) {
            "english" -> d.put("en", d.optInt("en", 0) + 1)
            "bangla" -> d.put("bn", d.optInt("bn", 0) + 1)
            "mixed" -> d.put("mx", d.optInt("mx", 0) + 1)
        }
        if (corrected) d.put("c", d.optInt("c", 0) + 1)
        if (newWord) d.put("v", d.optInt("v", 0) + 1)
        days.put(key, d)
        p.edit().putString("days", days.toString()).apply()
    }

    /** Totals for the last [n] days (1 = today only). n <= 0 means all time. */
    fun stats(n: Int): Stats {
        val days = obj("days")
        var total = Stats()
        if (n <= 0) {
            val keys = days.keys()
            while (keys.hasNext()) total += parse(days.optJSONObject(keys.next()))
        } else {
            for (i in 0 until n) total += parse(days.optJSONObject(dayKey(i)))
        }
        return total
    }

    /** Speaking minutes for each of the last [n] days, oldest first. */
    fun dailyMinutes(n: Int): List<Float> {
        val days = obj("days")
        return (n - 1 downTo 0).map { i ->
            ((days.optJSONObject(dayKey(i))?.optDouble("s", 0.0) ?: 0.0) / 60.0).toFloat()
        }
    }

    fun streak(): Int {
        val days = obj("days")
        var i = if (days.has(dayKey(0))) 0 else 1   // today not started yet doesn't break it
        var count = 0
        while (days.has(dayKey(i))) { count++; i++ }
        return count
    }

    private fun parse(d: JSONObject?): Stats {
        if (d == null) return Stats()
        return Stats(
            d.optDouble("s", 0.0).toFloat(), d.optInt("w", 0), d.optInt("en", 0),
            d.optInt("bn", 0), d.optInt("mx", 0), d.optInt("c", 0), d.optInt("v", 0), d.optInt("t", 0)
        )
    }

    private fun dayKey(daysAgo: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(c.timeInMillis))
    }

    fun clearAllData() {
        val keep = mapOf("api_key" to apiKey, "model" to model, "user_name" to userName)
        p.edit().clear().commit()
        val e = p.edit()
        keep.forEach { (k, v) -> e.putString(k, v) }
        e.apply()
    }

    // ---------- helpers ----------
    private fun obj(key: String) = try { JSONObject(p.getString(key, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
    private fun arr(key: String) = try { JSONArray(p.getString(key, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
    private fun lastN(a: JSONArray, n: Int): JSONArray {
        val out = JSONArray()
        for (i in maxOf(0, a.length() - n) until a.length()) out.put(a.get(i))
        return out
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
    val TITLES = listOf("Companion Mode", "Teacher Mode", "Conversation Mode")
    val SUBTITLES = listOf(
        "Minimal interruption",
        "More corrections & explanations",
        "Actively chats with you"
    )

    fun title(mode: String) = TITLES[ALL.indexOf(mode).coerceAtLeast(0)]
}
