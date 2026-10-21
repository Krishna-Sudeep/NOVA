package com.nova.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

// Better listening: several guesses per sentence, longer pauses allowed, fuzzy wake word, multi-step splitting.
object Voice {
    private const val VERBS = "call|open|play|text|message|whatsapp|set|turn|navigate|directions|search|send|youtube|volume"
    private val splitter = Regex(" and then | then | after that |, and | and (?=($VERBS) )", RegexOption.IGNORE_CASE)

    fun intent(c: Context) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        Prefs.get(c, "lang").takeIf { it.isNotBlank() }?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
    }

    fun alts(r: Bundle?): List<String> = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()

    private val msgStart = Regex("\\b(?:send|text|message|whatsapp)\\b[^,]*?\\b(?:saying|that|say)\\b", RegexOption.IGNORE_CASE)

    // Splits "do A and then do B", but a message ("... saying hey, then what's up") is never cut in the middle
    fun steps(t: String): List<String> {
        val s = t.trim()
        val m = msgStart.find(s)
        val head = if (m != null) s.substring(0, m.range.first) else s
        val parts = head.split(splitter).map { it.trim() }.filter { it.isNotEmpty() }
        return if (m != null) parts + s.substring(m.range.first) else parts
    }

    fun dist(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    private fun clean(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    // Looks through all guesses for the assistant's name (allowing small mishearings).
    // Returns what was said after it, or null if the name wasn't heard.
    fun afterWake(alts: List<String>, wake: String): String? {
        val w0 = clean(wake)
        val max = if (w0.length >= 6) 2 else if (w0.length >= 5) 1 else 0
        for (t in alts) {
            val w = t.split(" ").filter { it.isNotEmpty() }
            for (i in w.indices) {
                if (dist(clean(w[i]), w0) <= max) return w.drop(i + 1).joinToString(" ")
                if (i + 1 < w.size && dist(clean(w[i] + w[i + 1]), w0) <= max) return w.drop(i + 2).joinToString(" ")
            }
        }
        return null
    }
}
