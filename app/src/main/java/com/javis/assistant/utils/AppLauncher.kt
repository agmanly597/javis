package com.javis.assistant.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.JsonObject

object AppLauncher {

    private val gson = Gson()

    data class LaunchAction(
        val action: String,
        val `package`: String? = null,
        val label: String? = null,
        val intentAction: String? = null,
        val data: String? = null
    )

    fun tryParseLaunchAction(response: String): LaunchAction? {
        return try {
            val jsonStart = response.indexOf('{')
            val jsonEnd = response.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) return null
            val json = response.substring(jsonStart, jsonEnd + 1)
            val obj = gson.fromJson(json, JsonObject::class.java)
            if (obj.get("action")?.asString == "LAUNCH_APP") {
                LaunchAction(
                    action = "LAUNCH_APP",
                    `package` = obj.get("package")?.asString,
                    label = obj.get("label")?.asString,
                    intentAction = obj.get("intentAction")?.asString,
                    data = obj.get("data")?.asString
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun extractTextFromResponse(response: String): String {
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')
        return if (jsonStart != -1 && jsonEnd != -1) {
            val before = response.substring(0, jsonStart).trim()
            val after = response.substring(jsonEnd + 1).trim()
            listOf(before, after).filter { it.isNotBlank() }.joinToString(" ")
        } else response
    }

    fun launchApp(context: Context, action: LaunchAction): Boolean {
        return try {
            val intent = when {
                action.intentAction == "android.media.action.IMAGE_CAPTURE" ->
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                action.intentAction == "android.settings.SETTINGS" ->
                    Intent(Settings.ACTION_SETTINGS)
                action.intentAction == "android.intent.action.DIAL" ->
                    Intent(Intent.ACTION_DIAL).apply {
                        if (!action.data.isNullOrBlank()) data = Uri.parse(action.data)
                    }
                action.intentAction == "android.intent.action.VIEW" && action.data != null ->
                    Intent(Intent.ACTION_VIEW, Uri.parse(action.data))
                action.intentAction != null ->
                    Intent(action.intentAction)
                action.`package` != null -> {
                    context.packageManager.getLaunchIntentForPackage(action.`package`)
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${action.`package`}"))
                }
                else -> null
            }
            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** Fast-path parse for obvious single-word app commands without needing AI */
    fun parseNaturalLanguageCommand(input: String): String? {
        val lower = input.lowercase().trim()
        return when {
            // Communication
            lower.containsAny("whatsapp", "whats app") ->
                """{"action":"LAUNCH_APP","package":"com.whatsapp","label":"WhatsApp"}"""
            lower.containsAny("telegram") ->
                """{"action":"LAUNCH_APP","package":"org.telegram.messenger","label":"Telegram"}"""
            lower.containsAny("instagram") ->
                """{"action":"LAUNCH_APP","package":"com.instagram.android","label":"Instagram"}"""
            lower.containsAny("twitter", "x app") ->
                """{"action":"LAUNCH_APP","package":"com.twitter.android","label":"Twitter"}"""
            lower.containsAny("facebook") ->
                """{"action":"LAUNCH_APP","package":"com.facebook.katana","label":"Facebook"}"""
            lower.containsAny("snapchat") ->
                """{"action":"LAUNCH_APP","package":"com.snapchat.android","label":"Snapchat"}"""
            lower.containsAny("tiktok", "tik tok") ->
                """{"action":"LAUNCH_APP","package":"com.zhiliaoapp.musically","label":"TikTok"}"""
            lower.containsAny("gmail") ->
                """{"action":"LAUNCH_APP","package":"com.google.android.gm","label":"Gmail"}"""
            lower.containsAny("messages", "sms", "text message") ->
                """{"action":"LAUNCH_APP","package":"com.google.android.apps.messaging","label":"Messages"}"""
            lower.containsAny("zoom") ->
                """{"action":"LAUNCH_APP","package":"us.zoom.videomeetings","label":"Zoom"}"""

            // Media & Entertainment
            lower.containsAny("youtube", "you tube") ->
                """{"action":"LAUNCH_APP","package":"com.google.android.youtube","label":"YouTube"}"""
            lower.containsAny("netflix") ->
                """{"action":"LAUNCH_APP","package":"com.netflix.mediaclient","label":"Netflix"}"""
            lower.containsAny("spotify") ->
                """{"action":"LAUNCH_APP","package":"com.spotify.music","label":"Spotify"}"""

            // Google apps
            lower.containsAny("chrome", "browser") ->
                """{"action":"LAUNCH_APP","package":"com.android.chrome","label":"Chrome"}"""
            lower.containsAny("maps", "google maps", "navigation", "navigate") ->
                """{"action":"LAUNCH_APP","package":"com.google.android.apps.maps","label":"Google Maps"}"""
            lower.containsAny("calendar") ->
                """{"action":"LAUNCH_APP","package":"com.google.android.calendar","label":"Calendar"}"""
            lower.containsAny("play store", "app store") ->
                """{"action":"LAUNCH_APP","package":"com.android.vending","label":"Play Store"}"""

            // Device
            lower.containsAny("camera", "take photo", "take picture", "selfie") ->
                """{"action":"LAUNCH_APP","intentAction":"android.media.action.IMAGE_CAPTURE","label":"Camera"}"""
            lower.containsAny("calculator", "calc") ->
                """{"action":"LAUNCH_APP","package":"com.android.calculator2","label":"Calculator"}"""
            lower.containsAny("settings", "setting") ->
                """{"action":"LAUNCH_APP","intentAction":"android.settings.SETTINGS","label":"Settings"}"""
            lower.containsAny("contacts", "contact list") ->
                """{"action":"LAUNCH_APP","package":"com.android.contacts","label":"Contacts"}"""
            lower.containsAny("files", "file manager", "downloads", "documents") ->
                """{"action":"LAUNCH_APP","package":"com.android.documentsui","label":"Files"}"""
            lower.containsAny("gallery", "photos", "pictures", "images") ->
                """{"action":"LAUNCH_APP","package":"com.android.gallery3d","label":"Gallery"}"""
            lower.containsAny("clock", "alarm", "timer") ->
                """{"action":"LAUNCH_APP","package":"com.android.deskclock","label":"Clock"}"""
            lower.containsAny("dial", "call", "phone") ->
                """{"action":"LAUNCH_APP","intentAction":"android.intent.action.DIAL","label":"Phone"}"""

            // Shopping
            lower.containsAny("amazon") ->
                """{"action":"LAUNCH_APP","package":"com.amazon.mShop.android.shopping","label":"Amazon"}"""
            lower.containsAny("paypal") ->
                """{"action":"LAUNCH_APP","package":"com.paypal.android.p2pmobile","label":"PayPal"}"""
            lower.containsAny("uber") ->
                """{"action":"LAUNCH_APP","package":"com.ubercab","label":"Uber"}"""

            else -> null
        }
    }

    private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }
}
