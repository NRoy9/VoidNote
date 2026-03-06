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
 * UpdateCheckerManager — checks GitHub Releases for a newer version of the app.
 *
 * ─── HOW IT WORKS ────────────────────────────────────────────────────────────
 *
 * We use the public GitHub Releases API endpoint:
 *   GET https://api.github.com/repos/NRoy9/VoidNote/releases/latest
 *
 * The response is JSON. The only field we care about is `tag_name`, e.g.:
 *   { "tag_name": "v0.2.0-alpha", "html_url": "https://github.com/..." }
 *
 * We compare `tag_name` against the currently-installed version
 * (BuildConfig.VERSION_NAME, e.g. "0.1.0-alpha").
 *
 * COMPARISON LOGIC:
 * GitHub tags are expected to be "v{VERSION_NAME}" (e.g. "v0.1.0-alpha").
 * We strip the leading "v" from tag_name before comparing.
 * If they differ, we treat the GitHub version as the newer one and return it.
 *
 * ─── WHY NOT SEMANTIC VERSION COMPARISON? ────────────────────────────────────
 * During pre-Play Store alpha, we tag in chronological order. The latest
 * GitHub release is always the newest version. Simple string inequality is
 * sufficient and avoids a semver parsing dependency.
 *
 * ─── INTERNET PERMISSION ─────────────────────────────────────────────────────
 * This class requires:
 *   <uses-permission android:name="android.permission.INTERNET" />
 * in AndroidManifest.xml. If it's missing, the request will silently fail
 * and checkForUpdate() will return null (safe — no crash).
 *
 * ─── PLAY STORE MIGRATION ────────────────────────────────────────────────────
 * When the app is published to the Play Store, replace this class with
 * Google's official Play In-App Update library (AppUpdateManager). That
 * library checks the Play Store directly and handles forced/flexible updates.
 * This GitHub-based checker is only for the pre-Play Store alpha phase.
 *
 * ─── ERROR HANDLING ──────────────────────────────────────────────────────────
 * Any network failure, JSON parse error, or unexpected response returns null.
 * The caller treats null as "no update available" — the user sees nothing.
 * We never crash or show an error for a failed update check.
 */
@Singleton
class UpdateCheckerManager @Inject constructor() {

    companion object {
        // GitHub API endpoint for the latest release in the VoidNote repo
        private const val GITHUB_API_URL =
            "https://api.github.com/repos/NRoy9/VoidNote/releases/latest"

        // Timeout values in milliseconds
        private const val CONNECT_TIMEOUT_MS = 5_000   // 5 seconds to connect
        private const val READ_TIMEOUT_MS    = 8_000   // 8 seconds to read the response

        private const val TAG = "UpdateChecker"
    }

    /**
     * Check if a newer version is available on GitHub.
     *
     * Runs on [Dispatchers.IO] — safe to call from a coroutine in any ViewModel.
     *
     * @param currentVersion  The currently installed version string, e.g. "0.1.0-alpha".
     *                        Pass BuildConfig.VERSION_NAME from the caller.
     *
     * @return [UpdateInfo] if a newer version is available, or null if:
     *         - The installed version matches the latest GitHub release
     *         - The network call failed for any reason
     *         - The JSON response was malformed
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = (URL(GITHUB_API_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout    = READ_TIMEOUT_MS
                    requestMethod  = "GET"
                    // GitHub API requires a User-Agent header
                    setRequestProperty("User-Agent", "VoidNote-Android/$currentVersion")
                    // Tell GitHub we accept JSON
                    setRequestProperty("Accept", "application/vnd.github+json")
                }

                // Only proceed if the server responded with HTTP 200
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "GitHub API returned HTTP $responseCode — skipping update check")
                    return@withContext null
                }

                // Read the entire response body as a String
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                // Parse JSON — we only need two fields
                val json       = JSONObject(responseBody)
                val tagName    = json.optString("tag_name", "")    // e.g. "v0.2.0-alpha"
                val downloadUrl = json.optString("html_url", "")   // e.g. GitHub release page URL

                if (tagName.isBlank()) {
                    Log.w(TAG, "GitHub response had no tag_name — skipping")
                    return@withContext null
                }

                // Strip the leading "v" from the GitHub tag to get a bare version string
                // "v0.2.0-alpha" → "0.2.0-alpha"
                val latestVersion = tagName.removePrefix("v")

                // Strip -DEBUG suffix from debug builds before comparing
                // "0.2.0-alpha-DEBUG" → "0.2.0-alpha"
                val normalizedCurrent = currentVersion.removeSuffix("-DEBUG")

                // Only show banner if GitHub is strictly NEWER than what's installed.
                // If local build is ahead of the latest release (e.g. dev build),
                // isNewerVersion() returns false and we skip the banner.
                if (!isNewerVersion(latestVersion, normalizedCurrent)) {
                    Log.d(TAG, "No update needed — local: $normalizedCurrent, remote: $latestVersion")
                    return@withContext null
                }

                // A different version is on GitHub — surface it to the UI
                Log.i(TAG, "Update available: $currentVersion → $latestVersion")
                UpdateInfo(
                    latestVersion = latestVersion,   // e.g. "0.2.0-alpha"
                    tagName       = tagName,         // e.g. "v0.2.0-alpha" (for dismiss key)
                    downloadUrl   = downloadUrl      // GitHub release page URL
                )

            } catch (e: Exception) {
                // Network unavailable, DNS failure, SSL error, JSON parse error, etc.
                // Swallow silently — update check is non-critical.
                Log.d(TAG, "Update check failed (non-critical): ${e.message}")
                null
            }
        }
    }

    /**
     * Returns true only if [remote] is strictly newer than [local].
     * Compares each numeric segment (e.g. "0.2.0" → [0, 2, 0]).
     * The suffix after "-" (e.g. "-alpha") is ignored for ordering.
     *
     * Examples:
     *   "0.2.0" vs "0.1.0" → true  (remote is newer → show banner)
     *   "0.1.0" vs "0.2.0" → false (local is ahead  → no banner)
     *   "0.2.0" vs "0.2.0" → false (same version    → no banner)
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
 * UpdateInfo — data returned when a newer version is available.
 *
 * @param latestVersion  Bare version string, e.g. "0.2.0-alpha". Used for display.
 * @param tagName        Full tag as it appears in GitHub, e.g. "v0.2.0-alpha".
 *                       Used as the dismiss key in PreferencesManager so the banner
 *                       doesn't re-appear for the same version.
 * @param downloadUrl    The HTML URL of the GitHub release page. Opened in browser
 *                       when the user taps "Download".
 */
data class UpdateInfo(
    val latestVersion: String,
    val tagName: String,
    val downloadUrl: String
)