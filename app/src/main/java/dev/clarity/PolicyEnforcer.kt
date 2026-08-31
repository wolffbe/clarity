package dev.clarity

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings

data class EnforceResult(
    val hidden: List<String>,
    val kept: List<String>,
)

object PolicyEnforcer {

    private const val TAG = "ClarityPolicy"

    fun admin(context: Context): ComponentName =
        ComponentName(context, ClarityAdminReceiver::class.java)

    fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean =
        dpm(context).isDeviceOwnerApp(context.packageName)

    /**
     * Full lockdown pass. Idempotent, safe to call repeatedly.
     *  - Hides every launchable / user installed app not on the whitelist.
     *  - Applies the hardening restriction set (adb, safe boot, factory reset,
     *    app control, uninstall, install sources...).
     *  - Blocks its own uninstall.
     *  - Attempts to disable Private DNS and pins the always on filtering VPN.
     *  - Routes stray http/https link taps to a dead end.
     */
    fun enforce(context: Context): EnforceResult {
        if (!isDeviceOwner(context)) return EnforceResult(emptyList(), emptyList())

        val dpm = dpm(context)
        val admin = admin(context)

        applyHardening(context)
        blockOwnUninstall(context)
        forcePrivateDnsOff(context)
        pinAlwaysOnVpn(context)
        keepFactoryResetProtected(context)
        routeLinksToDeadEnd(context)
        applyKiosk(context)
        ensureMobileData(context)
        grantSelfBluetooth(context)

        val protectedSet = Whitelist.protectedSet(context.packageName)
        val candidates = hideCandidates(context)

        val hidden = mutableListOf<String>()
        val kept = mutableListOf<String>()

        // Make sure everything on the whitelist is visible.
        protectedSet.forEach { pkg ->
            try {
                if (isInstalled(context, pkg)) {
                    dpm.setApplicationHidden(admin, pkg, false)
                    kept.add(pkg)
                }
            } catch (_: Exception) { /* not present */ }
        }

        // Hide everything else.
        candidates.forEach { pkg ->
            if (pkg in protectedSet) return@forEach
            try {
                dpm.setApplicationHidden(admin, pkg, true)
                if (dpm.isApplicationHidden(admin, pkg)) hidden.add(pkg)
            } catch (_: Exception) { /* system critical, cannot hide */ }
        }

        return EnforceResult(hidden.sorted(), kept.sorted())
    }

    /**
     * Full permanent stand down. Only wired to the debug build's lift button;
     * a release build has no on-phone way to call this.
     */
    fun liftAll(context: Context) {
        if (!isDeviceOwner(context)) return
        standDown(context)
        EnforcementJob.cancel(context)
    }

    /** Relaxes every policy. Used only by the debug lift. */
    private fun standDown(context: Context) {
        val dpm = dpm(context)
        val admin = admin(context)

        hideCandidates(context).forEach { pkg ->
            try { dpm.setApplicationHidden(admin, pkg, false) } catch (_: Exception) {}
        }
        HARDENING.forEach { r ->
            try { dpm.clearUserRestriction(admin, r) } catch (_: Exception) {}
        }
        try { dpm.setUninstallBlocked(admin, context.packageName, false) } catch (_: Exception) {}
        try { dpm.clearPackagePersistentPreferredActivities(admin, context.packageName) } catch (_: Exception) {}
        try { dpm.setLockTaskPackages(admin, emptyArray()) } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
        } catch (_: Exception) {}
        try { dpm.setStatusBarDisabled(admin, false) } catch (_: Exception) {}
        try {
            dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE)
        } catch (_: Exception) {}
        try { dpm.setPermittedAccessibilityServices(admin, null) } catch (_: Exception) {}
        try { dpm.setPermittedInputMethods(admin, null) } catch (_: Exception) {}
        try { dpm.setAlwaysOnVpnPackage(admin, null, false) } catch (_: Exception) {}
        FilterVpnService.stop(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dpm.setGlobalPrivateDnsModeOpportunistic(admin)
            }
        } catch (_: Exception) {}
    }

    // ---- hardening ---------------------------------------------------------

    /**
     * The restrictions that make this airtight rather than a suggestion.
     * DISALLOW_DEBUGGING_FEATURES is the important one: it kills adb, so the
     * "adb remove-active-admin" escape used in testing is gone on a real
     * device.
     */
    private val HARDENING = buildList {
        add(UserManager.DISALLOW_DEBUGGING_FEATURES)      // no adb / USB debugging
        add(UserManager.DISALLOW_SAFE_BOOT)               // no safe mode
        add(UserManager.DISALLOW_INSTALL_APPS)            // no new installs at all
        add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES) // no sideloading
        add(UserManager.DISALLOW_UNINSTALL_APPS)          // cannot remove whitelisted set
        add(UserManager.DISALLOW_APPS_CONTROL)            // no force stop / clear data / disable
        add(UserManager.DISALLOW_FACTORY_RESET)           // no reset from Settings
        add(UserManager.DISALLOW_ADD_USER)                // no second user to escape into
        add(UserManager.DISALLOW_CONFIG_VPN)              // cannot disable our VPN
        add(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)    // no loading apks off SD
        add(UserManager.DISALLOW_USB_FILE_TRANSFER)       // no loading media over USB
        add(UserManager.DISALLOW_CONFIG_WIFI)             // wifi blocked: no config
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(UserManager.DISALLOW_CONFIG_DATE_TIME)    // no clock fiddling
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(UserManager.DISALLOW_CHANGE_WIFI_STATE)   // wifi stays off
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(UserManager.DISALLOW_ADD_WIFI_CONFIG)     // no adding wifi networks
        }
    }

    private fun applyHardening(context: Context) {
        val dpm = dpm(context)
        val admin = admin(context)
        HARDENING.forEach { r ->
            try { dpm.addUserRestriction(admin, r) } catch (e: Exception) {
                android.util.Log.w(TAG, "restriction failed: $r", e)
            }
        }
    }

    /**
     * Keep the phone online over cellular by default.
     *
     * Honest note: there is no guaranteed device owner API to flip the master
     * "mobile data" switch (that needs MODIFY_PHONE_STATE, a system permission
     * we cannot hold), so the setGlobalSetting call below is best effort and is
     * a no-op on many devices. The real guarantee is structural: a freshly
     * reset phone with a data SIM has mobile data ON by default, and the
     * lockdown removes every surface that could turn it OFF (no Settings, no
     * quick settings), so once on it stays on across reboots.
     *
     * Data roaming is left to the device default (usually off) to avoid
     * surprise charges. Flip FORCE_DATA_ROAMING if you want it always on.
     */
    private const val FORCE_DATA_ROAMING = false

    private fun ensureMobileData(context: Context) {
        val dpm = dpm(context)
        val admin = admin(context)
        // Best effort master toggle. Harmless where the platform rejects it.
        try { dpm.setGlobalSetting(admin, "mobile_data", "1") } catch (_: Exception) {}
        try { dpm.setGlobalSetting(admin, "mobile_data1", "1") } catch (_: Exception) {} // 2nd SIM slot
        if (FORCE_DATA_ROAMING) {
            try { dpm.setGlobalSetting(admin, Settings.Global.DATA_ROAMING, "1") } catch (_: Exception) {}
        }
    }

    /** Grant ourselves the Bluetooth runtime permission so the in app toggle works. */
    private fun grantSelfBluetooth(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            dpm(context).setPermissionGrantState(
                admin(context), context.packageName,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        } catch (_: Exception) {}
    }

    private fun blockOwnUninstall(context: Context) {
        try {
            dpm(context).setUninstallBlocked(admin(context), context.packageName, true)
        } catch (_: Exception) {}
    }

    private fun forcePrivateDnsOff(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        // Best effort: device owner has no guaranteed "disable DoT" setter, and
        // PRIVATE_DNS_MODE is not on the setGlobalSetting allow list on every
        // device, so this may be rejected. When it is, DoH host blocking in the
        // VPN is the remaining defense (see DomainRules.BLOCKED_DOH). We do NOT
        // set "specified" mode, which would point DoT at a bogus host and break
        // resolution.
        try {
            dpm(context).setGlobalSetting(admin(context), Settings.Global.PRIVATE_DNS_MODE, "off")
        } catch (e: Exception) {
            android.util.Log.i(TAG, "could not force Private DNS off (expected on some devices)")
        }
    }

    /**
     * The "high economic effort to reset" wall.
     *
     * DISALLOW_FACTORY_RESET (in HARDENING) removes the reset option from
     * Settings. A hardware/recovery reset is still physically possible, but
     * Android Factory Reset Protection then demands the Google account that was
     * synced on the device before it will finish setup. Without those
     * credentials the phone is a paperweight, which is exactly the deterrent
     * asked for: no cheap reset path.
     *
     * By default we rely on the stock FRP that a signed in Google account
     * already provides. If you want to pin specific unlock accounts explicitly,
     * fill FRP_ACCOUNTS with Google account IDs and this enables the policy.
     * Leaving it empty avoids any risk of locking the device to nobody.
     */
    private val FRP_ACCOUNTS = emptyList<String>()

    private fun keepFactoryResetProtected(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (FRP_ACCOUNTS.isEmpty()) return
        try {
            val policy = android.app.admin.FactoryResetProtectionPolicy.Builder()
                .setFactoryResetProtectionAccounts(FRP_ACCOUNTS)
                .setFactoryResetProtectionEnabled(true)
                .build()
            dpm(context).setFactoryResetProtectionPolicy(admin(context), policy)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "FRP policy not applied", e)
        }
    }

    private fun pinAlwaysOnVpn(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            // lockdown = false: DNS only VPN, so non DNS traffic must still flow.
            dpm(context).setAlwaysOnVpnPackage(admin(context), context.packageName, false)
        } catch (_: Exception) {}
        FilterVpnService.start(context)
    }

    // ---- app hiding --------------------------------------------------------

    /**
     * Everything we are willing to hide: all launchable apps plus all non
     * system (user installed) apps. System packages without a launcher icon
     * are left alone so the OS keeps working.
     */
    private fun hideCandidates(context: Context): Set<String> {
        val pm = context.packageManager
        val out = mutableSetOf<String>()

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL).forEach {
            out.add(it.activityInfo.packageName)
        }

        pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES).forEach { app ->
            val isSystem = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (!isSystem) out.add(app.packageName)
        }

        out.remove(context.packageName)
        return out
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        true
    } catch (_: Exception) { false }

    /**
     * Hard kiosk layer. Our LauncherActivity is the enforced HOME; only the
     * curated kiosk set may run in lock task (Settings, Play Store, installer
     * and file picker are intentionally excluded so nothing can deep link into
     * them while locked); recents is removed; the status bar, notification
     * shade and quick settings are disabled; the lock screen camera and widgets
     * are off; and third-party accessibility services and keyboards are barred,
     * which shuts the Voice Access and swapped-keyboard escape routes.
     */
    private fun applyKiosk(context: Context) {
        val dpm = dpm(context)
        val admin = admin(context)

        // Settings is allowed to RUN in lock task (so the mobile data / SIM
        // panel can open from the Clarity app) but it is not on the launcher
        // grid, so there is no Settings icon to browse from, only the targeted
        // mobile control. Dangerous Settings actions stay blocked by the
        // hardening restrictions, and wifi is neutered.
        val allowed = (Whitelist.kioskSet(context.packageName) + "com.android.settings").toTypedArray()
        try { dpm.setLockTaskPackages(admin, allowed) } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // HOME + power menu + keyguard only. No OVERVIEW (recents), and no
            // NOTIFICATIONS / SYSTEM_INFO since the status bar is disabled below.
            val features = DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
            try { dpm.setLockTaskFeatures(admin, features) } catch (_: Exception) {}
        }

        // Kill the notification shade / quick settings / status bar overlay,
        // the main path an app deep link or tile long press used to reach.
        try { dpm.setStatusBarDisabled(admin, true) } catch (_: Exception) {}

        // Lock screen: no secure camera shortcut, no widgets.
        try {
            dpm.setKeyguardDisabledFeatures(
                admin,
                DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA or
                    DevicePolicyManager.KEYGUARD_DISABLE_WIDGETS_ALL
            )
        } catch (_: Exception) {}

        // Bar third-party accessibility services (blocks Voice Access etc.) and
        // third-party input methods (system keyboards still work). Empty list =
        // none permitted beyond system.
        try { dpm.setPermittedAccessibilityServices(admin, emptyList<String>()) } catch (_: Exception) {}
        try { dpm.setPermittedInputMethods(admin, emptyList<String>()) } catch (_: Exception) {}

        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val launcher = ComponentName(context, LauncherActivity::class.java)
        try { dpm.addPersistentPreferredActivity(admin, homeFilter, launcher) } catch (_: Exception) {}
    }

    private fun routeLinksToDeadEnd(context: Context) {
        val dpm = dpm(context)
        val admin = admin(context)
        val deadEnd = ComponentName(context, DeadEndActivity::class.java)
        listOf("http", "https").forEach { scheme ->
            val filter = IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme(scheme)
            }
            try { dpm.addPersistentPreferredActivity(admin, filter, deadEnd) } catch (_: Exception) {}
        }
    }
}
