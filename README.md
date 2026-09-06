# Clarity

Turns a stock iPhone into a locked down, distraction free device using
**supervision**, the same OS role corporate device management uses. Strict
whitelist: only the apps you name exist, there is no browser, and undoing it
is meant to be genuinely hard, not merely inconvenient.

Clarity is three configuration profiles installed over
USB with [iMazing](https://imazing.com/supervision) (Windows or Mac),
enforced by the OS itself.

## What it enforces

* **Whitelist.** Only listed apps are visible or launchable. Safari, App
  Store, Shortcuts and everything unlisted vanish.
* **Web allowlist** (recommended). The OS built in WebKit filter refuses
  every web page on the device, in app views included, except listed login
  domains. Closes the classic in app browsing escape.
* **No off switch on the phone.** The profiles are non removable. Every
  exit wipes the phone.

| Escape route | Closed by |
| --- | --- |
| Remove the profiles in Settings | `PayloadRemovalDisallowed`; only the supervision host can lift them |
| Erase from Settings | `allowEraseContentAndSettings` |
| Pair with another computer | `allowHostPairing` |
| Install anything | store hidden and unlaunchable via the allowlist; `allowAutomaticAppDownloads`, `allowAppClips`, `allowEnterpriseAppTrust` |
| Remove or offload apps | `allowAppRemoval` |
| Sign out of the Apple ID | `allowAccountModification` (keeps Activation Lock armed) |
| Add a VPN | `allowVPNCreation` |
| Install rival profiles on device | `allowUIConfigurationProfileInstallation` |
| Siri and Spotlight web results | `allowAssistant`, `allowSpotlightInternetResults` |
| Clock fiddling | `forceAutomaticDateAndTime` |
| Cheap wipes via restore | `allowCloudBackup` off, see "Getting out" |

## What you need

* A PC with iMazing (paid license) and a USB cable.
* An iPhone you can erase.
* Optional: a filtering DNS resolver (below).

## Setup

1. Copy the templates to `*.local.mobileconfig` (gitignored) and fill in
   your apps (bundle IDs via iMazing or the iTunes search API) and resolver
   ID. iMazing Profile Editor (free) validates the files before install.
2. Factory reset, then supervise with iMazing. In the wizard: UNCHECK
   "Allow pairing without supervising organization", UNCHECK "Disable USB
   restricted mode", CHECK "Allow activation lock while supervised" (or
   Find My can never arm), leave "Save passcode unlock token" off. Skip MDM
   enrollment, none is used.
3. Set up the phone. Coming from Android, run Move to iOS here, WhatsApp
   history transfers only during setup. Sign into the Apple ID, enable Find
   My, set a passcode.
4. Install and sign into every whitelisted app. TAN apps need their
   activation letters now. Enable iCloud Photos if wanted.
5. Install the profiles. DNS and web filter anytime; restrictions strictly
   last, the store dies with it. Then turn on Settings, App Store, App
   Updates once.
6. Export the supervision identity (iMazing Preferences, Supervision) as a
   password protected .p12 to a USB stick kept off any cloud. The identity
   is the key, not the PC; lose both and the phone is frozen forever.

## Operating the phone

* **Add an app:** remove the restrictions profile in iMazing, install and
  sign in, reinstall the profile. Two minutes, requires the identity.
* **Updates: automatic.** With the store hidden by the allowlist and
  `allowAppInstallation` left unset, background auto updates run (verified).
  Manual installs stay impossible.
* **Rule changes:** edit a profile, reinstall over USB, effective
  immediately. DNS rules change in the dashboard, no cable.
* **Pre block media:** in app cache clearing is not enough, Spotify keeps
  showing pre block video until the app is reinstalled with the DNS block
  in place (the add an app round trip above).

## The DNS layer (optional)

One feature rides on it: blocking native video inside whitelisted apps
while audio keeps playing. Postures:

* **None.** Spotify canvas and video podcasts play. Alternative: Spotify's
  own video toggles, and on a family plan the manager can disable video per
  member, unre-enableable by the member.
* **Self hosted.** AdGuard Home, same rules as `||domain^`. No third
  party, fails closed, your server to run.
* **Hosted** (NextDNS, ControlD). Easiest. **Pay**: free tiers stop
  filtering at their quota and fail open. Enable Block Bypass Methods.

Hostname filtering only splits what uses distinct hosts. Spotify does:
video, canvas and even artwork die (the denylist strips covers on purpose,
a text and audio UI) while audio resolves. WhatsApp does not: Status shares
hosts with chat media, so blocking one kills both; mute per contact
instead. The device label in the DoH URL self registers and tags the phone
in the resolver logs. Verify from any PC: query the DoH endpoint, blocked
hosts answer 0.0.0.0. Spotify hosts per
[Tech Lockdown's writeup](https://www.techlockdown.com/articles/block-images-videos-spotify).

Exact per flow filtering without any server exists only as code:
`NEFilterDataProvider`, paid developer account, Mac at build time.

## Getting out

| Wipe path | Gated by |
| --- | --- |
| Security Lockout erase (repeated wrong passcode) | the Apple ID password |
| Recovery/DFU restore | a computer and a cable; Activation Lock after |
| iCloud remote erase | the Apple ID password |

All paths erase the phone; iCloud Backup is disabled so that stays
expensive (delete `allowCloudBackup` from your copy for a softer wall).
Recovery mode cannot wipe phone only, it needs a computer.

Survives a wipe: iCloud Photos, contacts, calendars, WhatsApp up to its
last chat backup, synced clouds. Dies: Signal history,
every login, all local app data (keep KeePassium and Obsidian vaults
synced), the eSIM, and every TAN binding, meaning days of letters before
mobile banking returns.

## Honest limits

* DNS filtering is hostname level; a hardcoded resolver IP dodges it. Only
  the video in allowed apps case depends on it.
* Web views render pages unless the web filter profile is installed.
  Captive portals need their domains added, and remote images in HTML mail
  render as placeholders (they load from unlisted hosts).
* Notification shade, Control Center and app switcher stay; nothing
  dangerous is reachable in them.
* Settings cannot be hidden, only defanged.
* Wifi cannot be forced off; the DNS pin follows every network anyway.
* A paired Apple Watch may not enforce the allowlist (forum reports).

## Files

* `clarity-restrictions.mobileconfig` whitelist plus hardening (template).
* `clarity-webfilter.mobileconfig` allowlist only web filter (template).
* `clarity-dns.mobileconfig` DNS pin to the resolver (template, optional).
* `nextdns-denylist.txt` video, artwork and DoH host list.
* `*.local.mobileconfig` your filled in copies, gitignored.
