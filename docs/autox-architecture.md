# AutoX Subsystem Architecture

## Overview

The AutoX feature lets AndroidAutoX project arbitrary Android apps onto a car head-unit
display via an isolated **VirtualDisplay** rendered through the Jetpack Car App SDK.

## Pipeline

```
Android Auto host
    │
    │  Car App SDK  (androidx.car.app)
    ▼
AutoXCarAppService  ─── onCreateSession() ──▶  AutoXSession
                                                    │
                                                    │  onCreateScreen()
                                                    ▼
                                               AutoXScreen  (SurfaceCallback)
                                                    │
                            SurfaceContainer        │  onSurfaceAvailable()
                            (width, height, dpi,    │
                             Surface)               │
                                               ╔════▼══════════════════════╗
                                               ║ 1. Build AutoXDisplaySpec  ║
                                               ║    from container geometry  ║
                                               ╚════╤══════════════════════╝
                                                    │
                                               ╔════▼══════════════════════════╗
                                               ║ 2. VirtualDisplayController    ║
                                               ║    DisplayManager              ║
                                               ║    .createVirtualDisplay(...)   ║
                                               ║    flags = defaultFlags()       ║
                                               ║    (PUBLIC|OWN_CONTENT|TRUSTED) ║
                                               ╚════╤══════════════════════════╝
                                                    │  displayId (>0)
                                               ╔════▼══════════════════════╗
                                               ║ 3. AppLauncher              ║
                                               ║    AppLaunchPolicy.canLaunch ║
                                               ║    PackageManager            ║
                                               ║    .getLaunchIntentForPackage ║
                                               ║    ActivityOptions            ║
                                               ║    .setLaunchDisplayId(id)    ║
                                               ╚════╤══════════════════════╝
                                                    │
                                               Guest app renders on VirtualDisplay
                                               frames flow back to car head unit
```

### Touch / gesture routing

Car-digitizer touch events arrive at `AutoXScreen` via the `SurfaceCallback` methods
(`onClick`, `onScroll`, `onFling`). The routing pipeline is:

```
Car surface coords (x, y)
    │
    ▼
CoordinateTranslator.translate(xCar, yCar)
    │  scales to virtual-display pixel space, clamps to bounds
    ▼
GestureSpec.tap(displayId, x, y)   or
GestureSpec.swipe(displayId, x1, y1, x2, y2, durationMs)
    │
    ▼
GestureInjector.inject(spec)
    │  (ReflectiveGestureInjector in production)
    ▼
InputManager#injectInputEvent  (hidden API, via reflection)
    │  routes to the correct window on the target display
    ▼
Guest app receives MotionEvent
```

## Testable-logic vs excluded-glue split

| Class | Layer | Coverage |
|---|---|---|
| `AutoXDisplaySpec` | Pure logic (no Android imports) | 100% required |
| `VirtualDisplayConfig` | Pure logic (flag constants + composition) | 100% required |
| `CoordinateTranslator` | Pure math (no Android imports) | 100% required |
| `GestureSpec` | Pure value object (no Android imports) | 100% required |
| `AutoXTargetApp` | Pure value object (no Android imports) | 100% required |
| `AutoXAppRegistry` | Pure registry (no Android imports) | 100% required |
| `AppLaunchPolicy` | Pure decision logic (no Android imports) | 100% required |
| `GestureInjector` | Interface — no executable lines | Excluded (safety) |
| `ReflectiveGestureInjector` | Reflection / InputManager (framework) | Excluded |
| `VirtualDisplayController` | DisplayManager (framework) | Excluded |
| `AppLauncher` | PackageManager / ActivityOptions (framework) | Excluded |
| `AutoXCarAppService` | CarAppService entry point (framework) | Excluded |
| `AutoXSession` | Car App Session (framework) | Excluded |
| `AutoXScreen` | Car App Screen + SurfaceCallback (framework) | Excluded |
| `AutoXForegroundService` | Service + PowerManager (framework) | Excluded |

## Input-injection privilege caveat

There is **no public Android API** for injecting touch events onto an arbitrary display
from a third-party app. `InputManager#injectInputEvent` is a `@hide` method guarded by
the `android.permission.INJECT_EVENTS` signature-level permission.

`ReflectiveGestureInjector` uses reflection to call this method. On a stock (non-root)
device the call will be silently dropped or throw `SecurityException`. On a rooted
device running AndroidAutoX with elevated privileges (via libsu), the injection works.

This is intentional: the `GestureInjector` interface is the seam. Tests and non-root
paths can provide a stub implementation. The reflection code is entirely inside
`ReflectiveGestureInjector`, which is excluded from the coverage gate, keeping the
untestable blast radius as small as possible.

## "Native APIs over shell" decision

The AutoX glue layer uses only standard Android / Jetpack APIs:

- `DisplayManager.createVirtualDisplay` — no `adb shell wm` or `/proc` writes
- `ActivityOptions.setLaunchDisplayId` — no `am start` shell strings
- `PackageManager.getLaunchIntentForPackage` — no `pm` shell commands
- `PowerManager.PARTIAL_WAKE_LOCK` — no `echo on > /sys/power/wake_lock`
- `InputManager#injectInputEvent` (hidden, via reflection) — no `input tap` shell strings

The only shell-adjacent path is `ReflectiveGestureInjector`, and even there the
invocation is a Java native-API call, not a `Runtime.exec` with a shell string.

## WS7 — Robustness and Security

### Restricted host allowlist

**Problem.** Shipping `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` in `AutoXCarAppService`
means any app that registers itself as an Android Auto host can connect to the AutoX
pipeline on a rooted device — a significant privilege-escalation risk.

**Solution.** `HostAllowlist` (pure, no Android imports) holds the allowlist data:

| Field | Value |
|---|---|
| Allowed package | `com.google.android.projection.gearhead` |
| SHA-256 digest | `F0:FD:6C:5B:41:0F:25:CB:25:C3:B5:33:46:C8:97:2F:AE:30:F8:EE:74:11:DF:91:04:80:AD:6B:2D:60:DB:83` |
| Digest source | Jetpack Car App SDK docs + community Play Store APK analysis |

`HostAllowlist#isAllowed(packageName, sha256Digest)` returns `true` only when both
match. `HostAllowlist#check(…)` returns a richer `AllowResult` enum
(`ALLOWED` / `REJECTED_NULL_PACKAGE` / `REJECTED_MALFORMED_DIGEST` /
`REJECTED_UNKNOWN_PACKAGE` / `REJECTED_WRONG_DIGEST`).

Digest comparison is **colon-agnostic and case-insensitive**: both plain 64-hex and
colon-separated forms are normalized to 64-char uppercase hex by
`HostAllowlist#normalizeDigest`.  Malformed digests (wrong length or non-hex chars)
return `null` from `normalizeDigest` and are skipped or rejected.

`AutoXCarAppService#createHostValidator()` iterates `HostAllowlist.createDefault().entries()`
and calls `HostValidator.Builder#addAllowedHost(packageName, digest)` for each digest.
The pure allowlist data is the tested unit; the `HostValidator.Builder` call is the
only Android-coupled line (the service is excluded from the coverage gate).

**Human-verification flag:** the baked-in digest should be confirmed on a real device:
```
adb shell pm get-app-signing-info -show-cert com.google.android.projection.gearhead
```
Compare the SHA-256 fingerprint against `HostAllowlist.GEARHEAD_SHA256`.

Multiple digests per package are supported in `HostAllowlist.HostEntry` for
key-rotation scenarios (list both old and new fingerprints while the fleet migrates).

### Connection lifecycle state machine

`ConnectionStateMachine` (pure, no Android imports) models the combined host + surface
lifecycle as a deterministic state machine to avoid duplicated transition logic in the
glue layer.

**States:**

| State | Meaning |
|---|---|
| `IDLE` | No host connected, no surface (initial state) |
| `CONNECTED` | Host connected, no surface yet |
| `SURFACE_ACTIVE` | Host connected AND surface available (virtual display running) |
| `DISCONNECTED` | Host disconnected |
| `RECONNECTING` | A reconnect attempt is in progress |

**Transition table:**

| From | Event | To |
|---|---|---|
| `IDLE` | `CONNECT` | `CONNECTED` |
| `IDLE` | `RECONNECT` | `RECONNECTING` |
| `CONNECTED` | `SURFACE_AVAILABLE` | `SURFACE_ACTIVE` |
| `CONNECTED` | `DISCONNECT` | `DISCONNECTED` |
| `SURFACE_ACTIVE` | `SURFACE_DESTROYED` | `CONNECTED` |
| `SURFACE_ACTIVE` | `DISCONNECT` | `DISCONNECTED` |
| `DISCONNECTED` | `RECONNECT` | `RECONNECTING` |
| `RECONNECTING` | `CONNECT` | `CONNECTED` |
| `RECONNECTING` | `DISCONNECT` | `DISCONNECTED` |
| *(any)* | *(undefined)* | *(unchanged — no-op)* |

All undefined (state, event) pairs are silently ignored so the machine is total and
crash-safe.

### Bounded exponential-backoff calculator

`ReconnectBackoff` (pure, no Android imports) computes a bounded exponential delay
sequence for reconnect attempts:

```
delay(n) = min(initialDelayMs × base^n, maxDelayMs)
```

Default parameters: initial = 1 s, base = 2, max = 30 s →
sequence: 1 s, 2 s, 4 s, 8 s, 16 s, 30 s, 30 s, … (capped).

Overflow safety: the pre-multiply check `delay >= maxDelayMs / base` ensures `delay *
base` is never computed when it would exceed `maxDelayMs`, preventing `long` overflow
at any combination of base and attempt count.  Negative or zero attempts are treated as
attempt 0 (return initial delay, clamped to max).

### Bounded wakelock in AutoXForegroundService

`AutoXForegroundService` acquires a `PowerManager.PARTIAL_WAKE_LOCK` via
`wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)` with a 4-hour backstop timeout.  The lock is
released in `onDestroy()`.  The timeout backstop guarantees the lock cannot leak
indefinitely if the process is killed without `onDestroy` running.

The service is started in `AutoXScreen#createDisplay()` and stopped in
`AutoXScreen#releaseDisplay()` — it is therefore strictly tied to the surface lifetime:
alive exactly while a virtual display is active, not for the whole app session.

### Pure vs excluded split (WS7 additions)

| Class | Layer | Coverage |
|---|---|---|
| `HostAllowlist` | Pure allowlist data + normalization + matching (no Android imports) | 100% required |
| `ConnectionStateMachine` | Pure state machine (no Android imports) | 100% required |
| `ReconnectBackoff` | Pure backoff calculator (no Android imports) | 100% required |

## Key files

| Path | Role |
|---|---|
| `app/src/main/java/com/xiddoc/androidautox/autox/` | All AutoX classes |
| `app/src/main/java/com/xiddoc/androidautox/autox/HostAllowlist.java` | WS7: pure host allowlist (allowlist data + digest normalization + matching) |
| `app/src/main/java/com/xiddoc/androidautox/autox/ConnectionStateMachine.java` | WS7: pure connection lifecycle state machine |
| `app/src/main/java/com/xiddoc/androidautox/autox/ReconnectBackoff.java` | WS7: pure bounded exponential-backoff calculator |
| `app/src/main/res/xml/automotive_app_desc.xml` | Car app descriptor (template capability) |
| `app/src/main/AndroidManifest.xml` | Service / permission declarations |
| `app/build.gradle` (`jacocoExclusions`) | Coverage gate exclusion list |
