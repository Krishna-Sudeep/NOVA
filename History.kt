package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Conversation memory, stored only in the app's private storage on your phone.
object History {
    private fun f(c: Context) = File(c.filesDir, "history.json")

    fun load(c: Context): JSONArray = try { JSONArray(f(c).readText()) } catch (e: Exception) { JSONArray() }

    fun add(c: Context, role: String, text: String) {
        val a = load(c)
        a.put(JSONObject().put("role", role).put("content", text))
        val out = JSONArray()
        for (i in maxOf(0, a.length() - 40) until a.length()) out.put(a.get(i))
        f(c).writeText(out.toString())
    }

    fun clear(c: Context) { f(c).delete() }

    fun export(c: Context): String {
        val a = load(c)
        return (0 until a.length()).joinToString("\n") {
            val o = a.getJSONObject(it); "${o.getString("role")}: ${o.getString("content")}"
        }
    }
}
