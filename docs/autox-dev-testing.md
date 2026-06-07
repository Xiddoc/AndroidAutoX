# AutoX Developer Testing Guide

## Overview

The **Desktop Head Unit (DHU)** is the primary development loop for Android Auto CarAppService
work — no physical car is required. The DHU is bundled with the Android SDK and renders the
car head-unit UI on your desktop, connecting to the Android Auto app running on a device or
emulator over a local TCP port.

> **Important:** DHU/device acceptance tests cannot be run on this build server (no display,
> no Android device attached). All DHU and on-device validation is **pending human tester
> confirmation**. The steps below document the exact procedure a human tester must follow.

---

## Step 1 — Enable Android Auto Developer Mode

Android Auto hides its developer settings behind a tap sequence. Without developer mode on, a
third-party `CarAppService` (such as `AutoXCarAppService`) is **not discoverable** by the
Android Auto host: it will not appear in the app launcher on the head unit.

1. On the phone, open **Android Auto** (the Gearhead app).
2. Tap the **hamburger menu** (three lines, top-left) → **About**.
3. Tap the **version number** 10 times rapidly.
4. A dialog appears: **"Developer mode enabled"** (or you are prompted to enter a code —
   the default code is `1337`).
5. Developer settings are now accessible via the hamburger menu → **Developer settings**.

### Enable "Unknown sources"

Within Developer settings, enable **"Allow apps from unknown sources"**
(also labeled "Unknown sources" depending on AA version). This allows side-loaded / debug APKs
(including the AndroidAutoX debug build) to be projected as a car app.

---

## Step 2 — Start the head-unit server on the device

In Android Auto developer settings, tap **"Start head unit server"**.

This starts the local TCP server that the DHU connects to (default port 5277).

---

## Step 3 — Forward the port with adb

On the desktop, run:

```bash
adb forward tcp:5277 tcp:5277
```

This tunnels the DHU's localhost:5277 connection through adb to the device's port 5277.
Re-run this after each `adb kill-server` or device reconnection.

---

## Step 4 — Launch the Desktop Head Unit

The DHU is located in the Android SDK under `extras/google/auto/`:

```bash
# Typical path (adjust SDK root as needed):
$ANDROID_SDK_ROOT/extras/google/auto/desktop-head-unit
```

Or launch it from Android Studio: **Tools → Android Auto → Desktop Head Unit**.

Once the DHU opens, it connects to the forwarded port and mirrors what would appear on the
car's display. The AutoX app (when enabled and a target package is configured) should appear
in the app launcher on the DHU.

---

## Key facts

- A third-party navigation `CarAppService` is **only discoverable with AA developer mode ON**
  and "Unknown sources" enabled. Without these, the service is silently ignored by the Android
  Auto host regardless of the `AndroidManifest.xml` declarations.
- The DHU is the authoritative dev loop: it exercises the full Car App SDK pipeline without
  needing a physical head unit.
- Gestures, surface callbacks (`onSurfaceAvailable`, `onClick`, `onScroll`, `onFling`) all fire
  normally through the DHU, so the `AutoXScreen` → `CoordinateTranslator` → `GestureInjector`
  pipeline can be observed end-to-end.
- The `adb forward` command must be re-run each time the device or adb daemon is restarted.

---

## Build + install for DHU testing

```bash
# Provision SDK once (see AGENTS.md "Toolchain in a fresh / cloud environment")
export ANDROID_HOME=$HOME/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Build and install
./gradlew assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## DHU/device acceptance status

**Pending human validation.** This build server has no display and no attached Android device,
so the DHU cannot be launched here. A human tester must:

1. Follow the steps above to enable AA dev mode + unknown sources.
2. Install the debug APK.
3. Forward port 5277 and launch the DHU.
4. Verify that the AutoX entry appears in the DHU launcher when the AutoX feature is enabled
   in AndroidAutoX and a target package is selected.
5. Verify that tapping the AutoX button in the AndroidAutoX tweak list toggles the enabled
   state and opens the target-app picker.
