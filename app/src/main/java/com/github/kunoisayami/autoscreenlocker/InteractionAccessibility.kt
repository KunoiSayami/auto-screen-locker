package com.github.kunoisayami.autoscreenlocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class InteractionAccessibility : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        LockService.resetTimer()
    }

    override fun onInterrupt() = Unit

    companion object {
        var isRunning = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
