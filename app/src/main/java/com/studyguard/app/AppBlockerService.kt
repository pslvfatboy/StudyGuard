package com.studyguard.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors

/**
 * Foreground service that:
 *  1. Every ~60s, re-downloads the blocklist from the configured URL.
 *  2. Every ~2s, checks which app is currently in the foreground via UsageStatsManager,
 *     and if it's on the blocklist, sends the device back to the home screen and shows
 *     a visible toast so the child knows why (this is intentionally NOT hidden).
 *
 * Requires the user to have granted "Usage Access" (Settings > Apps > Special access >
 * Usage access) for foreground-app detection to work.
 */
class AppBlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private var lastBlocklistFetch = 0L
    private val blocklistIntervalMs = 60_000L
    private val pollIntervalMs = 2_000L

    private var lastBlockedPackage: String? = null

    private val loop = object : Runnable {
        override fun run() {
            checkForegroundApp()
            maybeRefreshBlocklist()
            handler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        handler.post(loop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        super.onDestroy()
    }

    private fun maybeRefreshBlocklist() {
        val now = System.currentTimeMillis()
        if (now - lastBlocklistFetch >= blocklistIntervalMs) {
            lastBlocklistFetch = now
            bgExecutor.execute { BlocklistFetcher.refresh(applicationContext) }
        }
    }

    private fun checkForegroundApp() {
        val foregroundPkg = getForegroundPackage() ?: return
        if (foregroundPkg == packageName) return // never block ourselves

        val blocked = Prefs.getBlockedApps(applicationContext)
        if (foregroundPkg in blocked) {
            if (foregroundPkg != lastBlockedPackage) {
                lastBlockedPackage = foregroundPkg
                showBlockedToast(foregroundPkg)
            }
            goHome()
        } else {
            lastBlockedPackage = null
        }
    }

    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 10_000 // look at the last 10 seconds of events
        val events = usm.queryEvents(begin, end)
        var lastPkg: String? = null
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun showBlockedToast(pkg: String) {
        handler.post {
            Toast.makeText(
                applicationContext,
                "StudyGuard: that app is blocked right now — time to study! ($pkg)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startAsForeground() {
        val channelId = "studyguard_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "StudyGuard active", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("StudyGuard is active")
            .setContentText("Blocked apps will be closed automatically.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }
}
