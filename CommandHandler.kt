package com.nova.assistant

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager

class CommandHandler(private val c: Context, private val later: (String) -> Unit) {
    // confirm != null means: ask the user first, run confirm() only if they say yes
    class Result(val say: String, val confirm: (() -> String)? = null)
    private class Found(val name: String? = null, val number: String? = null, val many: List<String> = emptyList())

    private val ic = RegexOption.IGNORE_CASE
    private var gated = false
    private val say = "(?:saying|that|say)"
    private val waPatterns = listOf(
        "^send (?:a )?whatsapp (?:message )?to (.+?) $say (.+)$",
        "^send (.+?) a whatsapp(?: message)? $say (.+)$",
        "^(?:whatsapp|message|text) (.+?)(?: on whatsapp)? $say (.+)$"
    ).map { Regex(it, ic) }
    private val smsPatterns = listOf(
        "^(?:text|message|sms) (.+?) $say (.+)$",
        "^send (.+?) a (?:text|message|sms)(?: message)? $say (.+)$"
    ).map { Regex(it, ic) }

    private fun ask() = Prefs.get(c, "ask") == "1"   // Settings: "Ask before sending messages"
    private fun go(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i) }

    // For anything that shows another app: if the phone is locked, unlock first
    private fun goUi(i: Intent, fallback: Intent? = null) {
        val ran = LockGate.run(c, { later("Your phone stayed locked, so I didn't do that.") }) {
            try { go(i) } catch (e: Exception) { if (fallback != null) try { go(fallback) } catch (e2: Exception) { } }
        }
        if (!ran) gated = true
    }

    // Runs on a background thread. Returns null when the text is not a phone command (then the AI answers it).
    fun handle(raw0: String): Result? {
        val raw = raw0.trim()
        val t = raw.lowercase().replace(".", "")
        gated = false
        return try {
            val mWa = if (t.contains("whatsapp")) waPatterns.firstNotNullOfOrNull { it.find(raw) } else null
            val mSms = smsPatterns.firstNotNullOfOrNull { it.find(raw) }
            val mPlay = Regex("^(?:play|youtube) (.+?)(?: on (youtube|spotify))?$", ic).find(raw)
            val mAlarm = Regex("alarm (?:for|at) (\\d{1,2})(?::(\\d{2}))? ?(am|pm)?").find(t)
            val mTimer = Regex("timer (?:for )?(\\d+) (second|minute|hour)").find(t)
            val r = when {
                mWa != null -> whatsapp(mWa.groupValues[1], mWa.groupValues[2])
                t.startsWith("call ") -> call(t.removePrefix("call ").trim())
                mSms != null -> sms(mSms.groupValues[1], mSms.groupValues[2])
                mPlay != null -> if (mPlay.groupValues[2].lowercase() == "spotify") spotify(mPlay.groupValues[1]) else youtube(mPlay.groupValues[1])
                mAlarm != null -> Result(alarm(mAlarm.groupValues))
                mTimer != null -> Result(timer(mTimer.groupValues))
                t.startsWith("open ") -> Result(open(t.removePrefix("open ").trim()))
                t.contains("flashlight") || t.contains("torch") -> Result(torch(!t.contains("off")))
                t.contains("volume up") -> Result(vol(AudioManager.ADJUST_RAISE))
                t.contains("volume down") -> Result(vol(AudioManager.ADJUST_LOWER))
                t.startsWith("navigate to ") || t.startsWith("directions to ") -> Result(nav(t.substringAfter(" to ").trim()))
                t.contains("wifi") || t.contains("wi-fi") -> { goUi(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)); Result("Opening the Wi-Fi panel. Android needs you to tap the switch.") }
                t.contains("bluetooth") && (t.contains("turn") || t.contains("open") || t.contains("settings")) -> { goUi(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); Result("Opening Bluetooth settings. Android needs you to tap the switch.") }
                t.startsWith("search for ") || t.startsWith("google ") || t.contains("weather") -> {
                    goUi(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, t.removePrefix("search for ").removePrefix("google ").trim())); Result("Searching")
                }
                else -> null
            }
            if (gated) Result("Your phone is locked. Unlock it and I'll do that.") else r
        } catch (e: Exception) { Result("Sorry, that didn't work.") }
    }

    // Finds a contact; tolerates small mishearings; reports when two different people match
    private fun find(name0: String): Found {
        val name = name0.lowercase().trim()
        val q = c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null) ?: return Found()
        val rows = ArrayList<Triple<String, String, Int>>()
        q.use {
            while (it.moveToNext()) {
                val dn = it.getString(0) ?: continue
                val num = it.getString(1) ?: continue
                val n = dn.lowercase(); val words = n.split(" ")
                val d = when {
                    n == name || words.contains(name) -> 0
                    n.contains(name) -> 1
                    else -> minOf(Voice.dist(n, name), words.minOf { w -> Voice.dist(w, name) })
                }
                rows.add(Triple(dn, num, d))
            }
        }
        val best = rows.minOfOrNull { it.third } ?: return Found()
        if (best > 1 + name.length / 5) return Found()
        val top = rows.filter { it.third == best }
        val names = top.map { it.first }.distinct()
        return if (names.size > 1) Found(many = names.take(4)) else Found(top[0].first, top[0].second)
    }

    private fun contact(name: String, then: (Found) -> Result): Result {
        val f = find(name)
        return when {
            f.many.isNotEmpty() -> Result("I found ${f.many.joinToString(" and ")}. Please say the full name.")
            f.number == null -> Result("I couldn't find $name in your contacts.")
            else -> then(f)
        }
    }

    private fun call(name: String): Result {
        if (name.any(Char::isDigit) && name.none(Char::isLetter)) {
            go(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + name.filter { it.isDigit() || it == '+' }))); return Result("Calling")
        }
        return contact(name) { f -> go(Intent(Intent.ACTION_CALL, Uri.parse("tel:${f.number}"))); Result("Calling ${f.name}") }
    }

    private fun sms(name: String, msg: String): Result = contact(name) { f ->
        val act: () -> String = {
            val m = c.getSystemService(SmsManager::class.java)
            m.sendMultipartTextMessage(f.number!!, null, m.divideMessage(msg), null, null)
            "Texting ${f.name}."
        }
        if (ask()) Result("Send \"$msg\" to ${f.name}. Say yes to confirm.", act) else Result(act())
    }

    private fun waDigits(n: String): String? {
        val d = n.filter { it.isDigit() }
        if (n.trim().startsWith("+")) return d
        val cc = Prefs.get(c, "cc").filter { it.isDigit() }
        if (cc.isBlank()) return null
        return if (d.startsWith(cc) && d.length > 10) d else cc + d.trimStart('0')
    }

    private fun launchWa(digits: String, msg: String, name: String, auto: Boolean) {
        if (auto) SendService.arm { ok ->
            later(if (ok) "Done. I tapped Send in your chat with $name." else "I opened WhatsApp but couldn't confirm the message to $name was sent.")
        }
        val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(msg))
        try { go(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")) }
        catch (e: Exception) { try { go(Intent(Intent.ACTION_VIEW, uri)) } catch (e2: Exception) { later("I couldn't open WhatsApp.") } }
    }

    private fun whatsapp(name: String, msg0: String): Result = contact(name) { f ->
        val digits = waDigits(f.number!!) ?: return@contact Result("Please set your country code in Settings first.")
        val msg = msg0.trim().trim('"', '\'', '“', '”')
        val auto = SendService.enabled(c)
        val act: () -> String = {
            val ranNow = LockGate.run(c, { later("Your phone stayed locked, so I didn't message ${f.name}.") }) { launchWa(digits, msg, f.name!!, auto) }
            when {
                !ranNow -> "Your phone is locked. Unlock it and I'll message ${f.name}."
                auto -> "Messaging ${f.name} on WhatsApp."
                else -> "The message to ${f.name} is ready in WhatsApp. Tap send, or turn on my send helper in Accessibility."
            }
        }
        if (ask()) Result("Send \"$msg\" to ${f.name} on WhatsApp. Say yes to confirm.", act) else Result(act())
    }

    private fun youtube(q: String): Result {
        val id = AiClient.ytId(c, q)
        val search = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))
        goUi(Intent(Intent.ACTION_VIEW, if (id != null) Uri.parse("vnd.youtube:$id") else search).setPackage("com.google.android.youtube"),
            Intent(Intent.ACTION_VIEW, search))
        return Result(if (id != null) "Playing $q on YouTube" else "Showing YouTube results for $q. Tap the first one.")
    }

    private fun spotify(q: String): Result {
        if (c.packageManager.getLaunchIntentForPackage("com.spotify.music") == null) return Result("Spotify isn't installed.")
        goUi(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(q))))
        return Result("Searching Spotify for $q. Tap to play.")
    }

    private fun alarm(g: List<String>): String {
        var h = g[1].toInt(); val m = g[2].ifEmpty { "0" }.toInt()
        if (g[3] == "pm" && h < 12) h += 12
        if (g[3] == "am" && h == 12) h = 0
        go(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "Setting an alarm for $h:${m.toString().padStart(2, '0')}"
    }

    private fun timer(g: List<String>): String {
        val s = g[1].toInt() * when (g[2]) { "hour" -> 3600; "minute" -> 60; else -> 1 }
        go(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "Starting a ${g[1]} ${g[2]} timer"
    }

    private fun open(name: String): String {
        val pm = c.packageManager
        val app = pm.getInstalledApplications(0).firstOrNull {
            pm.getLaunchIntentForPackage(it.packageName) != null && pm.getApplicationLabel(it).toString().lowercase().contains(name)
        } ?: return "I couldn't find an app called $name."
        goUi(pm.getLaunchIntentForPackage(app.packageName)!!)
        return "Opening $name"
    }

    private fun torch(on: Boolean): String {
        val cm = c.getSystemService(CameraManager::class.java)
        cm.setTorchMode(cm.cameraIdList[0], on)
        return if (on) "Flashlight on" else "Flashlight off"
    }

    private fun vol(d: Int): String {
        c.getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
        return ""
    }

    private fun nav(place: String): String {
        goUi(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place))).setPackage("com.google.android.apps.maps"))
        return "Starting navigation to $place"
    }
}
