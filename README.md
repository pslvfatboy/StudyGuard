# StudyGuard

A parental-control app for your child's Android phone. It's **visible and disclosed to
him** — it shows a persistent "StudyGuard is active" notification and a toast whenever it
blocks something, so he knows what's going on and that Claude (for studying) is fine but
YouTube/Chrome/etc. aren't.

## What it does

- You keep a list of blocked app package names in a **public GitHub repo** (just a plain
  text file — no personal data in it, so public is fine).
- The phone re-downloads that list every ~60 seconds.
- Every ~2 seconds it checks which app is in the foreground. If it's on the list, the
  phone is sent back to the home screen and a toast explains why.
- The app registers as a Device Administrator, which means it can't be uninstalled until
  its admin rights are deactivated in Settings — and an Accessibility Service watches for
  that screen (and the app's own uninstall screen) and requires your PIN before it lets
  him proceed.

## Honest limitations (please read)

- This is **not** unbreakable. A factory reset, or booting into **Safe Mode** (which
  disables all non-system apps, including this one and its accessibility service), will
  get around the block. There's no way to fully prevent that without enrolling the phone
  as a managed "Device Owner" via full MDM provisioning at factory-reset time, which is a
  much bigger undertaking (and usually means wiping the phone to start).
- Because this app requests Device Admin, Accessibility, and Usage Access, it **cannot be
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
it during setup. See [Finding the blocklist URL](#1-set-up-the-blocklist-on-github)
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

### 4. Run through the on-phone setup wizard

Open StudyGuard and, in order:

1. Enter your **parent PIN** (just numbers, 4+ digits) and the **blocklist raw URL** from
   step 1, tap **Save PIN + URL**.
2. **Grant Device Admin** — tap through Android's prompt to activate it.
3. **Grant Usage Access** — Android will open a settings list; find StudyGuard and allow
   it.
4. **Enable the Accessibility Service** — Android will open Accessibility settings; find
   StudyGuard under "Installed apps" (or "Downloaded apps") and turn it on.
5. **Grant "draw over other apps"** — needed on some phones so the PIN screen can appear
   promptly on top of Settings.
6. **Start StudyGuard.**

From then on it keeps itself running in the background (it also restarts on reboot) and
refreshes the blocklist every minute.

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

## Project layout

```
StudyGuard/
  app/src/main/java/com/studyguard/app/
    MainActivity.kt                     setup wizard
    AppBlockerService.kt                foreground-app polling + blocklist refresh
    BlocklistFetcher.kt                 downloads/parses the GitHub raw file
    Prefs.kt                            stores PIN hash, URL, cached blocklist
    MyDeviceAdminReceiver.kt            device-admin callback
    UninstallGuardAccessibilityService.kt  watches Settings for uninstall/admin screens
    PinLockActivity.kt                  PIN entry screen
    BootReceiver.kt                     restarts the service after reboot
```
