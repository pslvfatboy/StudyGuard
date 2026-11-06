package com.studyguard.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors

/**
 * Foreground service whose only job now is to keep re-downloading the blocklist from the
 * configured URL every ~60s, and to show the persistent "StudyGuard is active" notification
 * (required for any foreground service, and intentionally visible since this app is disclosed
 * to the child).
 *
 * The actual blocking happens in UninstallGuardAccessibilityService, in real time, as soon as
 * a blocked app's window appears - not here. An earlier version of this service polled the
 * foreground app itself and tried to send the device home via startActivity(), but Android's
 * background-activity-start restrictions silently blocked that; the Accessibility Service's
 * performGlobalAction() is exempt from that restriction, which is why blocking moved there.
 */
class AppBlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val blocklistIntervalMs = 60_000L

    private val refreshLoop = object : Runnable {
        override fun run() {
            bgExecutor.execute { BlocklistFetcher.refresh(applicationContext) }
            handler.postDelayed(this, blocklistIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        handler.post(refreshLoop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(refreshLoop)
        super.onDestroy()
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
