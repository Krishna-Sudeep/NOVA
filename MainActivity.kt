package com.nova.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.app.NotificationManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class MainActivity : Activity() {
    private val perms = arrayOf(
        Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS
    )
    private lateinit var a: Assistant
    private lateinit var status: TextView
    private lateinit var response: TextView
    private lateinit var mic: Button
    private lateinit var hands: Switch
    private var sr: SpeechRecognizer? = null
    private var pulse: ObjectAnimator? = null
    private var listenNext = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 80, 40, 40) }
        status = TextView(this).apply { textSize = 18f; gravity = Gravity.CENTER; text = "Tap to talk" }
        response = TextView(this).apply { textSize = 20f; setPadding(0, 32, 0, 32) }
        mic = Button(this).apply { text = "🎤"; textSize = 40f; setOnClickListener { onMic() } }
        hands = Switch(this).apply {
            text = "Hands-free (say \"${Prefs.name(this@MainActivity)}\")"
            setOnCheckedChangeListener { _, on -> toggleHands(on) }
        }
        val row = LinearLayout(this).apply {
            addView(Button(this@MainActivity).apply { text = "Settings"; setOnClickListener { settings() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(Button(this@MainActivity).apply { text = "Setup check"; setOnClickListener { check() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        root.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(ScrollView(this).apply { addView(response) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(mic, LinearLayout.LayoutParams(MATCH_PARENT, 260))
        root.addView(hands); root.addView(row)
        setContentView(root)
        a = Assistant(this) { s, t -> show(s, t) }
        if (needPerms()) requestPermissions(perms, 1)
    }

    private fun needPerms() = perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private fun show(s: String, t: String) {
        status.text = when (s) { "listening" -> "Listening…"; "thinking" -> "Thinking…"; "speaking" -> "Speaking…"; else -> "Tap to talk" }
        if (t.isNotEmpty()) response.text = if (s == "thinking") "You: $t" else "${Prefs.name(this)}: $t"
        if (s == "confirm") listenNext = true
        if (s == "idle" && listenNext) { listenNext = false; onMic(); return }   // auto-listen for your yes/no
        if (s == "listening" || s == "speaking") {
            if (pulse == null) pulse = ObjectAnimator.ofPropertyValuesHolder(mic,
                PropertyValuesHolder.ofFloat("scaleX", 1.1f), PropertyValuesHolder.ofFloat("scaleY", 1.1f)).apply {
                duration = 500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
            }
        } else { pulse?.cancel(); pulse = null; mic.scaleX = 1f; mic.scaleY = 1f }
    }

    private fun onMic() {
        a.stopSpeaking()   // tapping interrupts speech
        if (needPerms()) { requestPermissions(perms, 1); return }
        if (hands.isChecked) hands.isChecked = false
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) { val alts = Voice.alts(r); if (alts.isNotEmpty()) a.handle(alts[0]) else show("idle", "") }
                override fun onError(e: Int) = show("idle", "")
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            it.startListening(Voice.intent(this))
        }
        show("listening", "")
    }

    private fun toggleHands(on: Boolean) {
        val svc = Intent(this, AssistantService::class.java)
        if (!on) { stopService(svc); return }
        if (needPerms()) { hands.isChecked = false; requestPermissions(perms, 1); return }
        if (!Settings.canDrawOverlays(this)) {
            hands.isChecked = false
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        startForegroundService(svc)
    }

    private fun settings() {
        fun field(h: String, k: String) = EditText(this).apply { hint = h; setText(Prefs.get(this@MainActivity, k)) }
        val n = field("Assistant name (e.g. Friday)", "name")
        val u = field("Backend URL", "url")
        val tk = field("Backend token", "token")
        val cc = field("Your country code, digits only (e.g. 91)", "cc")
        val lg = field("Speech language (optional, e.g. en-IN)", "lang")
        val ak = CheckBox(this).apply { text = "Ask before sending messages"; isChecked = Prefs.get(this@MainActivity, "ask") == "1" }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0)
            addView(n); addView(u); addView(tk); addView(cc); addView(lg); addView(ak)
        }
        AlertDialog.Builder(this).setTitle("Settings").setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("Save") { _, _ ->
                Prefs.set(this, "name", n.text.toString().trim()); Prefs.set(this, "url", u.text.toString().trim())
                Prefs.set(this, "token", tk.text.toString().trim()); Prefs.set(this, "cc", cc.text.toString().trim())
                Prefs.set(this, "lang", lg.text.toString().trim()); Prefs.set(this, "ask", if (ak.isChecked) "1" else "0")
                hands.text = "Hands-free (say \"${Prefs.name(this)}\")"
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Export chats") { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, History.export(this)), "Export"))
            }
            .setNegativeButton("Delete chats") { _, _ ->
                History.clear(this); Toast.makeText(this, "Chats deleted", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun check() {
        fun ok(b: Boolean) = if (b) "✅ " else "❌ "
        fun g(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        val msg = ok(g(Manifest.permission.RECORD_AUDIO)) + "Microphone\n" +
            ok(g(Manifest.permission.CALL_PHONE)) + "Calls\n" +
            ok(g(Manifest.permission.SEND_SMS)) + "Texts\n" +
            ok(g(Manifest.permission.READ_CONTACTS)) + "Contacts\n" +
            ok(Settings.canDrawOverlays(this)) + "Display over other apps (needed to open apps hands-free)\n" +
            ok(SendService.enabled(this)) + "WhatsApp auto-send helper (optional)\n" +
            ok(Prefs.get(this, "url").isNotBlank()) + "AI backend address\n" +
            ok(Prefs.get(this, "cc").isNotBlank()) + "Country code (for WhatsApp)\n" +
            ok(getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) + "Battery: unrestricted (keeps hands-free alive)\n" +
            ok(Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) + "Full-screen notifications (unlock prompt on lock screen)"
        AlertDialog.Builder(this).setTitle("Setup check").setMessage(msg)
            .setPositiveButton("Accessibility") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNeutralButton("App permissions") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Close", null).show()
    }

    override fun onDestroy() { sr?.destroy(); a.shutdown(); super.onDestroy() }
}
