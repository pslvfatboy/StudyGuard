package com.studyguard.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * One-time setup screen. Walks through:
 *  1. Setting the parent PIN.
 *  2. Entering the GitHub raw URL for the blocklist.
 *  3. Granting Device Admin (uninstall deterrent).
 *  4. Granting Usage Access (needed to detect the foreground app).
 *  5. Granting the Accessibility Service (uninstall PIN guard).
 *  6. Granting "draw over other apps" (needed for some OEMs to show the PIN screen promptly).
 *  7. Starting the background service.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        val pinInput = findViewById<EditText>(R.id.setupPinInput)
        val urlInput = findViewById<EditText>(R.id.setupUrlInput)
        val statusText = findViewById<TextView>(R.id.statusText)

        Prefs.getBlocklistUrl(this)?.let { urlInput.setText(it) }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val pin = pinInput.text.toString()
            val url = urlInput.text.toString().trim()
            if (pin.length < 4) {
                Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (url.isEmpty()) {
                Toast.makeText(this, "Enter the blocklist URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.setPin(this, pin)
            Prefs.setBlocklistUrl(this, url)
            Toast.makeText(this, "Saved. Now grant each permission below.", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.deviceAdminButton).setOnClickListener {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "StudyGuard needs this so it can't be uninstalled without the parent PIN."
                    )
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Device admin already active", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.usageAccessButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.overlayButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.startServiceButton).setOnClickListener {
            if (Prefs.getBlocklistUrl(this) == null || !Prefs.hasPin(this)) {
                Toast.makeText(this, "Save your PIN and blocklist URL first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Executors.newSingleThreadExecutor().execute {
                BlocklistFetcher.refresh(applicationContext)
            }
            val serviceIntent = Intent(this, AppBlockerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            statusText.text = "StudyGuard is running."
            Toast.makeText(this, "StudyGuard started", Toast.LENGTH_SHORT).show()
        }
    }
}
