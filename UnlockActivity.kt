package com.nova.assistant

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle

// Invisible screen that appears over the lock screen and asks Android to unlock.
class UnlockActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true); setTurnScreenOn(true)
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) { LockGate.fire(this); finish(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { LockGate.fire(this@UnlockActivity); finish() }
            override fun onDismissCancelled() { LockGate.fail(this@UnlockActivity); finish() }
            override fun onDismissError() { LockGate.fail(this@UnlockActivity); finish() }
        })
    }
}
