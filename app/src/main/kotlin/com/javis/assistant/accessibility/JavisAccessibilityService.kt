package com.javis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * JAVIS Accessibility Service
 *
 * Provides the backbone for:
 * - Global shortcut button (Android Accessibility Shortcut / Floating Button)
 * - WhatsApp message typing and sending via UI automation
 * - Reading any app's on-screen text
 * - Detecting the active foreground app
 * - Executing back/home/recents gestures
 */
class JavisAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: JavisAccessibilityService? = null

        fun getInstance(): JavisAccessibilityService? = instance

        private val _isEnabled = MutableStateFlow(false)
        val isEnabled: StateFlow<Boolean> = _isEnabled

        private val _activePackage = MutableStateFlow("")
        val activePackage: StateFlow<String> = _activePackage

        // ── WhatsApp helpers ──────────────────────────────────────────────
        const val WA_PACKAGE = "com.whatsapp"

        fun isWhatsAppActive() = _activePackage.value == WA_PACKAGE
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isEnabled.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { pkg ->
                _activePackage.value = pkg
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        _isEnabled.value = false
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public API called by the ViewModel / Repository
    // ─────────────────────────────────────────────────────────────────────

    /** Trigger the global back action */
    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    /** Trigger the home button */
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Open recents */
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /**
     * Type text into the currently-focused input field.
     * Works in any app that uses standard Android inputs.
     */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = findFocusedEditText(root) ?: findFirstEditText(root) ?: return false
        val args = Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Append text to the current input field (preserves existing content) */
    fun appendText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = findFocusedEditText(root) ?: findFirstEditText(root) ?: return false
        val existing = focused.text?.toString() ?: ""
        val args = Bundle().apply {
            putString(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                if (existing.isEmpty()) text else "$existing $text"
            )
        }
        focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Click the first node whose content-description or text matches [label].
     * Used to press WhatsApp's Send button.
     */
    fun clickNodeWithLabel(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return findNodeByText(root, label)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ?: findNodeByContentDesc(root, label)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ?: false
    }

    /** Read all visible text on screen (flat string) */
    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        return buildString { collectText(root, this) }.trim()
    }

    /** Read WhatsApp conversation list (recent chats) */
    fun readWhatsAppChats(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<String>()
        findAllNodesByViewId(root, "$WA_PACKAGE:id/conversations_row_contact_name")
            .forEach { result.add(it.text?.toString() ?: "") }
        return result.filter { it.isNotBlank() }
    }

    /** Tap a WhatsApp chat row by contact name */
    fun openWhatsAppChatByName(name: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = findAllNodesWithText(root, name)
        nodes.forEach { node ->
            val parent = node.parent ?: node
            if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    /**
     * Full WhatsApp send flow:
     * 1. Tap on the message input
     * 2. Type the message
     * 3. Click the Send button
     */
    fun whatsAppSendMessage(message: String): Boolean {
        val root = rootInActiveWindow ?: return false

        // Find the text input (entry box)
        val entryBox = findNodeByViewId(root, "$WA_PACKAGE:id/entry")
            ?: findFirstEditText(root)
            ?: return false

        entryBox.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        entryBox.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val args = Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
        }
        val typed = entryBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!typed) return false

        // Give UI a moment to register the text
        Thread.sleep(300)

        // Click the send button
        val sendButton = findNodeByViewId(root, "$WA_PACKAGE:id/send")
            ?: findNodeByContentDesc(root, "Send")
            ?: findNodeByContentDesc(root, "send")
        return sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun findFocusedEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isFocused && root.className?.contains("EditText") == true) return root
        for (i in 0 until root.childCount) {
            findFocusedEditText(root.getChild(i) ?: continue)?.let { return it }
        }
        return null
    }

    private fun findFirstEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.className?.contains("EditText") == true) return root
        for (i in 0 until root.childCount) {
            findFirstEditText(root.getChild(i) ?: continue)?.let { return it }
        }
        return null
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes?.firstOrNull()
    }

    private fun findNodeByContentDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (root.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) return root
        for (i in 0 until root.childCount) {
            findNodeByContentDesc(root.getChild(i) ?: continue, desc)?.let { return it }
        }
        return null
    }

    private fun findNodeByViewId(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes?.firstOrNull()
    }

    private fun findAllNodesByViewId(root: AccessibilityNodeInfo, viewId: String): List<AccessibilityNodeInfo> {
        return root.findAccessibilityNodeInfosByViewId(viewId) ?: emptyList()
    }

    private fun findAllNodesWithText(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        return root.findAccessibilityNodeInfosByText(text) ?: emptyList()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i) ?: continue, sb)
        }
    }
}
