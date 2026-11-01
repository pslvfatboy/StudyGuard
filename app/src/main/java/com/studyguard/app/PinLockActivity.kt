package com.studyguard.app

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * Full-screen PIN prompt. Shown either from MainActivity (to reveal settings) or from
 * UninstallGuardAccessibilityService (when the child opens the uninstall / device-admin
 * deactivation screen in Settings).
 */
class PinLockActivity : Activity() {

    companion object {
        const val EXTRA_REASON = "reason"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        val pinInput = findViewById<EditText>(R.id.pinInput)
        val submitButton = findViewById<Button>(R.id.pinSubmit)
        val label = findViewById<TextView>(R.id.pinLabel)

        val reason = intent.getStringExtra(EXTRA_REASON)
        if (reason == "unlock_settings") {
            label.text = "Enter the parent PIN to continue to this settings screen"
        }

        submitButton.setOnClickListener {
            val entered = pinInput.text.toString()
            if (Prefs.checkPin(this, entered)) {
                Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show()
                UninstallGuardAccessibilityService.instance?.clearPrompt()
                finish()
            } else {
                Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                pinInput.text.clear()
            }
        }
    }

    override fun onBackPressed() {
        // Block attempts to dismiss this screen with the back button: send them back to
        // the Settings home instead, so the uninstall/admin-deactivation flow is abandoned.
        UninstallGuardAccessibilityService.instance?.let {
            it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            it.clearPrompt()
        }
        finish()
    }
}
