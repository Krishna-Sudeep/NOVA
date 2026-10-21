package com.nova.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

// Optional helper: after Nova opens a WhatsApp chat with your message, it taps WhatsApp's Send button
// and reports back whether it managed to tap it. Only runs inside WhatsApp, only for 20 seconds.
class SendService : AccessibilityService() {
    companion object {
        @Volatile private var armedUntil = 0L
        private var cb: ((Boolean) -> Unit)? = null
        private val h = Handler(Looper.getMainLooper())
        private val timeout = Runnable { fire(false) }

        @Synchronized private fun fire(ok: Boolean) {
            val f = cb; cb = null; armedUntil = 0; h.removeCallbacks(timeout); f?.invoke(ok)
        }

        fun arm(onDone: (Boolean) -> Unit) {
            cb = onDone; armedUntil = System.currentTimeMillis() + 20000
            h.removeCallbacks(timeout); h.postDelayed(timeout, 20000)
        }

        fun enabled(c: Context): Boolean =
            (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains("SendService")
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (System.currentTimeMillis() > armedUntil) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != "com.whatsapp") return
        val btn: AccessibilityNodeInfo? = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Send").firstOrNull { it.isClickable }
        if (btn != null && btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) fire(true)
    }

    override fun onInterrupt() {}
}
