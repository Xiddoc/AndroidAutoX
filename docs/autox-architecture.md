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

## WS2 — Surface lifecycle: resize vs recreate

When `onSurfaceAvailable` fires more than once (the host can call it on geometry change
without an intervening `onSurfaceDestroyed`), `AutoXScreen` must decide whether to:

- **NOOP** — geometry unchanged; nothing to do.
- **RESIZE** — same `Surface` object, different dimensions/dpi; call
  `VirtualDisplay.resize(w, h, dpi)` on the existing display. Avoids the cost of
  destroying/recreating the display and re-launching the guest app.
- **RECREATE** — the host supplied a new `Surface` object (the old surface is
  invalid); release the old `VirtualDisplay` and create a fresh one.

The decision is made by the pure helper `SurfaceGeometry.decide(oldW, oldH, oldDpi,
newW, newH, newDpi, surfaceIdentityChanged)`.  `AutoXScreen` extracts primitives from
the framework `SurfaceContainer` and delegates to `SurfaceGeometry`; this keeps the
policy 100% unit-testable while the glue remains in `jacocoExclusions`.

`VirtualDisplayController.resize(newSpec)` wraps `VirtualDisplay.resize` and updates
the stored spec so `getSpec()` always reflects the current geometry.

## WS3 — Resizable / freeform launch enablement

AutoX must ensure any arbitrary app (including those with `resizeableActivity="false"`)
can be launched onto its virtual display, and that `ActivityOptions.setLaunchBounds`
(used for forced-vertical layout) is honored.  Two `Settings.Global` keys control this:

| Key | Value | Effect |
|---|---|---|
| `force_resizable_activities` | 1 | Overrides per-app `resizeableActivity="false"` so all activities are treated as resizable |
| `enable_freeform_support` | 1 | Enables freeform windowing mode; required for `setLaunchBounds` to be honored on non-desktop builds |

### Spec, applier, and bounds calculator

All policy (which keys, which values, revert strategy) lives in the pure helper
`SecureSettingsSpec`.  The thin applier `FreeformSettingsApplier` executes the spec
against a `SystemSettingsProvider` — it contains no decision logic.  The
`LaunchBoundsCalculator` computes the launch-bounds rectangle for the "forced vertical"
(portrait) case: on a landscape virtual display it shrinks the window width to satisfy a
target aspect ratio and centres it horizontally.

### Revert-on-disable

Before AutoX enables these keys it reads the current value of each key via the
`SystemSettingsProvider`.  The snapshot (a `null` for absent keys, an `Integer` for
present ones) is passed to `SecureSettingsSpec.revertList` to produce the correct
per-key revert strategy:

- `RESTORE_PRIOR` — key had a value; write it back.
- `WRITE_DEFAULT` — key was absent; write `0` (feature off).

The actual call-site that triggers revert is in the AutoX enable/disable flow
(framework-layer glue, excluded from the coverage gate):

```java
// TODO(WS3) call-site in AutoXScreen.onAutoXDisabled() / enable-policy disable path:
//   List<SecureSettingsSpec.Entry> revertEntries =
//       SecureSettingsSpec.revertList(priorForceResizable, priorEnableFreeform);
//   FreeformSettingsApplier.revert(revertEntries, provider);
```

The prior values must be persisted between enable and disable (e.g. in
`AutoXSettingsStore`) so the revert can reconstruct the correct list even after a
process restart.

### Forced-vertical bounds (§2.4)

`LaunchBoundsCalculator.forcedVertical(displayWidth, displayHeight, densityDpi,
targetAspectRatio)` computes the largest portrait sub-rectangle that fits within the
display:

- Display is already portrait (height ≥ width): full display area is returned.
- Display is landscape: `boundsWidth = floor(displayHeight / targetAspectRatio)`,
  clamped to `displayWidth`; the rectangle is centred: `left = (displayWidth − boundsWidth) / 2`.

The glue layer (excluded) converts `LaunchBoundsCalculator.Bounds` to `android.graphics.Rect`
and passes it to `ActivityOptions.setLaunchBounds`.

### Pure-logic vs excluded-glue split (WS3 additions)

| Class | Layer | Coverage |
|---|---|---|
| `SecureSettingsSpec` | Pure spec: keys, enabled values, revert strategy | 100% required |
| `LaunchBoundsCalculator` | Pure math: forced-vertical bounds computation | 100% required |
| `FreeformSettingsApplier` | Thin loop over spec + provider (no Android imports) | 100% required |

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
| `SurfaceGeometry` | Pure decision logic — resize-vs-recreate policy (WS2) | 100% required |
| `SecureSettingsSpec` | Pure spec — WS3 resizable/freeform keys + revert strategy | 100% required |
| `LaunchBoundsCalculator` | Pure math — forced-vertical bounds (WS3) | 100% required |
| `FreeformSettingsApplier` | Pure loop (no Android imports) — applies/reverts spec (WS3) | 100% required |
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

## WS4 — privileged-provider seam (root-reflection vs LSPosed)

WS4 abstracts every privileged action AutoX needs behind small **provider interfaces**
(`autox/provider/`), so later workstreams depend on the seam, not on *how* the privilege
is obtained. Two implementations sit behind the seam:

- **Root reflection** — best-effort use of `@hide` framework APIs from a rooted /
  platform-signed process (`ReflectiveGestureInjector`, `RootSystemSettingsProvider`,
  `RootDisplayProvider`). These DETECT and REPORT when a privileged operation silently
  fails (e.g. `VIRTUAL_DISPLAY_FLAG_TRUSTED` stripped, `injectInputEvent` dropped) via
  capability flags rather than throwing.
- **LSPosed module** — hooks in `system_server` that relax the trusted-display,
  input-injection, per-display IME / system-decor and launch-on-display checks at the
  source (`autox/provider/lsposed/AutoXXposedModule`). It reads the app's commands over
  `XSharedPreferences` using the pure `IpcCommand` schema, picks targets from the per-SDK
  `HookTargetTable`, and **fails closed** — every hook body is try/caught so nothing ever
  throws out of `system_server`.

### Provider interfaces (the contract later workstreams depend on)

| Interface | Purpose | Depended on by |
|---|---|---|
| `InputProvider` | inject gestures to a display + `isInjectionHonored()` | WS3 |
| `DisplayProvider` | create/resize/release virtual display + `isTrustedDisplayHonored()` | projection |
| `SystemSettingsProvider` | `putGlobalInt`/`getGlobalInt`/`putSecureInt`/`getSecureInt` → `SettingsResult` | WS3/WS5 |
| `AudioRouter` | `setUidAffinity`/`clearUidAffinity` | WS6 |

### Pure (100%-tested) vs excluded-glue split

| Class | Layer | Coverage |
|---|---|---|
| `SettingsResult`, `ProviderCapabilities` | Pure value/result objects | 100% required |
| `ProviderSelectionPolicy` | Pure decision: caps → `LSPOSED`/`ROOT_REFLECTION`/`DEGRADED` + reason | 100% required |
| `IpcCommand` | Pure app↔module wire schema (encode/decode over XSharedPreferences) | 100% required |
| `HookDescriptor`, `HookTargetSet`, `HookTargetTable` | Pure per-SDK (31–34) hook-target table + resolver (incl. unknown-SDK branch) | 100% required |
| `CapabilityDecider`, `TrustedFlagPolicy` | Pure capability-from-probe + trusted-flag math | 100% required |
| `InputProvider`/`DisplayProvider`/`SystemSettingsProvider`/`AudioRouter` | Pure interfaces (no executable lines) | n/a |
| `RootSystemSettingsProvider`, `RootDisplayProvider` | Settings/DisplayManager framework plumbing | Excluded |
| `ReflectiveGestureInjector` | now also implements `InputProvider` | Excluded |
| `AutoXXposedModule`, `TrustedFlagBridge`, `InputInjectionBridge` | LSPosed/Xposed `system_server` reflection | Excluded |

### Selection policy

`ProviderSelectionPolicy.select(ProviderCapabilities)` is total and exhaustively tested:
LSPosed-active → `LSPOSED`; else if a privileged path exists (root or platform signature)
AND trusted-display AND input-injection are honored → `ROOT_REFLECTION`; otherwise
`DEGRADED` (with a specific reason: no path / trusted not honored / injection dropped).

### Xposed API dependency

`de.robv.android.xposed:api:82` is added **compileOnly** (repo `https://api.xposed.info/`);
LSPosed provides those classes at runtime in `system_server`, so they are never bundled.
If the artifact can't be resolved offline, `-PuseXposedStub=true` swaps in a thin local
compileOnly stub source set (`app/src/xposedStub/java`) so the glue still compiles; the
resulting APK is identical. The module is declared via manifest `xposedmodule` /
`xposedminversion` / `xposeddescription` / `xposedscope` (scope `android` = system_server)
meta-data plus `assets/xposed_init` naming `AutoXXposedModule`.

> Real LSPosed/device acceptance (trusted display + input injection actually working on a
> head unit) is pending human validation; WS4 is verified here by unit tests + compilation.

## WS6 — Audio routing (per-UID device affinity)

When a guest app is projected onto the car head-unit via a `VirtualDisplay`, its audio
output must also be directed to the car's audio channel (BT A2DP or USB bus), not to the
phone speaker. WS6 implements this through a pure-policy / thin-applier / root-reflection-impl
stack, consistent with the testable-logic vs excluded-glue split established in WS4.

### Routing model

Android exposes per-UID audio device affinity via `@hide` APIs on `AudioManager`:
- `setPreferredDeviceForUid(int uid, AudioDeviceInfo device)` (API 31+)
- `removePreferredDeviceForUid(int uid)` (API 31+)

These are signature-guarded (`MODIFY_AUDIO_ROUTING`). From a root / platform-signed
process the calls succeed and pin all audio produced by `uid` to the nominated device,
leaving every other app's audio (including the phone call stack and the user's music
player) completely unaffected.

### Class structure

```
AudioRoutePolicy  (pure, 100%-tested)
  decide(uid, CarAudioDevice{BT_A2DP|USB|NONE}, deviceAddress)
  → RouteDecision {
        applyStep:  SetAffinity(uid, addr)  |  NoRoute(reason)
        revertStep: ClearAffinity(uid)      |  NoRoute(reason)
    }

AudioRouteApplier  (pure, 100%-tested — uses a fake AudioRouter in tests)
  apply(RouteDecision, AudioRouter)   → delegates SetAffinity → setUidAffinity()
  revert(RouteDecision, AudioRouter)  → delegates ClearAffinity → clearUidAffinity()

AudioRouter  (interface, provider seam — WS4)
  setUidAffinity(uid, deviceAddress)  → boolean
  clearUidAffinity(uid)               → boolean

RootAudioRouter  (excluded glue — AudioManager reflection, needs device)
  implements AudioRouter via setPreferredDeviceForUid / removePreferredDeviceForUid
  fails closed (returns false, never throws) on any reflection / permission error
```

### Decision rules (AudioRoutePolicy)

1. **Invalid UID** (`uid <= 0`) → `NoRoute` (both apply and revert steps).
2. **NONE device** → `NoRoute` (no car audio sink available).
3. **Null / blank address** → `NoRoute` (device address is required to locate the sink).
4. **Valid UID + BT_A2DP or USB + non-blank address** → `SetAffinity` apply / `ClearAffinity` revert.

### Revert-on-disable guarantee

`AudioRouteApplier.revert(decision, router)` is called when AutoX is disabled or the
projection session ends. The `ClearAffinity` revert step releases the binding so the
guest app's audio returns to default routing immediately — no device restart required.
If routing was never applied (NoRoute decision), `revert` returns `false` without
touching the AudioManager, so the phone's audio state is never disturbed.

### Phone playback unaffected

The per-UID affinity is scoped exclusively to the projected app's UID. All other UIDs
(phone call audio, media player, in-call audio) retain their default routing. The
`AudioRouter` interface provides no mechanism to route audio for any UID other than the
one supplied in the `RouteDecision`, so unintended side-effects are architecturally
impossible at the applier layer.

### Testable-logic vs excluded-glue split (WS6)

| Class | Layer | Coverage |
|---|---|---|
| `AudioRoutePolicy` | Pure logic (no Android imports) — enum + decision + value objects | 100% required |
| `AudioRouteApplier` | Pure logic — dispatches RouteStep to AudioRouter | 100% required |
| `AudioRouter` | Interface — no executable lines | n/a |
| `RootAudioRouter` | AudioManager + hidden-API reflection (framework, needs device) | Excluded |

> Device acceptance (projected app audio plays on car channel; phone unaffected; binding
> reverts on disable) is pending human validation on a real head unit with root access.

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
| `app/src/main/java/com/xiddoc/androidautox/autox/SecureSettingsSpec.java` | WS3 pure spec: resizable/freeform keys + revert strategy |
| `app/src/main/java/com/xiddoc/androidautox/autox/LaunchBoundsCalculator.java` | WS3 pure forced-vertical bounds computation |
| `app/src/main/java/com/xiddoc/androidautox/autox/FreeformSettingsApplier.java` | WS3 thin applier (spec + provider) |
| `app/src/main/java/com/xiddoc/androidautox/autox/provider/` | WS4 provider seam (pure interfaces + policy/schema/table) |
| `app/src/main/java/com/xiddoc/androidautox/autox/provider/lsposed/` | WS4 LSPosed module glue (excluded) |
| `app/src/xposedStub/java/` | Local compileOnly Xposed API stub (offline `-PuseXposedStub=true` fallback) |
| `app/src/main/assets/xposed_init` | LSPosed module entry-class pointer |
| `app/src/main/java/com/xiddoc/androidautox/autox/HostAllowlist.java` | WS7: pure host allowlist (allowlist data + digest normalization + matching) |
| `app/src/main/java/com/xiddoc/androidautox/autox/ConnectionStateMachine.java` | WS7: pure connection lifecycle state machine |
| `app/src/main/java/com/xiddoc/androidautox/autox/ReconnectBackoff.java` | WS7: pure bounded exponential-backoff calculator |
| `app/src/main/res/xml/automotive_app_desc.xml` | Car app descriptor (template capability) |
| `app/src/main/AndroidManifest.xml` | Service / permission / LSPosed module declarations |
| `app/build.gradle` (`jacocoExclusions`) | Coverage gate exclusion list |
