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

## Key files

| Path | Role |
|---|---|
| `app/src/main/java/com/xiddoc/androidautox/autox/` | All AutoX classes |
| `app/src/main/java/com/xiddoc/androidautox/autox/provider/` | WS4 provider seam (pure interfaces + policy/schema/table) |
| `app/src/main/java/com/xiddoc/androidautox/autox/provider/lsposed/` | WS4 LSPosed module glue (excluded) |
| `app/src/xposedStub/java/` | Local compileOnly Xposed API stub (offline `-PuseXposedStub=true` fallback) |
| `app/src/main/assets/xposed_init` | LSPosed module entry-class pointer |
| `app/src/main/res/xml/automotive_app_desc.xml` | Car app descriptor (template capability) |
| `app/src/main/AndroidManifest.xml` | Service / permission / LSPosed module declarations |
| `app/build.gradle` (`jacocoExclusions`) | Coverage gate exclusion list |
