package com.nova.assistant

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.speech.*
import android.view.View
import android.view.WindowManager

// Hands-free mode: keeps listening for the assistant's name (e.g. "Nova" or "Friday").
class AssistantService : Service() {
    private var sr: SpeechRecognizer? = null
    private lateinit var a: Assistant
    private var wl: PowerManager.WakeLock? = null
    private var overlay: View? = null
    private val main = Handler(Looper.getMainLooper())
    private var awaitUntil = 0L
    private var beat = 0L
    private var errors = 0

    private fun now() = System.currentTimeMillis()

    private val listener = object : RecognitionListener {
        override fun onResults(r: Bundle?) { beat = now(); errors = 0; process(Voice.alts(r)); listen(600) }
        override fun onError(e: Int) {
            beat = now()
            if (e != SpeechRecognizer.ERROR_NO_MATCH && e != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) errors++
            if (errors >= 5 || e == SpeechRecognizer.ERROR_CLIENT || e == SpeechRecognizer.ERROR_SERVER) { errors = 0; build() }
            listen(if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2000 else 400)
        }
        override fun onReadyForSpeech(p: Bundle?) { beat = now() }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p: Bundle?) {}
        override fun onEvent(t: Int, p: Bundle?) {}
    }

    private val listenTask = Runnable {
        if (a.speaking) listen(500)
        else try { sr?.startListening(Voice.intent(this)) } catch (e: Exception) { build(); listen(1000) }
    }
    private fun listen(d: Long) { main.removeCallbacks(listenTask); main.postDelayed(listenTask, d) }

    // Watchdog: if listening silently died, restart it
    private val dog = object : Runnable {
        override fun run() {
            if (!a.speaking && now() - beat > 25000) { build(); listen(0) }
            main.postDelayed(this, 10000)
        }
    }

    private fun build() {
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nova", "Assistant", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "nova").setContentTitle("${Prefs.name(this)} is listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        a = Assistant(this) { s, _ -> if (s == "confirm") awaitUntil = now() + 15000 }
        wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nova:listen").apply { acquire() }
        // Android 15 only lets a background app open other apps if it has a visible overlay window
        try {
            if (Settings.canDrawOverlays(this)) {
                val v = View(this)
                val lp = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT)
                getSystemService(WindowManager::class.java).addView(v, lp); overlay = v
            }
        } catch (e: Exception) { }
        beat = now(); build(); listen(0); main.postDelayed(dog, 10000)
    }

    private fun process(alts: List<String>) {
        if (alts.isEmpty()) return
        if (now() < awaitUntil) { awaitUntil = 0; a.handle(alts[0]); return }
        val rest = Voice.afterWake(alts, Prefs.name(this)) ?: return
        if (rest.isBlank()) { awaitUntil = now() + 15000; a.say("Yes?") } else a.handle(rest)
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() {
        main.removeCallbacksAndMessages(null); sr?.destroy(); a.shutdown()
        try { overlay?.let { getSystemService(WindowManager::class.java).removeView(it) } } catch (e: Exception) { }
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
