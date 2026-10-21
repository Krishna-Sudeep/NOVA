package com.nova.assistant

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("nova", Context.MODE_PRIVATE)
    fun get(c: Context, k: String, d: String = "") = sp(c).getString(k, d) ?: d
    fun set(c: Context, k: String, v: String) = sp(c).edit().putString(k, v).apply()
    fun name(c: Context) = get(c, "name", "Nova").ifBlank { "Nova" }
}
