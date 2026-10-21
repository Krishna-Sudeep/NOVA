package com.nova.assistant

import android.app.*
import android.content.Context
import android.content.Intent

// Android never lets an app open WhatsApp/YouTube over a locked screen. This asks the system to unlock first
// (instant if you have no lock or Smart Lock; otherwise your face/fingerprint/PIN), then continues the request.
object LockGate {
    @Volatile private var action: (() -> Unit)? = null
    @Volatile private var failure: (() -> Unit)? = null
    @Volatile private var at = 0L

    fun locked(c: Context) = c.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    // Runs act() now if unlocked. If locked, asks for unlock first and returns false.
    fun run(c: Context, onFail: () -> Unit, act: () -> Unit): Boolean {
        if (!locked(c)) { act(); return true }
        action = act; failure = onFail; at = System.currentTimeMillis()
        val i = Intent(c, UnlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nova_unlock", "Unlock requests", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(2, Notification.Builder(c, "nova_unlock").setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Nova: unlock to continue").setContentText("Tap to unlock and finish your request")
            .setCategory(Notification.CATEGORY_CALL).setFullScreenIntent(pi, true).setAutoCancel(true).build())
        try { c.startActivity(i) } catch (e: Exception) { }
        return false
    }

    fun fire(c: Context) {
        cancel(c)
        val a = action; val f = failure; action = null; failure = null
        if (System.currentTimeMillis() - at > 90000) f?.invoke() else a?.invoke()   // too old = don't surprise the user
    }

    fun fail(c: Context) { cancel(c); val f = failure; action = null; failure = null; f?.invoke() }
    private fun cancel(c: Context) = c.getSystemService(NotificationManager::class.java).cancel(2)
}
