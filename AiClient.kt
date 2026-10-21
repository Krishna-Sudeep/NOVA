package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Talks to YOUR backend (see SETUP.md). The AI key never lives in this app.
object AiClient {
    fun ask(c: Context, text: String): String {
        val url = Prefs.get(c, "url")
        if (url.isBlank()) throw Exception("Please add my backend address in Settings.")
        val h = History.load(c)
        val list = ArrayList<JSONObject>()
        for (i in maxOf(0, h.length() - 20) until h.length()) list.add(h.getJSONObject(i))
        while (list.isNotEmpty() && list[0].getString("role") != "user") list.removeAt(0)
        list.add(JSONObject().put("role", "user").put("content", text))
        val body = JSONObject().put("name", Prefs.name(c)).put("messages", JSONArray(list))
        try {
            val con = URL(url).openConnection() as HttpURLConnection
            con.requestMethod = "POST"; con.connectTimeout = 15000; con.readTimeout = 30000; con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json")
            con.setRequestProperty("X-Nova-Token", Prefs.get(c, "token"))
            con.outputStream.use { it.write(body.toString().toByteArray()) }
            if (con.responseCode != 200) throw Exception("Backend error ${con.responseCode}")
            return JSONObject(con.inputStream.bufferedReader().readText()).optString("reply", "")
                .ifBlank { throw Exception("Empty answer") }
        } catch (e: Exception) {
            throw Exception(if (e.message?.startsWith("Backend") == true) e.message!! else "I couldn't reach my brain.")
        }
    }

    // Finds the first YouTube video for a search (needs the optional YouTube key on the backend). Null = not available.
    fun ytId(c: Context, q: String): String? {
        val url = Prefs.get(c, "url")
        if (url.isBlank()) return null
        return try {
            val con = URL(url + "?yt=" + URLEncoder.encode(q, "UTF-8")).openConnection() as HttpURLConnection
            con.connectTimeout = 8000; con.readTimeout = 8000
            con.setRequestProperty("X-Nova-Token", Prefs.get(c, "token"))
            if (con.responseCode != 200) null else {
                val id = JSONObject(con.inputStream.bufferedReader().readText()).optString("id", "")
                if (id.isBlank() || id == "null") null else id
            }
        } catch (e: Exception) { null }
    }
}
