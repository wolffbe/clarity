package dev.clarity

import android.content.Context

/**
 * Whitelist model. Everything NOT allowed gets hidden.
 *
 * Your personal app list (APPS) is deliberately NOT in this file and NOT in the
 * repo. It is loaded at runtime from assets/whitelist.txt, which is gitignored,
 * so your actual apps (banking, health, finance) never get published. See
 * whitelist.example.txt for the format.
 *
 * The reliable way to fill it is from your own device, which lists exactly the
 * apps you installed:
 *
 *   adb shell pm list packages -3 | sed "s/package://" > app/src/main/assets/whitelist.txt
 *
 * SYSTEM_ESSENTIALS and KIOSK_ESSENTIALS are generic OS packages, not personal,
 * so they stay here in the committed code.
 */
object Whitelist {

    private const val ASSET = "whitelist.txt"

    @Volatile
    private var cached: Set<String>? = null

    /** Allowed apps, loaded from the gitignored asset. Empty set if absent. */
    fun apps(context: Context): Set<String> {
        cached?.let { return it }
        val set = try {
            context.assets.open(ASSET).bufferedReader().useLines { lines ->
                lines.map { it.substringBefore('#').trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
        cached = set
        return set
    }

    /**
     * System packages that must stay to keep the device functional.
     * Broad on purpose: covers AOSP, Pixel, Samsung, Xiaomi, OnePlus names.
     */
    val SYSTEM_ESSENTIALS = setOf(
        // phone / dialer
        "com.android.dialer", "com.google.android.dialer",
        "com.samsung.android.dialer", "com.android.phone",
        // messages (SMS, not social)
        "com.android.messaging", "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        // contacts
        "com.android.contacts", "com.google.android.contacts",
        "com.samsung.android.app.contacts",
        // settings
        "com.android.settings",
        // clock / alarm
        "com.android.deskclock", "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        // camera
        "com.android.camera2", "com.google.android.GoogleCamera",
        "com.sec.android.app.camera",
        // system UI, installer, permission controller
        "com.android.systemui", "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        // Play services + store are needed for most app auth to work
        "com.google.android.gms", "com.google.android.gsf",
        "com.android.vending",
        // input methods
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        // phone/account setup
        "com.google.android.setupwizard", "com.android.settings.intelligence",
    )

    /**
     * User facing utility system apps allowed to run in the kiosk and show on
     * the home grid. Narrower than SYSTEM_ESSENTIALS: no Settings, Play Store,
     * installer, or file picker.
     */
    val KIOSK_ESSENTIALS = setOf(
        // phone / dialer
        "com.android.dialer", "com.google.android.dialer",
        "com.samsung.android.dialer", "com.android.phone",
        // messages (SMS)
        "com.android.messaging", "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        // contacts
        "com.android.contacts", "com.google.android.contacts",
        "com.samsung.android.app.contacts",
        // clock / alarm
        "com.android.deskclock", "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        // camera
        "com.android.camera2", "com.google.android.GoogleCamera",
        "com.sec.android.app.camera",
    )

    /** Packages that must never be hidden (kept installed and functional). */
    fun protectedSet(context: Context): Set<String> =
        apps(context) + SYSTEM_ESSENTIALS + context.packageName

    /** Packages allowed to run in the kiosk and shown on the launcher grid. */
    fun kioskSet(context: Context): Set<String> =
        apps(context) + KIOSK_ESSENTIALS + context.packageName
}
