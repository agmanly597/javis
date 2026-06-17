package com.javis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Accessibility service that allows JAVIS to:
 * 1. Type messages in WhatsApp (tap-to-send flow)
 * 2. Tap send button after user confirms
 * 3. Track which app is currently open
 */
class JavisAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private enum class WhatsAppState {
        IDLE, IN_CHAT_READY_TO_TYPE, MESSAGE_TYPED, SENT
    }

    private var waState = WhatsAppState.IDLE
    private var pendingMessage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isEnabled.value = true

        serviceInfo = serviceInfo?.apply {
            flags = flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // Track the current foreground app
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.className?.toString()?.contains("Activity") == true) {
                _currentApp.value = pkg
            }
        }

        // WhatsApp chat is open — check if we have a pending message to type
        if (pkg in WHATSAPP_PACKAGES && waState == WhatsAppState.IN_CHAT_READY_TO_TYPE) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ attemptTypeMessage() }, 800)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isEnabled.value = false
    }

    // ─── WhatsApp Typing ──────────────────────────────────────────────────────

    /**
     * Called after WhatsApp is opened to a specific chat.
     * JAVIS will wait for the chat screen to appear, then type [message].
     * [onTyped] is called with the typed text once done.
     */
    fun prepareToTypeInWhatsApp(message: String, onTyped: (String) -> Unit) {
        pendingMessage = message
        waState = WhatsAppState.IN_CHAT_READY_TO_TYPE
        onMessageTyped = onTyped
        // Try typing immediately in case WhatsApp is already open to the chat
        handler.postDelayed({ attemptTypeMessage() }, 1500)
    }

    private fun attemptTypeMessage() {
        val msg = pendingMessage ?: return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg !in WHATSAPP_PACKAGES) return

        // Find message input field (WhatsApp uses this id)
        val inputField = root.findByViewId("entry")
            ?: root.findByViewId("message_input")
            ?: root.findByClassName("android.widget.EditText")

        if (inputField != null) {
            // Click to focus
            inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            // Set the text
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    msg
                )
            }
            val typed = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (typed) {
                waState = WhatsAppState.MESSAGE_TYPED
                onMessageTyped?.invoke(msg)
            }
        }
    }

    /**
     * Taps the WhatsApp send button. Call this after the user confirms with "send".
     */
    fun tapSendInWhatsApp(): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: return false
        if (pkg !in WHATSAPP_PACKAGES) return false

        val sendBtn = root.findByViewId("send")
            ?: root.findByViewId("send_button")
            ?: root.findByContentDescription("Send")

        return if (sendBtn != null) {
            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waState = WhatsAppState.SENT
            pendingMessage = null
            true
        } else false
    }

    fun resetWhatsAppState() {
        waState = WhatsAppState.IDLE
        pendingMessage = null
        handler.removeCallbacksAndMessages(null)
    }

    fun isWaitingForSend() = waState == WhatsAppState.MESSAGE_TYPED

    // ─── Node search helpers ───────────────────────────────────────────────────

    private fun AccessibilityNodeInfo.findByViewId(idFragment: String): AccessibilityNodeInfo? {
        val whatsappPkg = packageName?.toString() ?: "com.whatsapp"
        val fullId = "$whatsappPkg:id/$idFragment"
        return findAccessibilityNodeInfosByViewId(fullId)?.firstOrNull()
            ?: run {
                // Try both WhatsApp and Business
                for (pkg in WHATSAPP_PACKAGES) {
                    val r = findAccessibilityNodeInfosByViewId("$pkg:id/$idFragment")?.firstOrNull()
                    if (r != null) return@run r
                }
                null
            }
    }

    private fun AccessibilityNodeInfo.findByContentDescription(desc: String): AccessibilityNodeInfo? {
        if (contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) return this
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            val found = child.findByContentDescription(desc)
            if (found != null) return found
        }
        return null
    }

    private fun AccessibilityNodeInfo.findByClassName(cls: String): AccessibilityNodeInfo? {
        if (className?.toString() == cls) return this
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            val found = child.findByClassName(cls)
            if (found != null) return found
        }
        return null
    }

    // ─── Singleton ────────────────────────────────────────────────────────────

    companion object {
        val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        var instance: JavisAccessibilityService? = null
            private set

        var onMessageTyped: ((String) -> Unit)? = null

        private val _isEnabled = MutableStateFlow(false)
        val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

        private val _currentApp = MutableStateFlow("")
        val currentApp: StateFlow<String> = _currentApp.asStateFlow()
    }
}
