package dev.clarity

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class ClarityAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        PolicyEnforcer.enforce(context)
        EnforcementJob.schedule(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        PolicyEnforcer.enforce(context)
        EnforcementJob.schedule(context)
    }
}
