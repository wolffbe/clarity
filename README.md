# Clarity

Turns a stock iPhone into a locked down, distraction free device using
**supervision**, the OS level management role that corporate device management
uses. It runs a strict **whitelist**: only the apps you name stay visible,
everything else disappears, there is no browser, and it is built to be
genuinely hard to undo, not merely inconvenient.

There is no code and no server. Clarity is three configuration profiles
installed over USB from a PC running [iMazing](https://imazing.com/supervision)
(Windows or Mac), enforced on the phone by the OS itself. Everything works in
airplane mode; nothing phones home.

## What it enforces

**Whitelist, not blocklist.** Only apps listed in the restrictions profile are
shown or launchable. Safari, the App Store, Shortcuts, and every unlisted app
disappear. Whitelisted apps keep full internet access, so their in app login
and OAuth web views work normally. Tapped `http`/`https` links have nowhere to
go and die quietly.

**Web content allowlist (optional but recommended).** The web filter profile
uses the OS built in WebKit filter in allowlist only mode: every web page
rendered anywhere on the device, including inside whitelisted apps' web views,
is refused unless its domain is listed. Only login domains for OAuth need
allowing. This closes the classic kiosk escape of browsing inside an allowed
app.

**No on-phone off switch.** The profiles are marked non removable. Nothing on
the device relaxes anything. The only ways out are listed under "Getting out",
and every one of them destroys all data on the phone.

**Hardening** (all supervised restriction keys, enforced by the OS):

| Escape route | Closed by |
| --- | --- |
| Remove the profiles in Settings | `PayloadRemovalDisallowed`; only the supervision host over USB can lift them |
| Erase from Settings | `allowEraseContentAndSettings` |
| Pair with another computer | `allowHostPairing`: the phone only pairs with the supervision host |
| Install anything | `allowAppInstallation`, `allowUIAppInstallation`, `allowAutomaticAppDownloads`, `allowAppClips`, `allowEnterpriseAppTrust` |
| Remove or offload apps | `allowAppRemoval` |
| Sign out of the Apple ID (disarms Activation Lock) | `allowAccountModification` |
| Add a VPN to dodge filtering | `allowVPNCreation` |
| Install rival profiles on device | `allowUIConfigurationProfileInstallation` |
| Talk or search your way to content (Siri, Spotlight web results) | `allowAssistant`, `allowSpotlightInternetResults` |
| Clock fiddling | `forceAutomaticDateAndTime` |
| Soften the wipe deterrent via iCloud Backup | `allowCloudBackup` (see "Getting out") |

## What you need

* A PC with iMazing (supervision needs a paid license, one time) and a USB
  cable.
* An iPhone you can erase.
* Optionally, a filtering DNS resolver (see "The DNS layer is optional").

## Setup

1. **Fill in your profiles.** Copy `clarity-restrictions.mobileconfig` to
   `clarity-restrictions.local.mobileconfig` and put your real apps in the
   allowlist (bundle IDs show in iMazing's Apps view, or search
   `itunes.apple.com/search?term=NAME&entity=software`). The `*.local.*`
   copies are gitignored, so your personal app list never gets committed. If
   you use the DNS layer, copy and fill `clarity-dns.mobileconfig` the same
   way.
2. **Erase and supervise.** Factory reset the iPhone. Run iMazing's
   Supervision wizard on the fresh device. iMazing generates a supervision
   identity certificate; keep it for now, it is your only future write access.
3. **Sign in and install.** Set up the phone, sign into the Apple ID, turn on
   Find My (this arms Activation Lock), install every whitelisted app from the
   App Store and log into each one while the store still exists.
4. **Install the profiles** over USB from iMazing: restrictions, web filter,
   and optionally DNS. They install silently on a supervised phone. The moment
   the restrictions land, the App Store, Safari and every unlisted app vanish.
5. **Live on it while you still hold the key.** The profiles cannot be removed
   on the phone, but this PC can still lift them over USB. Tune the app list
   and the web filter's login domains until the phone is right.
6. **Commit.** Two credentials decide how hard the wall is:
   * **Supervision identity.** Delete it from iMazing's library and from any
     PC backups, and no computer on earth can pair with the phone or touch the
     profiles again. Or export it to a USB stick held by someone you trust,
     which is a lift path that does not cost your data.
   * **Apple ID password.** Held by you, every wipe path stays open to you and
     Activation Lock is mere anti theft. Held or co held by someone else, the
     phone has zero phone-only wipe paths and even a wiped phone stays a brick
     until they consent.

## The DNS layer is optional

Only one behavior needs it: blocking native video inside whitelisted apps
while keeping their audio (for example streaming apps whose music you want but
whose video you do not). Everything else is enforced on the phone with no
external moving parts. Three postures:

* **None.** Skip the DNS profile. Native video inside whitelisted apps plays;
  everything else still holds. For Spotify specifically, video can instead be
  disabled at the account level: the Content and display settings carry
  toggles for canvas, music videos and all video content (rolling out), and on
  a family plan the plan manager can disable video per member such that the
  member cannot re enable it. Managed by someone you trust, that is real
  enforcement with no resolver at all. Verify it holds on your account before
  relying on it.
* **Self hosted.** AdGuard Home on a machine you control, rules in
  `||domain^` form from `nextdns-denylist.txt`, DoH with a valid certificate,
  and the DNS profile pointed at it. No third party, fails closed. The machine
  and its admin credentials are yours to keep alive and to escrow.
* **Hosted (NextDNS or ControlD).** Least effort. Paste
  `nextdns-denylist.txt` into the denylist, enable Block Bypass Methods, put
  the config ID into the DNS profile. **Pay for it**: free tiers stop
  filtering past a monthly quota and fail open. The dashboard login is a
  remote off switch, so escrow it too.

The pin applies to every interface, cellular and any wifi, and cannot be
disabled on the phone (`ProhibitDisablement`). Exact per flow filtering with
no server exists only as code: an `NEFilterDataProvider` extension, which
needs a paid Apple developer account and Xcode at build time (a cloud Mac
rented by the hour suffices; the built app installs via iMazing).

## Getting out: wipe from a computer, lose everything

The Settings erase is disabled. What remains:

| Wipe path | Gated by |
| --- | --- |
| Security Lockout erase (fail the passcode repeatedly) | demands the Apple ID password before erasing |
| Recovery/DFU restore | needs a computer and a cable; Activation Lock demands the Apple ID afterward |
| iCloud remote erase | demands the Apple ID password, from another device |

Every path destroys everything on the phone. The restrictions profile
disables iCloud Backup precisely so that stays true; a wipe then genuinely
costs every photo, message and login, which is the deterrent. Delete the
`allowCloudBackup` key from your `.local` copy if you would rather have
breakage insurance and a softer wall. Note that recovery mode on an iPhone
cannot wipe the device by itself; it only lets an attached computer restore
it, so with the Apple ID escrowed there is no phone-only way out at all.

## Honest limits

* **DNS filtering (if used) is hostname level** and dodgeable by an app with
  a hardcoded resolver IP. The web filter and the whitelist do not have this
  hole; only the video-inside-allowed-apps case rides on DNS.
* **Web views render pages unless the web filter profile is installed.** With
  it, the hole closes at the cost of maintaining a login domain allowlist.
  Captive portal pages (hotel wifi) are also WebKit and need their domains
  allowed if you use such networks.
* **The notification shade, Control Center and app switcher stay.** There is
  no multi app kiosk on iOS. With everything dangerous hidden or disabled
  there is nothing actionable in them.
* **Settings cannot be hidden**, only defanged: erase, account changes, VPN
  and profile installs are closed; wifi, cellular and Bluetooth remain
  available.
* **Wifi cannot be forced off** by any profile key. The DNS pin follows the
  phone onto every network, so wifi is not a filter bypass.
* **A paired Apple Watch may not enforce the app allowlist** (reported on
  Apple's developer forums). Test or do not pair one.
* **`allowAssistant` (Siri) is deprecated as of iOS 26.4**, still honored, no
  replacement key yet. Revisit if a future OS drops it.
* **Emergency dialing cannot be removed**, for legal and safety reasons.
* **Recovery/bootloader level attacks** are outside any profile's control;
  Activation Lock is the deterrent, not a guarantee.

## Files

* `clarity-restrictions.mobileconfig` app whitelist plus hardening (template,
  example apps only).
* `clarity-webfilter.mobileconfig` allowlist only web filter for every WebKit
  view (template).
* `clarity-dns.mobileconfig` pins all DNS to a filtering resolver (template,
  optional).
* `nextdns-denylist.txt` video and DoH host list for the resolver.
* `*.local.mobileconfig` your filled in copies, gitignored, never committed.
