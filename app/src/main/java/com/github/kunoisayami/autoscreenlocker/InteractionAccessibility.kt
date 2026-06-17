package com.github.kunoisayami.autoscreenlocker

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class InteractionAccessibility : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type in USER_INTERACTION_EVENTS) {
            Log.d(TAG, "User interaction detected: eventType=${AccessibilityEvent.eventTypeToString(type)}")
            LockService.resetTimer()
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "InteractionA11y"

        var isRunning = false
            private set

        private val USER_INTERACTION_EVENTS = setOf(
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START,
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        )
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
