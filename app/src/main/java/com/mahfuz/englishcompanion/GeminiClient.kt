package com.mahfuz.englishcompanion

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** What the AI decided after hearing one thing the user said. */
data class AiResult(
    val transcript: String,
    val language: String,      // english | bangla | mixed | noise
    val speak: Boolean,
    val reply: String,
    val corrected: String,
    val mistakeType: String,
    val mistakeNote: String,
    val newWord: String,
    val command: String,       // none | pause | continue | slower | faster | repeat | stop
    val reason: String = ""    // why the AI chose to speak / stay silent (for the debug log)
)

class ApiException(val code: Int, message: String) : IOException(message)

/** How long each part of the last request took (for the debug log). */
data class CallTiming(
    var requestBytes: Int = 0,
    var uploadMs: Long = 0,      // sending the request
    var waitMs: Long = 0,        // waiting for Google to answer (includes AI thinking)
    var downloadMs: Long = 0,
    var totalMs: Long = 0,
    var attempts: Int = 0,
    var httpCode: Int = 0,
)

class GeminiClient {
    /** Timing of the most recent call (only used from the single worker thread). */
    val timing = CallTiming()

    fun analyzeAudio(
        apiKey: String, model: String, systemPrompt: String, audio: EncodedAudio
    ): AiResult {
        val b64 = Base64.encodeToString(audio.bytes, Base64.NO_WRAP)
        val parts = JSONArray()
            .put(JSONObject().put("inlineData",
                JSONObject().put("mimeType", audio.mime).put("data", b64)))
            .put(JSONObject().put("text",
                "This audio is what the user just said. Respond with the JSON object only."))
        return call(apiKey, model, systemPrompt, parts)
    }

    /** A message from the app itself (not the user), e.g. "start a practice session". */
    fun instruct(apiKey: String, model: String, systemPrompt: String, instruction: String): AiResult {
        val parts = JSONArray().put(JSONObject().put("text",
            "[APP INSTRUCTION — the user did not say this. Set transcript to \"\" and language to \"english\".] " +
                instruction + "\nRespond with the JSON object only."))
        return call(apiKey, model, systemPrompt, parts)
    }

    fun analyzeText(
        apiKey: String, model: String, systemPrompt: String, text: String
    ): AiResult {
        val parts = JSONArray().put(JSONObject().put("text",
            "The user typed this instead of speaking (treat it exactly like speech; " +
                "transcript = this text): \"$text\"\nRespond with the JSON object only."))
        return call(apiKey, model, systemPrompt, parts)
    }

    /**
     * Try once more only if Google's server hiccups (5xx) or the connection couldn't be made.
     * A slow network is NOT retried: repeating a slow upload would only make replies later.
     */
    private fun call(apiKey: String, model: String, systemPrompt: String, parts: JSONArray): AiResult {
        val t = timing
        t.requestBytes = 0; t.uploadMs = 0; t.waitMs = 0; t.downloadMs = 0; t.attempts = 0; t.httpCode = 0
        val t0 = System.currentTimeMillis()
        try {
            return try {
                callOnce(apiKey, model, systemPrompt, parts)
            } catch (e: java.net.ConnectException) {
                callOnce(apiKey, model, systemPrompt, parts)
            } catch (e: ApiException) {
                if (e.code >= 500) callOnce(apiKey, model, systemPrompt, parts) else throw e
            }
        } finally {
            t.totalMs = System.currentTimeMillis() - t0
        }
    }

    private fun callOnce(apiKey: String, model: String, systemPrompt: String, parts: JSONArray): AiResult {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.4))

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 25000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            timing.attempts++
            timing.requestBytes = bytes.size
            val a = System.currentTimeMillis()
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val b = System.currentTimeMillis()

            val code = conn.responseCode
            val c = System.currentTimeMillis()
            timing.httpCode = code
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            timing.uploadMs = b - a
            timing.waitMs = c - b
            timing.downloadMs = System.currentTimeMillis() - c
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).getJSONObject("error").optString("message", text)
                } catch (e: Exception) { text }
                throw ApiException(code, msg.take(300))
            }
            return parse(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(responseText: String): AiResult {
        val root = JSONObject(responseText)
        val candidates = root.optJSONArray("candidates")
            ?: throw IOException("AI gave no answer (it may have been blocked).")
        val content = candidates.getJSONObject(0).optJSONObject("content")
            ?: throw IOException("AI gave an empty answer.")
        val partsArr = content.optJSONArray("parts") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until partsArr.length()) {
            val part = partsArr.getJSONObject(i)
            if (part.optBoolean("thought", false)) continue
            sb.append(part.optString("text", ""))
        }
        var raw = sb.toString().trim()
        // Remove ```json fences if the model added them
        if (raw.startsWith("```")) {
            raw = raw.substringAfter('\n').substringBeforeLast("```").trim()
        }
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("Could not read AI answer: ${raw.take(120)}")
        val j = JSONObject(raw.substring(start, end + 1))
        return AiResult(
            transcript = j.optString("transcript", ""),
            language = j.optString("language", "english").lowercase(),
            speak = j.optBoolean("speak", false),
            reply = j.optString("reply", ""),
            corrected = j.optString("corrected", ""),
            mistakeType = j.optString("mistake_type", "none"),
            mistakeNote = j.optString("mistake_note", ""),
            newWord = j.optString("new_word", ""),
            command = j.optString("command", "none").lowercase(),
            reason = j.optString("reason", "")
        )
    }
}
