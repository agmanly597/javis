package com.javis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JavisAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                val className = event.className?.toString() ?: ""
                if (className.contains("Activity")) {
                    _currentApp.value = packageName
                }
            }
            else -> {}
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isEnabled.value = true
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isEnabled.value = false
    }

    companion object {
        var instance: JavisAccessibilityService? = null
            private set

        private val _isEnabled = MutableStateFlow(false)
        val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

        private val _currentApp = MutableStateFlow("")
        val currentApp: StateFlow<String> = _currentApp.asStateFlow()
    }
}
