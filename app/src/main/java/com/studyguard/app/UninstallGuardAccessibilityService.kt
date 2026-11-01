package com.studyguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Watches Settings app screens. If the child navigates to:
 *   - "App info" for this app with an Uninstall button, or
 *   - "Device admin apps" screen where StudyGuard's admin rights could be deactivated,
 * this pops PinLockActivity on top. If the PIN isn't entered correctly, PinLockActivity
 * sends the user back to Settings' home (it does not and cannot "silently" let the
 * uninstall through either way - Android still shows its own confirmation dialogs).
 *
 * This is a deterrent, not a hard guarantee: Safe Mode or a factory reset bypasses it,
 * which is a limitation of any non-MDM-enrolled Android app and worth knowing about.
 */
class UninstallGuardAccessibilityService : AccessibilityService() {

    companion object {
        var instance: UninstallGuardAccessibilityService? = null
    }

    private var pinPromptShowing = false

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
        if (event.packageName != "com.android.settings") return

        val className = event.className?.toString() ?: return
        val isRelevantScreen = className.contains("InstalledAppDetails") ||
            className.contains("DeviceAdminSettings") ||
            className.contains("DeviceAdminAdd")

        if (isRelevantScreen && !pinPromptShowing) {
            // Only trigger when our own package is actually on screen somewhere, to avoid
            // interrupting unrelated app-info / device-admin screens for other apps.
            val root = rootInActiveWindow
            val mentionsUs = root?.let { containsOurPackage(it) } ?: true
            if (mentionsUs) {
                pinPromptShowing = true
                val intent = Intent(this, PinLockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(PinLockActivity.EXTRA_REASON, "unlock_settings")
                }
                startActivity(intent)
            }
        }
    }

    private fun containsOurPackage(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
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
