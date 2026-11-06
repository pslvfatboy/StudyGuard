package com.studyguard.app

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Does two jobs, both using the same window-state-changed events:
 *
 * 1. APP BLOCKING (primary mechanism): whenever any app's window comes to the front and
 *    its package is on the blocklist, immediately calls performGlobalAction(GLOBAL_ACTION_HOME).
 *    This is done from the Accessibility Service rather than by calling startActivity() from a
 *    background Service, because Android 10+ blocks background apps from starting activities
 *    (the "background activity start" restriction) - an AccessibilityService's global actions
 *    are exempt from that, which is why this is the standard technique real screen-time /
 *    parental-control apps use, and why the previous UsageStatsManager+startActivity approach
 *    silently failed to block anything.
 *
 * 2. UNINSTALL / ADMIN-DEACTIVATION GUARD: watches Settings screens. If the child opens the
 *    screen that could deactivate StudyGuard's Device Admin rights AND admin rights are
 *    currently active (i.e. this really is a deactivation attempt, not the original grant
 *    during setup - those share the same screen, so we tell them apart via isAdminActive()),
 *    it pops PinLockActivity on top and requires the PIN to continue.
 */
class UninstallGuardAccessibilityService : AccessibilityService() {

    companion object {
        var instance: UninstallGuardAccessibilityService? = null
        private const val SETTINGS_PACKAGE = "com.android.settings"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pinPromptShowing = false
    private var lastBlockedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        if (pkg == SETTINGS_PACKAGE) {
            handleSettingsScreen(event)
        } else {
            handlePossibleBlock(pkg)
        }
    }

    private fun handlePossibleBlock(pkg: String) {
        if (pkg == packageName) {
            // StudyGuard itself came to the front - nothing to block, reset de-dup.
            lastBlockedPackage = null
            return
        }

        val blocked = Prefs.getBlockedApps(applicationContext)
        if (pkg in blocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            if (pkg != lastBlockedPackage) {
                lastBlockedPackage = pkg
                mainHandler.post {
                    Toast.makeText(
                        applicationContext,
                        "StudyGuard: that app is blocked right now — time to study!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            lastBlockedPackage = null
        }
    }

    private fun handleSettingsScreen(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: return
        val isAdminScreen = className.contains("DeviceAdminSettings") ||
            className.contains("DeviceAdminAdd")
        val isAppInfoScreen = className.contains("InstalledAppDetails")

        if (isAdminScreen) {
            // DeviceAdminAdd/DeviceAdminSettings is shown BOTH when first granting admin
            // rights (during normal setup - must NOT be gated) and when removing them
            // (must be gated). Tell them apart: if our admin rights are not yet active,
            // this has to be the initial grant flow, so let it proceed untouched.
            val dpm = getSystemService(DevicePolicyManager::class.java)
            val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
            val alreadyActive = dpm?.isAdminActive(adminComponent) == true
            if (alreadyActive) {
                showPinGate()
            }
            return
        }

        if (isAppInfoScreen && !pinPromptShowing) {
            // Only gate this when it's plausibly our own app's App Info screen. Default to
            // NOT gating when we can't confirm it (root view not ready yet), so we don't
            // interrupt unrelated apps' App Info screens.
            val root = rootInActiveWindow ?: return
            if (containsOurPackage(root)) {
                showPinGate()
            }
        }
    }

    private fun showPinGate() {
        if (pinPromptShowing) return
        pinPromptShowing = true
        val intent = Intent(this, PinLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(PinLockActivity.EXTRA_REASON, "unlock_settings")
        }
        startActivity(intent)
    }

    private fun containsOurPackage(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        if (text.contains("StudyGuard", ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsOurPackage(child)) return true
        }
        return false
    }

    fun clearPrompt() {
        pinPromptShowing = false
    }

    override fun onInterrupt() {}
}
