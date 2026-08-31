# Clarity

Turns a stock Android phone into a locked down, distraction free device using
the OS level **device owner** role, the same mechanism corporate device
management uses. It runs a strict **whitelist**: only the apps you name stay
usable, everything else disappears, there is no browser, and it is built to be
genuinely hard to undo, not merely inconvenient.

## What it enforces

**Whitelist, not blocklist.** Only apps listed in
`app/src/main/java/dev/clarity/Whitelist.kt` stay visible. Every other
launchable or user installed app is hidden. Whitelisted apps keep full internet
access, so their in app login and OAuth web views work normally. There is no
standalone browser; stray `http`/`https` link taps hit a dead end screen.

**Custom launcher + hard kiosk.** Clarity replaces the home screen with its
own launcher that shows only the allowed apps, pinned as the enforced default
HOME so the stock launcher (also hidden) cannot take back over. Allowed apps run
in Android lock task mode, and the kiosk is hardened to close the usual pry
points:

* Recents / multitasking is removed (no OVERVIEW lock task feature).
* The status bar, notification shade, and quick settings are disabled
  (`setStatusBarDisabled`), so there is no shade to pull down and no tile long
  press into Settings.
* Only a curated kiosk set may run while locked. Settings, the Play Store, the
  package installer, and the file picker are excluded, so an app cannot deep
  link you into them. They stay installed and working in the background.
* The lock screen camera shortcut and widgets are disabled.
* Third-party accessibility services (which is how Voice Access would let you
  talk your way around the UI) and third-party keyboards are barred; system
  keyboards still work.

The power menu and lockscreen stay active so you can still power off and unlock.

**No on-phone off switch.** A release build has no button, menu, or setting on
the device that relaxes any of this. The only way back is a full wipe done from
a computer, which erases everything. See "Getting out" below.

**Airtight hardening** (all applied automatically as device owner):

| Escape route | Closed by |
| --- | --- |
| adb / USB debugging | `DISALLOW_DEBUGGING_FEATURES` (this removes the adb path used to undo device owner) |
| Safe mode | `DISALLOW_SAFE_BOOT` |
| Installing anything | `DISALLOW_INSTALL_APPS` + unknown sources + no physical media |
| Uninstalling / force stop / clear data | `DISALLOW_UNINSTALL_APPS`, `DISALLOW_APPS_CONTROL`, self uninstall blocked |
| Factory reset from Settings | `DISALLOW_FACTORY_RESET` |
| Second user / work profile escape | `DISALLOW_ADD_USER` |
| Disabling the filter VPN | `DISALLOW_CONFIG_VPN` + always on VPN |
| DoH DNS bypass | Known DoH resolver hosts blocked in the VPN; Private DNS (DoT) set off where the device allows it |

**Video blocked, music kept.** A built in filtering VPN (always on, pinned by
device owner) inspects DNS and refuses to resolve video and streaming hosts,
while leaving audio hosts alone. Spotify music plays; Spotify canvas/video
clips, YouTube playback, and other video CDNs do not load. DoH resolvers are
also blocked so apps cannot route around the filter. Rules live in
`DomainRules.kt`.

## Getting out: wipe from a computer, lose everything

There is no delay, no passcode, and no on-phone off switch. The design bet is
blunt: the only way out is to erase the whole phone, and that is painful enough
to stop an impulse. Losing every photo, message, and app login is the
deterrent.

**The phone's own reset paths are closed:**

* In-settings factory reset is blocked (`DISALLOW_FACTORY_RESET`).
* Developer options, adb, and the OEM unlock toggle are disabled
  (`DISALLOW_DEBUGGING_FEATURES`), so the bootloader stays locked and cannot be
  unlocked from the device.
* Safe boot is blocked, uninstall and force stop are blocked, and there is no
  lift button in a release build.

**So a reset requires a computer.** You put the phone into its manufacturer's
flash/download mode (for example Odin on Samsung, EDL/Mi Flash on Xiaomi,
fastboot where the bootloader is unlockable) and reflash or wipe from the
laptop. That erases everything and is deliberate, cabled, and slow, exactly the
friction you want. This is the sanctioned way to reconfigure or retire the
device.

**The one hole, stated honestly.** No app can disable stock recovery's
hardware-key "wipe data/factory reset". A determined person who knows the key
combo can still trigger it from the phone. Two things blunt it: it destroys all
data just like the laptop path, and Android Factory Reset Protection then
demands the Google account that was signed in, so it is not a clean or casual
escape. If you ever want even that closed to *you*, the only way is to not hold
the credential yourself: have someone else run setup with their Google account
and put it in `FRP_ACCOUNTS` in `PolicyEnforcer.kt`. With your own account, FRP
is standard anti theft, not a wall against the owner.

## Setup

Need: a computer with adb, and a phone you can factory reset.

1. **Factory reset the phone and skip account sign in** during setup. Device
   owner can only be set when no accounts exist yet.
2. Build and install (Android Studio, or `gradlew assembleDebug`):

   ```
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. Activate device owner:

   ```
   adb shell dpm set-device-owner dev.clarity/.ClarityAdminReceiver
   ```

4. Open Clarity. It enforces immediately and starts the filtering VPN.
5. Now sign into the Google account you want to use (and want FRP bound to),
   then install/allow only the apps you whitelisted. Reopen Clarity and tap
   **Enforce now** so the new apps get sorted.
6. This is now a commitment build: the manifest has no `android:testOnly`, so
   device owner cannot be removed over adb. Build it in **release** mode as well
   (so `BuildConfig.DEBUG` is false and no lift button is compiled in), install
   that, and set device owner on a fresh reset. From then on adb is off and
   there is no software off switch; the only way out is a wipe from a computer.

   Because there is no `testOnly` escape, do your testing with a debug build
   first and confirm the whitelist and behavior before committing a phone to the
   release build.

## Tuning

* **Allowed apps:** edit `Whitelist.APPS` (what you allow) and
  `Whitelist.KIOSK_ESSENTIALS` (the utility apps that may run and show on the
  grid while locked). `SYSTEM_ESSENTIALS` is the broader "keep installed but not
  necessarily reachable" set.
* **Settings while locked:** Settings is intentionally NOT in the kiosk set, so
  it cannot be opened while the phone is locked. Set up wifi and anything else
  you need before you lock down. If you must reach a Settings screen on the
  device, add `com.android.settings` to `KIOSK_ESSENTIALS`, at the cost of
  reopening the "deep link into Settings" surface.
* **Connectivity (mobile data):** the phone is meant to run on a SIM, not wifi.
  Clarity keeps mobile data on by default. There is no reliable device owner API
  to flip the master data switch (it needs a system permission), so the code
  makes a best effort attempt, but the real guarantee is structural: a freshly
  reset phone with a data SIM starts with data on, and the lockdown removes every
  way to turn it off (no Settings, no quick settings), so it stays on across
  reboots. Insert an activated SIM and confirm data works before you lock down.
  Data roaming stays at the device default; set `FORCE_DATA_ROAMING` in
  `PolicyEnforcer.kt` to force it on if you travel.
* **Video hosts:** enable logcat and watch tag `ClarityDNS` to see exactly what
  Spotify (or anything) resolves on your device, then move video hosts into
  `DomainRules.BLOCKED_SUFFIXES`. Audio hosts left out of that set keep working.

## Honest limits (what "airtight" cannot mean on an unrooted phone)

* **Content level video/mp4 blocking is impossible without root.** Spotify and
  others use pinned TLS, so their traffic cannot be decrypted to strip video
  while keeping audio. We block by **hostname** via DNS, which works because
  video and audio use different hosts, but if a service ever serves both from
  one pinned host, domain filtering cannot separate them. The `ClarityDNS` log is
  there so you can adapt when hosts change.
* **DNS filtering can be dodged by hardcoded resolver IPs or DoH.** We mitigate
  by forcing Private DNS off and blocking known DoH endpoints, not by claiming
  it is unbeatable.
* **Whitelisted apps can still show web pages in their own WebViews** (needed
  for auth). This is now the single biggest remaining kiosk escape: a link
  tapped inside an allowed app can render web content in that app. It has no
  address bar and no general browsing UI, and the kiosk blocks jumping to any
  other app, but the page still loads. The only real defense is keeping the
  whitelist tight and avoiding link heavy or web heavy apps. Everything else
  (recents, the shade, Settings deep links, Voice Access, swapped keyboards,
  the lock screen camera) is now closed.
* **Emergency dialing cannot be removed**, for legal and safety reasons, so the
  emergency dialer remains reachable from the lock screen. It cannot launch your
  apps, but it is a surface that exists on every phone.
* **Recovery/bootloader level attacks** (custom firmware, JTAG) are outside any
  app's control; FRP is the deterrent, not a guarantee against a well funded
  attacker.

## Project layout

* `PolicyEnforcer.kt` core: whitelist hiding, hardening restrictions, uninstall
  block, always on VPN, FRP, link routing, kiosk / lock task.
* `LauncherActivity.kt` the home screen: grid of allowed apps, enters lock task.
* `Whitelist.kt` the apps you allow, plus system essentials.
* `DomainRules.kt` blocked video and DoH host suffixes.
* `FilterVpnService.kt` the DNS filtering VPN.
* `ClarityAdminReceiver.kt` / `BootReceiver.kt` / `EnforcementJob.kt` keep policy
  applied at activation, after reboot, and every 15 minutes.
* `DeadEndActivity.kt` where link taps land. `MainActivity.kt` status panel.
