package com.greenicephoenix.voidnote.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UpdateCheckerManager — checks a hosted version.json for a newer version of the app.
 *
 * ─── HOW IT WORKS ────────────────────────────────────────────────────────────
 *
 * We fetch a JSON file hosted on Cloudflare Pages:
 *   GET https://voidnote.pages.dev/version.json
 *
 * The file format:
 *   { "version": "1.1.0", "apkUrl": "https://voidnote.pages.dev/voidnote.apk" }
 *
 * We compare `version` against the currently-installed version.
 * If the hosted version is strictly newer, we surface it to the UI.
 *
 * ─── UPDATING A RELEASE ──────────────────────────────────────────────────────
 * 1. Upload the new APK as `voidnote.apk` to Cloudflare Pages
 * 2. Update `version` in version.json to the new version string
 * 3. Purge Cloudflare cache
 * The download button in the app will always point to voidnote.pages.dev/voidnote.apk
 *
 * ─── PLAY STORE MIGRATION ────────────────────────────────────────────────────
 * When published to Play Store, replace this class with AppUpdateManager
 * (Google's official in-app update library). This checker is for the
 * direct-APK distribution phase only.
 */
@Singleton
class UpdateCheckerManager @Inject constructor() {

    companion object {
        private const val VERSION_JSON_URL = "https://voidnote.pages.dev/version.json"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS    = 8_000
        private const val TAG = "UpdateChecker"
    }

    /**
     * Check if a newer version is available.
     *
     * @param currentVersion  The installed version string, e.g. "1.0.2".
     *                        Pass BuildConfig.VERSION_NAME from the caller.
     * @return [UpdateInfo] if a newer version is available, null otherwise.
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = (URL(VERSION_JSON_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout    = READ_TIMEOUT_MS
                    requestMethod  = "GET"
                    setRequestProperty("User-Agent", "VoidNote-Android/$currentVersion")
                    setRequestProperty("Accept", "application/json")
                    // Bypass Cloudflare cache for update checks
                    setRequestProperty("Cache-Control", "no-cache")
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "version.json returned HTTP ${connection.responseCode}")
                    return@withContext null
                }

                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json       = JSONObject(responseBody)
                val latestVersion = json.optString("version", "").trim()
                val apkUrl     = json.optString("apkUrl", "").trim()

                if (latestVersion.isBlank()) {
                    Log.w(TAG, "version.json had no version field")
                    return@withContext null
                }

                // Strip -DEBUG suffix from debug builds before comparing
                val normalizedCurrent = currentVersion.removeSuffix("-DEBUG")

                if (!isNewerVersion(latestVersion, normalizedCurrent)) {
                    Log.d(TAG, "No update — local: $normalizedCurrent, remote: $latestVersion")
                    return@withContext null
                }

                Log.i(TAG, "Update available: $normalizedCurrent → $latestVersion")
                UpdateInfo(
                    latestVersion = latestVersion,
                    downloadUrl   = apkUrl.ifBlank { "https://voidnote.pages.dev" }
                )

            } catch (e: Exception) {
                Log.d(TAG, "Update check failed (non-critical): ${e.message}")
                null
            }
        }
    }

    /**
     * Returns true only if [remote] is strictly newer than [local].
     * Compares each numeric segment (e.g. "1.1.0" vs "1.0.2").
     * Suffix after "-" is ignored for ordering.
     */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        fun parts(v: String) = v.substringBefore("-")
            .split(".")
            .mapNotNull { it.toIntOrNull() }

        val r = parts(remote)
        val l = parts(local)
        val len = maxOf(r.size, l.size)

        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}

/**
 * UpdateInfo — returned when a newer version is available.
 *
 * NOTE: tagName field removed (was the GitHub tag). We now use
 * latestVersion directly as the dismiss key in PreferencesManager.
 *
 * @param latestVersion  Version string from version.json, e.g. "1.1.0"
 * @param downloadUrl    Direct APK download URL from version.json
 */
data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String
)