package com.nova.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.concurrent.atomic.AtomicInteger

// The brain: splits multi-step requests, runs phone commands, otherwise asks the AI.
// States sent to the screen: listening, thinking, speaking, confirm (waiting for yes/no), idle.
class Assistant(private val c: Context, private val onState: (String, String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val cmd = CommandHandler(c) { s -> main.post { say(s) } }
    private val queue = ArrayDeque<String>()
    private var pending: (() -> String)? = null
    private val spoken = AtomicInteger(0)
    private val tts = TextToSpeech(c) { }
    val speaking: Boolean get() = spoken.get() > 0
    private val yes = Regex("\\b(yes|yeah|yep|confirm|sure|go ahead|do it|send it)\\b")

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { done() }
            override fun onError(id: String?) { done() }
        })
    }

    private fun done() {
        if (spoken.decrementAndGet() <= 0) { spoken.set(0); main.post { onState("idle", "") } }
    }

    fun say(s: String) {
        if (s.isEmpty()) { onState("idle", ""); return }
        spoken.incrementAndGet()
        onState("speaking", s)
        tts.speak(s, TextToSpeech.QUEUE_ADD, null, "nova" + System.nanoTime())
    }

    fun handle(text: String) {
        onState("thinking", text)
        val p = pending
        if (p != null) {
            pending = null
            if (yes.containsMatchIn(text.lowercase())) {
                say(try { p() } catch (e: Exception) { "That failed." })
                after()
            } else { queue.clear(); say("Cancelled.") }
            return
        }
        queue.clear(); queue.addAll(Voice.steps(text)); step()
    }

    private fun after() { if (queue.isNotEmpty()) main.postDelayed({ step() }, 2500) }

    private fun step() {
        val seg = queue.removeFirstOrNull() ?: return
        Thread {
            val r = try { cmd.handle(seg) } catch (e: Exception) { CommandHandler.Result("That failed.") }
            if (r != null) {
                main.post {
                    pending = r.confirm
                    History.add(c, "user", seg); History.add(c, "assistant", r.say)
                    if (r.confirm != null) onState("confirm", "")
                    say(r.say)
                    if (r.confirm == null) after()
                }
            } else {
                val res = runCatching { AiClient.ask(c, seg) }
                main.post {
                    res.onSuccess { History.add(c, "user", seg); History.add(c, "assistant", it); say(it); after() }
                        .onFailure { queue.clear(); say(it.message ?: "Something went wrong.") }
                }
            }
        }.start()
    }

    fun stopSpeaking() { queue.clear(); tts.stop(); spoken.set(0) }
    fun shutdown() { tts.shutdown() }
}
