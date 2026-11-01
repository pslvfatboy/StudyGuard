package com.studyguard.app

import android.content.Context
import java.security.MessageDigest

/**
 * Simple SharedPreferences wrapper. The PIN is stored as a SHA-256 hash, never in plaintext.
 */
object Prefs {
    private const val FILE = "studyguard_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_BLOCKLIST_URL = "blocklist_url"
    private const val KEY_BLOCKED_APPS = "blocked_apps" // newline-separated package names
    private const val KEY_LAST_FETCH = "last_fetch_at"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun setPin(ctx: Context, pin: String) {
        prefs(ctx).edit().putString(KEY_PIN_HASH, sha256(pin)).apply()
    }

    fun hasPin(ctx: Context): Boolean = prefs(ctx).contains(KEY_PIN_HASH)

    fun checkPin(ctx: Context, candidate: String): Boolean {
        val stored = prefs(ctx).getString(KEY_PIN_HASH, null) ?: return false
        return stored == sha256(candidate)
    }

    fun setBlocklistUrl(ctx: Context, url: String) {
        prefs(ctx).edit().putString(KEY_BLOCKLIST_URL, url).apply()
    }

    fun getBlocklistUrl(ctx: Context): String? = prefs(ctx).getString(KEY_BLOCKLIST_URL, null)

    fun setBlockedApps(ctx: Context, packages: Set<String>) {
        prefs(ctx).edit()
            .putString(KEY_BLOCKED_APPS, packages.joinToString("\n"))
            .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
            .apply()
    }

    fun getBlockedApps(ctx: Context): Set<String> {
        val raw = prefs(ctx).getString(KEY_BLOCKED_APPS, "") ?: ""
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun lastFetchAt(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_FETCH, 0L)
}
