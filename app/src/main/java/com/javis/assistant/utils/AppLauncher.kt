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

    @Volatile private var installedAppsCache: List<InstalledApp> = emptyList()
    private var cacheTimestamp = 0L
    private const val CACHE_TTL_MS = 3 * 60 * 1000L

    /** Scans ALL launchable installed apps and caches them */
    fun getInstalledApps(context: Context): List<InstalledApp> {
        val now = System.currentTimeMillis()
        if (now - cacheTimestamp < CACHE_TTL_MS && installedAppsCache.isNotEmpty()) {
            return installedAppsCache
        }
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app -> pm.getLaunchIntentForPackage(app.packageName) != null }
            .map { app -> InstalledApp(packageName = app.packageName, label = pm.getApplicationLabel(app).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label }

        installedAppsCache = apps
        cacheTimestamp = now
        return apps
    }

    /** Invalidate cache — call when user installs/uninstalls apps */
    fun invalidateCache() { cacheTimestamp = 0L }

    /**
     * Scored fuzzy app finder — works for ANY app by name.
     * "opay" → OPay, "kuda" → Kuda, "palm pay" → PalmPay, etc.
     */
    fun findInstalledApp(context: Context, query: String): InstalledApp? {
        val apps = getInstalledApps(context)
        val q = query.lowercase().trim().replace("-", " ")
        if (q.isBlank()) return null

        data class ScoredApp(val app: InstalledApp, val score: Int)

        val qWords = q.split("\\s+".toRegex()).filter { it.length > 1 }
        val qNoSpaces = q.replace(" ", "")

        val scored = apps.mapNotNull { app ->
            val label = app.label.lowercase().replace("-", " ")
            val pkg = app.packageName.lowercase()
            val labelNoSpaces = label.replace(" ", "")

            val score = when {
                label == q -> 1000
                label.startsWith(q) -> 900
                label.contains(" $q") || label.contains("$q ") -> 850
                labelNoSpaces == qNoSpaces -> 820
                label.contains(q) -> 800
                q.contains(label) && label.length > 3 -> 780
                labelNoSpaces.startsWith(qNoSpaces) -> 760
                qNoSpaces.startsWith(labelNoSpaces) && labelNoSpaces.length > 3 -> 740
                pkg.contains(qNoSpaces) -> 600
                // All query words appear in label
                qWords.all { w -> label.contains(w) } && qWords.size > 1 -> 700
                // Most query words appear in label
                qWords.isNotEmpty() && qWords.count { w -> label.contains(w) }.toFloat() / qWords.size > 0.5f -> 500
                // Label words appear in query
                label.split(" ").filter { it.length > 2 }.any { w -> q.contains(w) } -> 400
                // Levenshtein check for short words
                label.length <= 8 && levenshtein(label, q) <= 2 -> 300
                else -> 0
            }
            if (score > 0) ScoredApp(app, score) else null
        }.sortedByDescending { it.score }

        return scored.firstOrNull()?.app
    }

    /** Simple Levenshtein distance for typo tolerance */
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
        }
        return dp[a.length][b.length]
    }

    /**
     * Try to find an app name embedded anywhere in the input.
     * Handles: "open opay", "can you launch kuda", "I want to use palm pay", etc.
     */
    fun resolveAppFromInput(input: String, context: Context): InstalledApp? {
        val lower = input.lowercase().trim()

        // Remove open/launch command words to extract app name
        val stripped = lower
            .replace(Regex("^(open|launch|start|go to|show me|take me to|bring up|use|run|i want to use|can you open|please open|can you launch)\\s+"), "")
            .replace(Regex("\\s+(app|application|for me|please|now)$"), "")
            .trim()

        if (stripped.isBlank() || stripped == lower) return null
        return findInstalledApp(context, stripped)
    }

    /** Build app list string for AI system prompt injection */
    fun buildAppListForPrompt(context: Context): String {
        val apps = getInstalledApps(context)
        return apps.joinToString(", ") { "${it.label} (${it.packageName})" }
    }

    /** Short label-only list for prompt (less tokens) */
    fun buildAppLabelList(context: Context): String {
        return getInstalledApps(context).joinToString(", ") { it.label }
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
        } catch (e: Exception) { null }
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
                action.intentAction != null -> Intent(action.intentAction)
                action.`package` != null ->
                    // Try exact package first, then fuzzy match
                    context.packageManager.getLaunchIntentForPackage(action.`package`)
                        ?: findInstalledApp(context, action.label ?: action.`package`)?.let {
                            context.packageManager.getLaunchIntentForPackage(it.packageName)
                        }
                        ?: return false
                else -> null
            }
            intent?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(it); true } ?: false
        } catch (e: Exception) { false }
    }

    /** Launch app by fuzzy name. Returns the actual app label if successful, empty string if not. */
    fun launchByName(context: Context, appName: String): String {
        val app = findInstalledApp(context, appName) ?: return ""
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return ""
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            app.label
        } catch (e: Exception) { "" }
    }

    /**
     * Fast-path: check if the entire input (of any form) is asking to open an installed app.
     * Returns JSON action string or null.
     */
    fun parseNaturalLanguageCommand(input: String, context: Context? = null): String? {
        val lower = input.lowercase().trim()

        // Quick hardcoded fast-paths (always work, even with no internet)
        val hardcoded = matchHardcoded(lower)
        if (hardcoded != null) return hardcoded

        // Dynamic lookup — works for ANY app including OPay, Kuda, PalmPay etc.
        if (context != null) {
            val app = resolveAppFromInput(input, context)
            if (app != null) {
                return """{"action":"LAUNCH_APP","package":"${app.packageName}","label":"${app.label}"}"""
            }
        }
        return null
    }

    private fun matchHardcoded(lower: String): String? = when {
        lower.containsAny("whatsapp", "whats app", "wassap", "whatsap") ->
            """{"action":"LAUNCH_APP","package":"com.whatsapp","label":"WhatsApp"}"""
        lower.containsAny("telegram") ->
            """{"action":"LAUNCH_APP","package":"org.telegram.messenger","label":"Telegram"}"""
        lower.containsAny("instagram", "insta") ->
            """{"action":"LAUNCH_APP","package":"com.instagram.android","label":"Instagram"}"""
        lower.containsAny("twitter", " x app", "open x") ->
            """{"action":"LAUNCH_APP","package":"com.twitter.android","label":"Twitter/X"}"""
        lower.containsAny("facebook", " fb ") ->
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
        lower.containsAny("google maps", " maps", "navigate", "directions") ->
            """{"action":"LAUNCH_APP","package":"com.google.android.apps.maps","label":"Google Maps"}"""
        lower.containsAny("calendar") ->
            """{"action":"LAUNCH_APP","package":"com.google.android.calendar","label":"Calendar"}"""
        lower.containsAny("play store", "app store", "playstore") ->
            """{"action":"LAUNCH_APP","package":"com.android.vending","label":"Play Store"}"""
        lower.containsAny("camera", "take photo", "take picture", "selfie") ->
            """{"action":"LAUNCH_APP","intentAction":"android.media.action.IMAGE_CAPTURE","label":"Camera"}"""
        lower.containsAny("calculator", " calc ") ->
            """{"action":"LAUNCH_APP","package":"com.android.calculator2","label":"Calculator"}"""
        lower.containsAny("settings", "setting") ->
            """{"action":"LAUNCH_APP","intentAction":"android.settings.SETTINGS","label":"Settings"}"""
        lower.containsAny("contacts", "contact list", "address book") ->
            """{"action":"LAUNCH_APP","package":"com.android.contacts","label":"Contacts"}"""
        lower.containsAny("file manager", " files ", "downloads") ->
            """{"action":"LAUNCH_APP","package":"com.android.documentsui","label":"Files"}"""
        lower.containsAny("gallery", "photos", "pictures") ->
            """{"action":"LAUNCH_APP","package":"com.android.gallery3d","label":"Gallery"}"""
        lower.containsAny("clock", "open alarm", "open timer") ->
            """{"action":"LAUNCH_APP","package":"com.android.deskclock","label":"Clock"}"""
        lower.containsAny("amazon") ->
            """{"action":"LAUNCH_APP","package":"com.amazon.mShop.android.shopping","label":"Amazon"}"""
        lower.containsAny("paypal") ->
            """{"action":"LAUNCH_APP","package":"com.paypal.android.p2pmobile","label":"PayPal"}"""
        lower.containsAny("uber") ->
            """{"action":"LAUNCH_APP","package":"com.ubercab","label":"Uber"}"""
        else -> null
    }

    private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }
}
