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

---

## Debugging & diagnostics

AutoX's privileged paths (trusted virtual display, cross-display input injection, per-display
settings, audio routing) are **device-validation pending** — the `// TODO(device-verify)` markers
mark hidden-API guesses that can only be confirmed on a real head unit. They are the most likely
things to fail on the first on-device run, so the subsystem is **heavily instrumented** to make
those failures pinpointable. Logging is split by **process**, because AutoX runs in two:

| Process | Code | Log sink | How to read it |
| --- | --- | --- | --- |
| App / car-app process | `AutoXScreen`, `VirtualDisplayController`, `AppLauncher`, `LsposedInputInjector`, the providers | `AutoXLog` → logcat tag **`AutoX`** + an in-process ring buffer | `adb logcat -s AutoX` **or** the in-app **Copy Logs** button |
| `system_server` (LSPosed hooks) | `AutoXXposedModule`, `TrustedFlagBridge`, `InputInjectionBridge` | `XposedDebug` → `XposedBridge.log`, prefix **`AutoX/sys`** | **LSPosed Manager → Logs**, or `adb logcat` |

### App-side: `AutoXLog`

Every app-side AutoX step logs through `AutoXLog` (`autox/AutoXLog.java`), which mirrors each
entry to **two** sinks:

1. **logcat** under the tag `AutoX` — for a tethered session:
   ```bash
   adb logcat -s AutoX        # live AutoX trace (timestamp, level, area tag, message)
   ```
   Area tags identify the stage: `Screen`, `VDisplay`, `Launcher`, `Inject`, `FgService`.
   Caught exceptions are logged with their full stack trace (the stack trace is what pinpoints
   an unverified hidden-API guess).

2. **An in-process ring buffer** (bounded, newest-wins) — so the trace can be retrieved **without
   a cable**. Because the AutoX car services run in the app's default process (no
   `android:process` in the manifest), this static buffer is shared with `MainActivity`. The
   in-app **Copy Logs** button copies the on-screen tweak logs **plus** the full AutoX ring-buffer
   dump (`AutoXLog.dump()`) to the clipboard — paste it straight into a bug report.

At session start `AutoXScreen` logs a one-shot **environment snapshot** (`AutoXDiagnostics`):
SDK level, the resolved provider decision (`LSPOSED` vs `BLOCKED`), the display id + geometry,
whether injection is honored yet, and a one-line **verdict**. When a tester says "it didn't work",
this block answers the first questions at a glance — most often it reveals the provider decision is
`BLOCKED` because the **LSPosed module is not enabled in the LSPosed Manager** (AutoX requires it;
there is no root-only fallback).

```
==== AutoX diagnostics [createDisplay] ====
  sdkInt           = 34
  providerDecision = LSPOSED
  displayId        = 7
  geometry         = 1920x1080 @ 160dpi
  injectionHonored = false
  verdict          = OK — LSPosed path active
```

### system_server side: `XposedDebug`

The LSPosed hooks run inside `system_server`, where the app-side ring buffer is unreachable. They
log via `XposedDebug` (`autox/provider/lsposed/XposedDebug.java`) to `XposedBridge.log`, viewable
in the **LSPosed Manager app's log screen**. Grep handle: **`AutoX/sys`**. These lines trace each
`TODO(device-verify)` path as it executes:

- **`AutoX/sys/Module`** — module loaded into `system_server`, SDK, which hooks installed (or
  `DEGRADED` when the SDK has no `HookTargetSet`), and per-hook install success/failure.
- **`AutoX/sys/Trusted`** / **`AutoX/sys/TrustedBridge`** — the `createVirtualDisplay` frame, the
  name-gate decision, and whether `FLAG_TRUSTED` was actually OR'd into the flags arg (with the
  before/after hex).
- **`AutoX/sys/Inject`** / **`AutoX/sys/InjectBridge`** — the `injectInputEvent` frame, the
  extracted display id vs AutoX's display id, the gate decision, and whether the (unverified) mode
  relax / display-id stamp ran. A `no-act` line here means the event was **not** AutoX's display.
- **`AutoX/sys/ForceTrue`** — the per-display `shouldShowIme` / `shouldShowSystemDecors` /
  launch-on-display gates, with the hooked vs AutoX display id and the force decision.

Every fail-closed `catch` in a hook now logs the captured throwable via `XposedDebug.e(...)` before
swallowing it (a throw must never escape `system_server`), so a reflection mismatch shows up in the
LSPosed log instead of vanishing silently.

> `XposedDebug.VERBOSE` (in `XposedDebug.java`) gates the high-volume `v(...)` trace lines. It is
> left **on** while the privileged paths are device-validation pending; flip it off once the
> `TODO(device-verify)` markers are resolved and the per-frame noise is no longer wanted
> (milestone `i(...)` and error `e(...)` lines stay on regardless).

### Suggested capture during a test run

```bash
# Terminal 1 — app-side trace (clear first so the capture is just this run):
adb logcat -c && adb logcat -s AutoX

# Terminal 2 — everything from system_server with our prefix (LSPosed hooks):
adb logcat | grep 'AutoX/sys'
```

Then drive a projection session in the DHU. If it crashes or misbehaves, attach **both** captures
(plus the in-app **Copy Logs** clipboard dump) — together they cover both processes end to end.
