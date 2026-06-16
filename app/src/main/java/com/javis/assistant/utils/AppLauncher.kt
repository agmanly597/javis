package com.javis.assistant.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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

    data class InstalledApp(val packageName: String, val label: String)

    // Cache of installed apps — refreshed on demand
    @Volatile
    private var installedAppsCache: List<InstalledApp> = emptyList()
    private var cacheTimestamp = 0L
    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    /** Scans device for all launchable installed apps */
    fun getInstalledApps(context: Context): List<InstalledApp> {
        val now = System.currentTimeMillis()
        if (now - cacheTimestamp < CACHE_TTL_MS && installedAppsCache.isNotEmpty()) {
            return installedAppsCache
        }
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                // Only include apps that have a launcher intent (i.e. can be opened)
                pm.getLaunchIntentForPackage(app.packageName) != null &&
                app.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .map { app ->
                InstalledApp(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label }

        installedAppsCache = apps
        cacheTimestamp = now
        return apps
    }

    /** Fuzzy-find an installed app by name — handles typos and partial matches */
    fun findInstalledApp(context: Context, query: String): InstalledApp? {
        val apps = getInstalledApps(context)
        val q = query.lowercase().trim()
        if (q.isBlank()) return null

        // Exact match
        apps.find { it.label.lowercase() == q }?.let { return it }
        // Starts with
        apps.find { it.label.lowercase().startsWith(q) }?.let { return it }
        // Contains
        apps.find { it.label.lowercase().contains(q) }?.let { return it }
        // Query contains app label
        apps.find { q.contains(it.label.lowercase()) }?.let { return it }
        // Package name contains query
        apps.find { it.packageName.lowercase().contains(q.replace(" ", "")) }?.let { return it }
        return null
    }

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
            val intent: Intent? = when {
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
                action.`package` != null ->
                    context.packageManager.getLaunchIntentForPackage(action.`package`) ?: return false
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

    /** Launch an app by its spoken name — searches installed apps dynamically */
    fun launchByName(context: Context, appName: String): String {
        val app = findInstalledApp(context, appName) ?: return ""
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                ?: return ""
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            app.label
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Fast-path command parser — handles obvious app open commands.
     * Returns a JSON action string or null if not a launch command.
     * Also handles dynamic app lookup for unknown apps.
     */
    fun parseNaturalLanguageCommand(input: String, context: Context? = null): String? {
        val lower = input.lowercase().trim()
        val openPrefixes = listOf("open ", "launch ", "start ", "go to ", "show me ")
        val isOpenCommand = openPrefixes.any { lower.startsWith(it) }

        // Extract the app name from the command
        val appNameQuery = if (isOpenCommand) {
            openPrefixes.fold(lower) { acc, prefix -> if (acc.startsWith(prefix)) acc.removePrefix(prefix) else acc }
        } else lower

        // Hardcoded fast-paths for known apps
        val hardcoded = matchHardcoded(lower)
        if (hardcoded != null) return hardcoded

        // Dynamic lookup — search installed apps
        if (context != null && isOpenCommand && appNameQuery.isNotBlank()) {
            val app = findInstalledApp(context, appNameQuery)
            if (app != null) {
                return """{"action":"LAUNCH_APP","package":"${app.packageName}","label":"${app.label}"}"""
            }
        }

        return null
    }

    private fun matchHardcoded(lower: String): String? = when {
        lower.containsAny("whatsapp", "whats app", "wassap") ->
            """{"action":"LAUNCH_APP","package":"com.whatsapp","label":"WhatsApp"}"""
        lower.containsAny("telegram") ->
            """{"action":"LAUNCH_APP","package":"org.telegram.messenger","label":"Telegram"}"""
        lower.containsAny("instagram", "insta") ->
            """{"action":"LAUNCH_APP","package":"com.instagram.android","label":"Instagram"}"""
        lower.containsAny("twitter", "x app", " x ") ->
            """{"action":"LAUNCH_APP","package":"com.twitter.android","label":"Twitter"}"""
        lower.containsAny("facebook", "fb") ->
            """{"action":"LAUNCH_APP","package":"com.facebook.katana","label":"Facebook"}"""
        lower.containsAny("snapchat", "snap") ->
            """{"action":"LAUNCH_APP","package":"com.snapchat.android","label":"Snapchat"}"""
        lower.containsAny("tiktok", "tik tok") ->
            """{"action":"LAUNCH_APP","package":"com.zhiliaoapp.musically","label":"TikTok"}"""
        lower.containsAny("gmail", "google mail") ->
            """{"action":"LAUNCH_APP","package":"com.google.android.gm","label":"Gmail"}"""
        lower.containsAny("zoom") ->
            """{"action":"LAUNCH_APP","package":"us.zoom.videomeetings","label":"Zoom"}"""
        lower.containsAny("youtube", "you tube", "yt") ->
            """{"action":"LAUNCH_APP","package":"com.google.android.youtube","label":"YouTube"}"""
        lower.containsAny("netflix") ->
            """{"action":"LAUNCH_APP","package":"com.netflix.mediaclient","label":"Netflix"}"""
        lower.containsAny("spotify") ->
            """{"action":"LAUNCH_APP","package":"com.spotify.music","label":"Spotify"}"""
        lower.containsAny("chrome", "browser") ->
            """{"action":"LAUNCH_APP","package":"com.android.chrome","label":"Chrome"}"""
        lower.containsAny("maps", "google maps", "navigation", "navigate") ->
            """{"action":"LAUNCH_APP","package":"com.google.android.apps.maps","label":"Google Maps"}"""
        lower.containsAny("calendar") ->
            """{"action":"LAUNCH_APP","package":"com.google.android.calendar","label":"Calendar"}"""
        lower.containsAny("play store", "app store", "playstore") ->
            """{"action":"LAUNCH_APP","package":"com.android.vending","label":"Play Store"}"""
        lower.containsAny("camera", "take photo", "take picture", "selfie") ->
            """{"action":"LAUNCH_APP","intentAction":"android.media.action.IMAGE_CAPTURE","label":"Camera"}"""
        lower.containsAny("calculator", "calc") ->
            """{"action":"LAUNCH_APP","package":"com.android.calculator2","label":"Calculator"}"""
        lower.containsAny("settings", "setting") ->
            """{"action":"LAUNCH_APP","intentAction":"android.settings.SETTINGS","label":"Settings"}"""
        lower.containsAny("contacts", "contact list") ->
            """{"action":"LAUNCH_APP","package":"com.android.contacts","label":"Contacts"}"""
        lower.containsAny("file manager", "files", "downloads") ->
            """{"action":"LAUNCH_APP","package":"com.android.documentsui","label":"Files"}"""
        lower.containsAny("gallery", "photos", "pictures", "images") ->
            """{"action":"LAUNCH_APP","package":"com.android.gallery3d","label":"Gallery"}"""
        lower.containsAny("clock", "alarm", "timer") ->
            """{"action":"LAUNCH_APP","package":"com.android.deskclock","label":"Clock"}"""
        lower.containsAny("dial", "call", "phone") ->
            """{"action":"LAUNCH_APP","intentAction":"android.intent.action.DIAL","label":"Phone"}"""
        lower.containsAny("amazon") ->
            """{"action":"LAUNCH_APP","package":"com.amazon.mShop.android.shopping","label":"Amazon"}"""
        lower.containsAny("paypal") ->
            """{"action":"LAUNCH_APP","package":"com.paypal.android.p2pmobile","label":"PayPal"}"""
        lower.containsAny("uber") ->
            """{"action":"LAUNCH_APP","package":"com.ubercab","label":"Uber"}"""
        lower.containsAny("messages", "sms", "text message") ->
            """{"action":"LAUNCH_APP","package":"com.google.android.apps.messaging","label":"Messages"}"""
        else -> null
    }

    private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }
}
