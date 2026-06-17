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
 * Accessibility service — gives JAVIS eyes and hands inside other apps:
 * • WhatsApp typing + send
 * • Type in any focused search bar / text field
 * • Answer incoming phone calls
 * • Track which app is currently active
 */
class JavisAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    enum class WhatsAppState { IDLE, IN_CHAT_READY_TO_TYPE, MESSAGE_TYPED, SENT }
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

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.className?.toString()?.contains("Activity") == true) {
            _currentApp.value = pkg
        }

        // WhatsApp chat typing flow
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

    fun prepareToTypeInWhatsApp(message: String, onTyped: (String) -> Unit) {
        pendingMessage = message
        waState = WhatsAppState.IN_CHAT_READY_TO_TYPE
        onMessageTyped = onTyped
        handler.postDelayed({ attemptTypeMessage() }, 1500)
    }

    private fun attemptTypeMessage() {
        val msg = pendingMessage ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in WHATSAPP_PACKAGES) return

        val inputField = root.findByViewId("entry")
            ?: root.findByViewId("message_input")
            ?: root.findByClassName("android.widget.EditText")

        inputField?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, msg)
            }
            val typed = it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (typed) {
                waState = WhatsAppState.MESSAGE_TYPED
                onMessageTyped?.invoke(msg)
            }
        }
    }

    fun tapSendInWhatsApp(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() !in WHATSAPP_PACKAGES) return false

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

    // ─── Type in Any Focused Field ────────────────────────────────────────────

    /**
     * Types [text] into whatever text field currently has focus on screen.
     * Works in search bars, browsers, any app.
     */
    fun typeInFocusedField(text: String): Boolean {
        val root = rootInActiveWindow ?: return false

        // Try focused input first
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val bundle = Bundle()
            bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        }

        // Find any visible EditText
        val editText = root.findByClassName("android.widget.EditText")
            ?: root.findByClassName("android.widget.SearchView\$SearchAutoComplete")
        if (editText != null) {
            editText.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            handler.postDelayed({
                val bundle = Bundle()
                bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            }, 300)
            return true
        }
        return false
    }

    /**
     * Tap the search/submit button after typing (looks for enter, go, search buttons).
     */
    fun tapSearchSubmit(): Boolean {
        val root = rootInActiveWindow ?: return false
        val btn = root.findByContentDescription("Search")
            ?: root.findByContentDescription("Go")
            ?: root.findByViewId("search_button")
            ?: root.findByViewId("go_button")
        return btn?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    // ─── Answer/Decline Calls ─────────────────────────────────────────────────

    /**
     * Answers an incoming call by finding and tapping the answer button.
     * Works on most Android phones including MIUI/Redmi.
     */
    fun answerCall(): Boolean {
        val root = rootInActiveWindow ?: return false

        // Try various known answer button IDs (MIUI, stock Android, OEM skins)
        val answerNode = root.findByViewId("answer_action_icon")
            ?: root.findByViewId("incoming_call_answer_button")
            ?: root.findByViewId("btn_accept")
            ?: root.findByViewId("answer")
            ?: root.findByContentDescription("Answer")
            ?: root.findByContentDescription("Accept")
            ?: root.findByContentDescription("answer call")
            ?: root.findByText("Answer")
            ?: root.findByText("Accept")

        return answerNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    /**
     * Declines an incoming call.
     */
    fun declineCall(): Boolean {
        val root = rootInActiveWindow ?: return false
        val declineNode = root.findByViewId("decline_action_icon")
            ?: root.findByViewId("decline")
            ?: root.findByViewId("btn_reject")
            ?: root.findByContentDescription("Decline")
            ?: root.findByContentDescription("Reject")
            ?: root.findByText("Decline")
        return declineNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    // ─── Node search helpers ───────────────────────────────────────────────────

    private fun AccessibilityNodeInfo.findByViewId(idFragment: String): AccessibilityNodeInfo? {
        val pkg = packageName?.toString() ?: return null
        return findAccessibilityNodeInfosByViewId("$pkg:id/$idFragment")?.firstOrNull()
            ?: run {
                for (p in listOf("com.whatsapp", "com.whatsapp.w4b", "com.android.server.telecom",
                    "com.miui.incallui", "com.android.incallui")) {
                    val r = findAccessibilityNodeInfosByViewId("$p:id/$idFragment")?.firstOrNull()
                    if (r != null) return@run r
                }
                null
            }
    }

    private fun AccessibilityNodeInfo.findByText(text: String): AccessibilityNodeInfo? {
        return findAccessibilityNodeInfosByText(text)?.firstOrNull()
    }

    private fun AccessibilityNodeInfo.findByContentDescription(desc: String): AccessibilityNodeInfo? {
        if (contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) return this
        for (i in 0 until childCount) {
            val f = getChild(i)?.findByContentDescription(desc)
            if (f != null) return f
        }
        return null
    }

    private fun AccessibilityNodeInfo.findByClassName(cls: String): AccessibilityNodeInfo? {
        if (className?.toString() == cls && isVisibleToUser) return this
        for (i in 0 until childCount) {
            val f = getChild(i)?.findByClassName(cls)
            if (f != null) return f
        }
        return null
    }

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
