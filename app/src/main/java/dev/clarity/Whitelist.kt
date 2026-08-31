package dev.clarity

/**
 * Whitelist model. Everything NOT named here gets hidden.
 *
 * APPS is your allow list: the only non system apps that stay visible and
 * usable. They keep full internet access, so in app auth and OAuth web views
 * work normally. Edit this to match the phone.
 *
 * SYSTEM_ESSENTIALS keeps the phone a phone: dialer, settings, clock, the
 * system launcher fallback, etc. These are matched across common OEM package
 * names; harmless entries for a brand you do not have are simply ignored.
 * Trim this if you want an even tighter device.
 */
object Whitelist {

    /** The apps the user is allowed to keep. Customize freely. */
    val APPS = setOf(
        "com.spotify.music",              // music (video blocked at the network layer)
        "com.google.android.apps.maps",   // maps
        "com.whatsapp",                   // messaging
        "com.google.android.gm",          // mail (auth via in app web view)
        "com.azure.authenticator",        // 2FA
        "com.google.android.apps.authenticator2",
        "com.microsoft.office.outlook",
    )

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
        // dialer/emergency, system UI, installer, permission controller
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
     * The ONLY packages allowed to run in the kiosk (lock task) and to appear
     * on the home grid. Deliberately narrower than SYSTEM_ESSENTIALS: it leaves
     * out Settings, the Play Store, the package installer, and the file picker,
     * so those stay installed and working in the background but cannot be
     * launched or deep linked into while the phone is locked. That closes the
     * "app links me into Settings" and "Play Store is a browser" escapes.
     *
     * Only genuinely user facing utilities live here.
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
    fun protectedSet(ownPackage: String): Set<String> =
        APPS + SYSTEM_ESSENTIALS + ownPackage

    /** Packages allowed to run in the kiosk and shown on the launcher grid. */
    fun kioskSet(ownPackage: String): Set<String> =
        APPS + KIOSK_ESSENTIALS + ownPackage
}
