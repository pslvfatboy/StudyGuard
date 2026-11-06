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
 *  1. Setting the parent PIN + blocklist URL.
 *  2. Granting Device Admin (uninstall deterrent).
 *  3. Granting the Accessibility Service (this is what actually blocks apps, in real time,
 *     and also guards the uninstall/admin-deactivation screens).
 *  4. Granting "draw over other apps" (helps the PIN screen appear promptly on some OEMs).
 *  5. Starting the background service (blocklist refresh + persistent notification).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var pinInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        pinInput = findViewById(R.id.setupPinInput)
        val urlInput = findViewById<EditText>(R.id.setupUrlInput)
        statusText = findViewById(R.id.statusText)

        Prefs.getBlocklistUrl(this)?.let { urlInput.setText(it) }
        if (Prefs.hasPin(this)) {
            pinInput.hint = "PIN already set — leave blank to keep it"
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val pin = pinInput.text.toString()
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Enter the blocklist URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pin.isNotEmpty()) {
                if (pin.length < 4) {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Prefs.setPin(this, pin)
                pinInput.setText("")
                pinInput.hint = "PIN already set — leave blank to keep it"
            } else if (!Prefs.hasPin(this)) {
                Toast.makeText(this, "Enter a PIN (at least 4 digits)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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

        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            Toast.makeText(
                this,
                "Find StudyGuard in the list and turn it on. If the switch won't turn on, " +
                    "see 'Restricted settings' in the README.",
                Toast.LENGTH_LONG
            ).show()
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
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(
                    this,
                    "Accessibility service isn't on yet — blocking won't work without it.",
                    Toast.LENGTH_LONG
                ).show()
            }
            Executors.newSingleThreadExecutor().execute {
                BlocklistFetcher.refresh(applicationContext)
            }
            val serviceIntent = Intent(this, AppBlockerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "StudyGuard started", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val adminOk = devicePolicyManager.isAdminActive(adminComponent)
        val accessibilityOk = isAccessibilityServiceEnabled()
        val overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        fun mark(ok: Boolean) = if (ok) "✅" else "❌"
        statusText.text = "Device Admin: ${mark(adminOk)}   " +
            "Accessibility: ${mark(accessibilityOk)}   " +
            "Overlay: ${mark(overlayOk)}"
    }

    /** Reads Settings.Secure directly - more reliable than guessing from the Settings UI alone. */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, UninstallGuardAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
