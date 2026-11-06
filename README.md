# StudyGuard

A parental-control app for your child's Android phone. It's **visible and disclosed to
him** — it shows a persistent "StudyGuard is active" notification and a toast whenever it
blocks something, so he knows what's going on and that Claude (for studying) is fine but
YouTube/Chrome/etc. aren't.

## What it does

- You keep a list of blocked app package names in a **public GitHub repo** (just a plain
  text file — no personal data in it, so public is fine).
- The phone re-downloads that list every ~60 seconds.
- As soon as a blocked app's window comes to the front, it's sent back to the home screen
  immediately and a toast explains why (this is handled by the Accessibility Service in
  real time, not by polling — see "How blocking actually works" below).
- The app registers as a Device Administrator, which means it can't be uninstalled until
  its admin rights are deactivated in Settings — and the same Accessibility Service
  watches for that screen (and the app's own uninstall screen) and requires your PIN
  before it lets him proceed.

## How blocking actually works (read this if it's not working)

An earlier version of this app detected the foreground app with `UsageStatsManager` and
tried to send the phone home with `startActivity()` from a background service. **That
doesn't work reliably** — Android 10+ blocks apps from starting activities while they're
in the background, so the "go home" call was silently dropped by the OS even though
detection worked fine. Blocking now happens inside the **Accessibility Service** instead,
using `performGlobalAction(GLOBAL_ACTION_HOME)`, which is exempt from that restriction.
This is the same technique real screen-time / parental-control apps use, and it reacts
instantly instead of on a polling delay.

**This means the Accessibility Service being actually, truly turned on is the single most
important thing to get right.** If blocking isn't working, check that first — see
"Restricted settings" below, and use the in-app status row (Device Admin / Accessibility /
Overlay, each with ✅ or ❌) on the setup screen to confirm.

## Honest limitations (please read)

- This is **not** unbreakable. A factory reset, or booting into **Safe Mode** (which
  disables all non-system apps, including this one and its accessibility service), will
  get around the block. There's no way to fully prevent that without enrolling the phone
  as a managed "Device Owner" via full MDM provisioning at factory-reset time, which is a
  much bigger undertaking (and usually means wiping the phone to start).
- Because this app requests Device Admin and Accessibility access, it **cannot be
  published on Google Play** in this form — Play restricts that permission combination to
  apps approved as monitored MDM solutions. You'll install it by sideloading the APK
  directly (enable "Install unknown apps" for whatever source you use to transfer it).
- If this matters more than the effort of building this: **Google Family Link** already
  does app-blocking + remote management + uninstall protection, maintained by Google, for
  free, with none of the above caveats. This project is useful if you specifically want
  your own GitHub-controlled list, want it open-source/inspectable, or want this as a
  learning project.

## Installation steps

### Step 0 — do this first, on your own device

Before touching his phone, create the blocklist repo and note its raw URL — you'll need
it during setup. See [Set up the blocklist on GitHub](#1-set-up-the-blocklist-on-github)
below. Also pick a PIN you'll actually remember — see
[About the parent PIN](#about-the-parent-pin), because there's no recovery for it later
without clearing app data.

### 1. Set up the blocklist on GitHub

1. Create a **public** GitHub repo, e.g. `studyguard-list`.
2. Add a file `blocklist.txt` with one Android package name per line. Lines starting
   with `#` are comments.

   ```
   # Entertainment / social
   com.google.android.youtube
   com.android.chrome
   com.instagram.android
   com.facebook.katana
   com.whatsapp
   com.zhiliaoapp.musically
   com.snapchat.android
   ```

   (Package names, not display names — see
   [Finding the right package name for an app](#finding-the-right-package-name-for-an-app)
   below.)
3. Your raw URL will be:
   `https://raw.githubusercontent.com/<your-username>/studyguard-list/main/blocklist.txt`
4. To update the list later, just edit `blocklist.txt` in the GitHub web UI (from your
   phone or laptop) and commit — the app picks it up within a minute.

### 2. Build the APK

This is a standard Gradle Android project (Kotlin, min SDK 26).

**From Android Studio:** `File > Open` this `StudyGuard/` folder, let it sync, then
`Build > Build Bundle(s)/APK(s) > Build APK(s)`, or run it directly on the child's phone
over USB with `Run`.

**From the command line** (needs `JAVA_HOME` pointing at a JDK 17+ and the Android SDK
set up — e.g. on Windows with Android Studio installed):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

```bash
# macOS / Linux
export JAVA_HOME=/path/to/jdk-17-or-newer
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### 3. Install it on the child's phone

Transfer the APK to the phone (USB cable, a cloud drive, emailing it to yourself, etc.)
and open it there. Android will ask to allow "install unknown apps" for whichever app you
used to open the file (Files, a browser, etc.) — allow it, then tap Install.

### 4. Run through the on-phone setup wizard, in order

Open StudyGuard and work straight down the screen:

1. Enter your **parent PIN** (4+ digits) and the **blocklist raw URL** from step 1, tap
   **Save PIN + URL**. (If you come back to this screen later, the PIN field stays blank
   on purpose — leave it blank to keep your existing PIN; only type a new one if you
   actually want to change it.)
2. **Grant Device Admin** — tap through Android's "Activate this device admin app?"
   prompt.
3. **Enable the Accessibility Service** — this is the important one; see
   [Restricted settings](#restricted-settings-android-13) immediately below if the switch
   won't turn on.
4. **Grant "draw over other apps"** — helps the PIN screen appear promptly on some OEMs.
5. **Start StudyGuard.**

The status row at the top of the screen shows ✅/❌ for each permission — check it reads
all ✅ before assuming setup is done. From then on it keeps itself running in the
background (it also restarts on reboot) and refreshes the blocklist every minute.

### Restricted settings (Android 13+)

**This is the single most common reason blocking silently doesn't work.** Starting with
Android 13, apps installed by sideloading (not through the Play Store) have their
sensitive permissions — Accessibility Service is one — blocked by default, even if you
tap the toggle and it looks like it turned on. If the Accessibility switch won't stay on,
or StudyGuard doesn't appear in the Accessibility list at all:

1. Go to **Settings → Apps → StudyGuard**.
2. Tap the **⋮ (three-dot) menu** in the top-right corner.
3. Tap **"Allow restricted settings."**
4. Now go back to Accessibility settings and turn StudyGuard on — it should work this
   time.

## Finding the right package name for an app

`blocklist.txt` needs the app's **package name** (e.g. `com.google.android.youtube`), not
its display name ("YouTube") — Android only matches on the package name.

1. **Play Store URL (easiest).** Search the app on the Play Store website. The URL is
   `https://play.google.com/store/apps/details?id=com.example.app` — everything after
   `id=` is the package name.
2. **`adb` — read it directly off his phone (most reliable).** With the Android SDK's
   platform-tools installed and the phone connected over USB with Developer Options →
   USB debugging turned on:

   ```bash
   adb shell pm list packages -3
   ```

   This lists every user-installed app's real package name, so there's no guessing —
   useful for apps the Play Store search doesn't make obvious, renamed apps, or
   region-specific clones.
3. **Search it.** `"<app name> android package name"` works for most mainstream apps.

A couple of apps people expect to find that may not have an obvious match:
- **Valorant** is a PC/console-only Riot Games title — there's no official Android app in
  most regions, so an app with that name on the phone is likely a companion/stats app or
  an unofficial clone, not the real game.
- Games/apps with regional variants (e.g. different publishers per country) can have
  different package names than what shows up in a generic search — the `adb` method
  above is the only way to be sure for those.

## About the parent PIN

The PIN you enter during setup is stored **only** as a SHA-256 hash in the app's local
storage on that phone — it is never transmitted anywhere (not to GitHub, not to us, not
logged). That means:

- **There is no "forgot PIN" recovery.** If you lose it, the only way back in is
  Settings → Apps → StudyGuard → Storage → **Clear data** on the phone, which wipes the
  stored PIN, the saved blocklist URL, and the cached blocklist — you'll need to redo the
  whole setup wizard afterward with a new PIN.
- Pick something you'll actually remember, and write it down somewhere outside the phone
  (not in a note on the phone itself).
- Revisiting the setup screen later to grant a permission you skipped does **not**
  require retyping the PIN — only typing a new one actually changes it.

## Troubleshooting

- **A blocked app still runs / isn't sent home.** Check the status row on the setup
  screen — the Accessibility ✅ is the one that matters most for blocking. If it's ❌, see
  [Restricted settings](#restricted-settings-android-13) above.
- **PIN prompt pops up while you're still granting Device Admin during setup.** This was
  a bug in an earlier version (the PIN gate couldn't tell "granting admin" from
  "removing admin," since Android shows the same screen for both) — it's fixed now; the
  gate only triggers when admin rights are already active, i.e. actually being removed.
- **Still stuck?** Connect the phone over USB with USB debugging on and run
  `adb logcat | grep StudyGuard` (or filter for `AndroidRuntime` to catch crashes) while
  reproducing the issue — that will show exactly what's failing.

## Project layout

```
StudyGuard/
  app/src/main/java/com/studyguard/app/
    MainActivity.kt                     setup wizard + live permission status
    AppBlockerService.kt                blocklist refresh (every 60s) + persistent notification
    BlocklistFetcher.kt                 downloads/parses the GitHub raw file
    Prefs.kt                            stores PIN hash, URL, cached blocklist
    MyDeviceAdminReceiver.kt            device-admin callback
    UninstallGuardAccessibilityService.kt  blocks apps in real time + guards uninstall/admin screens
    PinLockActivity.kt                  PIN entry screen
    BootReceiver.kt                     restarts the service after reboot
```
