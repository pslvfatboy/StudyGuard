package com.studyguard.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Being an active Device Administrator means Android requires this app's admin rights
 * to be deactivated (Settings > Security > Device admin apps) before it can be uninstalled.
 * The UninstallGuardAccessibilityService watches for that deactivation screen and gates it
 * behind the parent PIN.
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "StudyGuard: admin protection enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling StudyGuard admin protection removes app blocking. Ask your parent for the PIN."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "StudyGuard: admin protection disabled", Toast.LENGTH_SHORT).show()
    }
}
