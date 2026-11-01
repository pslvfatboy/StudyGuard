package com.studyguard.app

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Pulls the blocklist from a public raw-text URL (e.g. a raw.githubusercontent.com link
 * to a file in a public GitHub repo). Expected format: one Android package name per line,
 * '#' starts a comment, blank lines ignored.
 *
 * Example file contents:
 *   # YouTube and Chrome
 *   com.google.android.youtube
 *   com.android.chrome
 *   com.instagram.android
 */
object BlocklistFetcher {
    private const val TAG = "StudyGuard.Fetch"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Fetches synchronously. Call this from a background thread. Returns null on failure. */
    fun fetch(url: String): Set<String>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fetch failed: HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                parse(body)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Fetch error: ${e.message}")
            null
        }
    }

    private fun parse(text: String): Set<String> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }

    /** Refreshes the cached blocklist in Prefs if a URL is configured. Safe to call on a background thread. */
    fun refresh(ctx: Context) {
        val url = Prefs.getBlocklistUrl(ctx) ?: return
        val fetched = fetch(url) ?: return
        Prefs.setBlockedApps(ctx, fetched)
        Log.i(TAG, "Blocklist updated: ${fetched.size} app(s)")
    }
}
