# AutoX Subsystem Architecture

## Status (honest summary — read first)

- **AutoX requires root + LSPosed (LSPosed-first single path).** Provider selection is binary
  (`LSPOSED` / `BLOCKED`); there is no root-only-vs-LSPosed dual support. Trusted-display +
  cross-display input injection go through LSPosed ONLY (the root-reflection injector
  `ReflectiveGestureInjector` and the root trusted-display provider `RootDisplayProvider` were
  removed; injection is now `LsposedInputInjector`). Settings writes stay on root. When LSPosed
  is inactive the feature is BLOCKED cleanly in both the phone UI (`MainActivity`) and the car
  surface (`AutoXScreen` renders a requires-LSPosed `MessageTemplate`). Phenotype/Gearhead root
  tweaks are unaffected.
- **WS2–WS7 pure logic is COMPLETE and unit-tested** to the repo's 100% line+branch gate
  (specs, policies, bounds/coordinate math, host allowlist, state machine, backoff, the
  shared settings entry/applier/result, the LSPosed `HookGatePolicy`, and the new pure
  glue-extracted helpers — `PriorCaptureGate`, `SettingsPriorMapper`, `AudioDeviceTypeMapper`,
  `CarOutputDeviceSelector`, `ProjectionStep`/`ProjectionStepPlan`, `ImePriorCodec`).
- **Runtime WIRING of that pure logic into the framework glue is now DONE.** The settings
  apply/revert call-sites (WS3 freeform/globals, WS5 per-display IME/decors, WS6 audio
  routing) and the LSPosed IPC `displayId` plumbing are wired in `AutoXScreen.createDisplay`
  / `AutoXScreen.releaseDisplay`. The screen obtains its privileged providers from
  `AutoXProviderFactory` (two-phase probe → reevaluate), captures+persists every prior in
  `AutoXSettingsStore` (gated by `PriorCaptureGate`) so revert survives process death, and
  walks the pure `ProjectionStepPlan` apply/revert order. This is code-complete and
  unit/compile-verified — it is **not** device-proven (see next bullet).
- **On-device validation is PENDING for every privileged path.** The privileged glue still
  carries `// TODO(device-verify)` markers where exact hidden-API signatures/values/return
  contracts can only be confirmed on a real rooted device / DHU — e.g. reading the
  trusted-flag back off the created display (`DisplayInfo.flags & Display.FLAG_TRUSTED`;
  `reevaluateProviders` currently defaults it conservatively to `false`, so the meaningful gate
  today is the pre-surface LSPosed check in `AutoXScreen.createDisplay`), `AudioManager`
  hidden-API reflection (`setPreferredDeviceForUid`/`removePreferredDeviceForUid`), the
  `injectInputEvent` argument/field positions, the live car-audio device type, the
  platform-signature probe, and the Car App SDK host-digest format. **Everything that can
  only be confirmed on a rooted device/DHU remains device-validation pending.**
- **`InputInjectionBridge` is implemented but UNVERIFIED on-device.** It is no longer a pure
  no-op placeholder: `allow()` now performs real per-SDK frame mutation (extract the target
  display id from the call frame, gate it against AutoX's display via `HookGatePolicy`, then
  best-effort stamp `mDisplayId` and — only when the per-SDK `modeArgIndex` is pinned —
  normalise the injection mode). It **fails closed** (any reflection/field error is swallowed,
  never thrown out of `system_server`) and is conservative (mode relaxation is skipped while
  the arg position is unverified, so a wrong-position write can never clobber the display id).
  But the field name (`mDisplayId`), the argument positions, and whether stamping the id is
  sufficient to clear the ownership check are all best-effort **guesses** marked
  `// TODO(device-verify)` — do not present this as a confirmed working injection bypass.
- **Capability probing**: there is no `ReflectiveCapabilityProbe` class. The static probes
  (LSPosed-active / platform-signed / root-available) and the surface-time signals are
  collected by the excluded `AutoXProviderFactory` glue (and `LsposedInputInjector#isInjectionHonored`)
  and turned into a **binary** decision (`LSPOSED` / `BLOCKED`) by the pure `CapabilityDecider` +
  `ProviderSelectionPolicy`, packaged in the pure `AutoXProviders` value object.

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
    │  (LsposedInputInjector in production — LSPosed-only path)
    ▼
InputManager#injectInputEvent  (hidden API, via reflection)
    │  LSPosed InputInjectionBridge hook (system_server) relaxes the per-display
    │  ownership check for AutoX's display, so the event reaches the target window
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
`FreeformGlobalSettingsSpec` (formerly `SecureSettingsSpec` — renamed because the keys live
in `Settings.Global`).  It emits shared `SettingsEntry` lists that the shared instance
`SettingsApplier` (constructed with `Namespace.GLOBAL`) writes against a
`SystemSettingsProvider`, aggregating outcomes into the shared `ApplyResult`
(continue-and-report).  The `LaunchBoundsCalculator` computes the launch-bounds rectangle
for the "forced vertical"
(portrait) case: on a landscape virtual display it shrinks the window width to satisfy a
target aspect ratio and centres it horizontally.

### Revert-on-disable

Before AutoX enables these keys it reads the current value of each key via the
`SystemSettingsProvider`.  The snapshot (a `null` for absent keys, an `Integer` for
present ones) is passed to `FreeformGlobalSettingsSpec.revertList` to produce the correct
per-key revert strategy:

- `RESTORE_PRIOR` — key had a value; write it back.
- `WRITE_DEFAULT` — key was absent; write `0` (feature off).

**WIRED (device-validation pending).** The apply/revert call-sites live in `AutoXScreen`
(framework-layer glue, excluded from the coverage gate):

- **Apply** (`AutoXScreen.applyFreeform`, called from `createDisplay`): the genuine prior
  value of each key is read through the provider seam
  (`AutoXProviders.settings().getGlobalInt(...)` → `SettingsResult`) and mapped to a boxed
  `Integer` prior via the pure `SettingsPriorMapper.toPrior` (OK → value; NOT_FOUND / DENIED
  / null → `null`). Priors are persisted in `AutoXSettingsStore`
  (`setPriorForceResizable` / `setPriorEnableFreeform`) — but **only on a fresh session**,
  gated by `PriorCaptureGate.shouldCapturePrior(AutoXSettingsStore.isEnabled(prefs))`. On a
  re-entrant apply (e.g. `createDisplay` re-runs after process death) the already-persisted
  genuine prior is re-read instead of re-captured (a live read would observe AutoX's own
  written value and permanently strand the setting). The AutoX values are then written via
  `new SettingsApplier(providers.settings(), Namespace.GLOBAL).apply(FreeformGlobalSettingsSpec.applyList(...))`.
- **Revert** (`AutoXScreen.revertFreeform`, called from `releaseDisplay`): the persisted
  priors are read back and `FreeformGlobalSettingsSpec.revertList(...)` →
  `SettingsApplier.revert(...)` restores them (RESTORE_PRIOR / WRITE_DEFAULT), after which the
  persisted priors are cleared.
- **Bounds**: `forcedVerticalBounds` runs `LaunchBoundsCalculator.forcedVertical(...)` and
  hands the resulting `Rect` to `AppLauncher.launch(pkg, displayId, bounds)`, which applies it
  via `ActivityOptions.setLaunchBounds` (honored only when freeform windowing is enabled).

Because the priors are persisted in `AutoXSettingsStore` and the enabled flag is set last
(after a successful apply) and cleared last (after every revert), the revert reconstructs the
correct list even after a process restart. Device-validation of the privileged GLOBAL writes
themselves is still pending.

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
| `FreeformGlobalSettingsSpec` | Pure spec: keys, enabled values, revert strategy (emits `SettingsEntry`) | 100% required |
| `LaunchBoundsCalculator` | Pure math: forced-vertical bounds computation (`fullDisplay` validates but ignores `densityDpi`) | 100% required |
| `SettingsEntry` / `ApplyResult` / `SettingsApplier` | Shared pure entry + result + instance applier (GLOBAL/SECURE) | 100% required |
| `SettingsPriorMapper` | Pure: maps a read `SettingsResult` → boxed `Integer` prior (OK→value; NOT_FOUND/DENIED/null→null) | 100% required |
| `PriorCaptureGate` | Pure: capture-prior vs re-apply decision keyed off the persisted enabled flag (process-death safety) | 100% required |
| `ProjectionStep` / `ProjectionStepPlan` | Pure: the bring-up apply order + (reverse) revert order the glue iterates against | 100% required |

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
| `FreeformGlobalSettingsSpec` | Pure spec — WS3 resizable/freeform keys + revert strategy | 100% required |
| `LaunchBoundsCalculator` | Pure math — forced-vertical bounds (WS3) | 100% required |
| `SettingsEntry` / `ApplyResult` / `SettingsApplier` | Shared pure entry/result + instance applier (no Android imports) | 100% required |
| `GestureInjector` | Interface — no executable lines | Excluded (safety) |
| `LsposedInputInjector` | Reflection / InputManager — LSPosed-only AutoX injection (framework) | Excluded |
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

`LsposedInputInjector` (the only AutoX `InputProvider`) uses reflection to call this method.
On a stock device the call is silently dropped or throws `SecurityException`. Root alone is
**not** sufficient — a rooted app is still not the target display's owner from
`system_server`'s point of view, so the per-display ownership check rejects the event. AutoX
therefore relies on the LSPosed `InputInjectionBridge` hook (running inside `system_server`,
gated to AutoX's display) to relax that check; only then does the injection reach the guest.
There is **no root-reflection injection fallback** — when LSPosed is inactive AutoX is BLOCKED.

This is intentional: the `GestureInjector` / `InputProvider` interfaces are the seam. Tests and
non-LSPosed paths provide a stub / no-op implementation. The reflection code is entirely inside
`LsposedInputInjector`, which is excluded from the coverage gate, keeping the untestable blast
radius as small as possible.

## "Native APIs over shell" decision

The AutoX glue layer uses only standard Android / Jetpack APIs:

- `DisplayManager.createVirtualDisplay` — no `adb shell wm` or `/proc` writes
- `ActivityOptions.setLaunchDisplayId` — no `am start` shell strings
- `PackageManager.getLaunchIntentForPackage` — no `pm` shell commands
- `PowerManager.PARTIAL_WAKE_LOCK` — no `echo on > /sys/power/wake_lock`
- `InputManager#injectInputEvent` (hidden, via reflection) — no `input tap` shell strings

The only shell-adjacent path is `LsposedInputInjector`, and even there the
invocation is a Java native-API call, not a `Runtime.exec` with a shell string.

## WS4 — privileged-provider seam (LSPosed-first single path)

WS4 abstracts every privileged action AutoX needs behind small **provider interfaces**
(`autox/provider/`), so later workstreams depend on the seam, not on *how* the privilege
is obtained.

**AutoX requires root (baseline) AND LSPosed.** This is a deliberate single clean path — there
is no dual root-only-vs-LSPosed support. The split is by *which* privileged action:

- **Trusted-display flag + cross-display input injection — LSPosed ONLY.** Neither has a stable
  root-only path (even a rooted app is not the display owner from `system_server`'s point of
  view), so the **LSPosed module** hooks in `system_server` relax the trusted-display and
  input-injection checks at the source (`autox/provider/lsposed/AutoXXposedModule`,
  `TrustedFlagBridge`, `InputInjectionBridge`, each gated by the pure `HookGatePolicy` to
  AutoX's display). The module reads the app's commands over `XSharedPreferences` using the pure
  `IpcCommand` schema, picks targets from the per-SDK `HookTargetTable`, and **fails closed** —
  every hook body is try/caught so nothing ever throws out of `system_server`. The app-side
  injection is issued by `LsposedInputInjector` (the sole AutoX `InputProvider`). The former
  root-reflection injector (`ReflectiveGestureInjector`) and the root trusted-display provider
  (`RootDisplayProvider`) were **removed** — there is no root injection/trusted fallback.
- **Settings writes — root.** Per-display IME / system-decors (`Settings.Secure`) and
  freeform/resizable (`Settings.Global`) are written by `RootSystemSettingsProvider` (root is
  the clean, stable answer; not moved to LSPosed). Audio routing (`RootAudioRouter`) likewise
  stays on root.
- **No LSPosed → BLOCKED (no silent degrade).** Both the phone UI (`MainActivity`) and the car
  surface (`AutoXScreen`) block the feature with a clear "requires LSPosed" message rather than
  degrading.

### Provider factory — two-phase selection (WIRED)

`AutoXScreen` no longer constructs the `Root*`/`Reflective*` providers directly; it obtains
them from `AutoXProviderFactory` (excluded glue) in two phases:

1. **`AutoXProviderFactory.probe(Context)`** runs at screen construction (the surface does not
   exist yet). It performs only the cheap *static* probes (LSPosed-active, platform-signed,
   root-available — all best-effort, never blocking, never throwing), feeds the two
   projection-critical capabilities (trusted-display-honored, injection-honored) as
   conservatively `false`, runs the pure `CapabilityDecider` + `ProviderSelectionPolicy`, and
   returns a **provisional** `AutoXProviders` bundle (`isProvisional() == true`) with an
   `UnboundDisplayProvider` placeholder. The `InputProvider` is the LSPosed-backed
   `LsposedInputInjector` when LSPosed is active and a no-op `BlockedInputProvider` otherwise
   (AutoX is BLOCKED then). With LSPosed active the provisional decision is `LSPOSED`; without
   it, `BLOCKED`.
2. **`AutoXProviders.reevaluate(trusted, injection)`** runs in `AutoXScreen.createDisplay`
   once the virtual display exists. It folds in the now-observable surface-time signals and
   purely recomputes the decision. `injectionHonored` is read from `providers.input().isInjectionHonored()`;
   `trustedDisplayHonored` is honestly defaulted to `false` because `AutoXScreen` owns its own
   `VirtualDisplayController` (the WS4 `DisplayProvider` seam is still unbound) and the trusted
   flag has not been read back off `DisplayInfo.flags` (`// TODO(device-verify)`). Because of
   that conservative default, the **meaningful gate today is the pre-surface LSPosed check**:
   `AutoXScreen.createDisplay` refuses to create the display (and shows the requires-LSPosed
   `MessageTemplate`) unless the provisional decision is `LSPOSED`. Once the real trusted-flag
   read is wired, the post-surface reevaluate can additionally block an ineffective hook.

`AutoXProviders` is a pure value object (not excluded) and stays at 100%; `reevaluate` only
re-runs the pure decider/policy and carries the provider instances (and the display
placeholder) over unchanged.

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
| `ProviderSelectionPolicy` | Pure binary decision: caps → `LSPOSED`/`BLOCKED` + reason (LSPosed-first single path) | 100% required |
| `IpcCommand` | Pure app↔module wire schema (encode/decode over XSharedPreferences) | 100% required |
| `HookDescriptor`, `HookTargetSet`, `HookTargetTable` | Pure per-SDK (31–34) hook-target table + resolver (incl. unknown-SDK branch) | 100% required |
| `CapabilityDecider`, `TrustedFlagPolicy` | Pure capability-from-probe (inputs from existing glue; no `ReflectiveCapabilityProbe` class) + trusted-flag math | 100% required |
| `HookGatePolicy` | Pure act/no-act gate (primitives) so LSPosed hooks act only for AutoX's display | 100% required |
| `AutoXProviders` | Pure value object: bundles the chosen providers + decision; `reevaluate` re-runs the decider/policy purely; `isBlocked()` predicate | 100% required |
| `InputProvider`/`DisplayProvider`/`SystemSettingsProvider`/`AudioRouter` | Pure interfaces (no executable lines) | n/a |
| `RootSystemSettingsProvider` | Settings.Global/Secure framework plumbing (root path) | Excluded |
| `LsposedInputInjector` | App-side half of the LSPosed-only AutoX injection path (`InputProvider`); InputManager reflection | Excluded |
| `AutoXProviderFactory` | Probes the live system (Context/ContentResolver/InputManager/AudioManager/libsu Shell) and assembles the provider bundle (LSPosed-backed input when LSPosed active, else no-op `BlockedInputProvider`); all probe→decision mapping is in the pure decider/policy | Excluded |
| `IpcCommandWriter` | App-side writer for the LSPosed IPC channel (Android `Context`/`SharedPreferences`, world-readable mode); command construction/validation is in the pure `IpcCommand` | Excluded |
| `AutoXXposedModule`, `TrustedFlagBridge` | LSPosed/Xposed `system_server` reflection (each gated by the pure `HookGatePolicy` to AutoX's display) | Excluded |
| `InputInjectionBridge` | LSPosed/Xposed `system_server` reflection: `allow()` now performs real per-SDK frame mutation (display-id extract → `HookGatePolicy` gate → stamp `mDisplayId` + conditional mode-relax), fail-closed, but UNVERIFIED on-device (best-effort field/arg-position guesses, `// TODO(device-verify)`) | Excluded |

### Selection policy

`ProviderSelectionPolicy.select(ProviderCapabilities)` is total, **binary**, and exhaustively
tested (LSPosed-first single path):

- LSPosed NOT active → `BLOCKED` (reason names "requires LSPosed").
- LSPosed active but the trusted-display hook is not honored (post-surface) → `BLOCKED`
  (reason names the ineffective trusted-display hook).
- LSPosed active but input injection is not honored (post-surface) → `BLOCKED` (reason names
  the ineffective injection hook).
- LSPosed active and (when observable) both honored → `LSPOSED`.

`root`/`platform-signature` no longer affect this decision — trusted-display + input injection
are LSPosed-only. (Root is still required as a baseline by `AutoXEnablementPolicy`, and settings
writes stay on root, but root is not a provider alternative here.) The old `ROOT_REFLECTION` /
`DEGRADED` outcomes and the `AutoXProviders.isDegraded()` predicate were removed; the predicate
is now `AutoXProviders.isBlocked()`.

### LSPosed IPC channel — WIRED (device-validation pending)

When the (provisional) decision is `LSPOSED`, `AutoXScreen` drives the app-side
`IpcCommandWriter` to publish commands into a shared-prefs channel the `AutoXXposedModule`
reads from `system_server` via `XSharedPreferences`. The channel is:

- **World-READABLE, NOT world-writable.** The writer always requests `MODE_WORLD_READABLE` so
  that, when the app is loaded as an LSPosed module, LSPosed's hook makes the file readable
  from `system_server`; the file stays owned by the app's UID at `0644`, so no other app can
  forge or tamper with the commands.
- **Untrusted + display-id gated (second line of defence).** Every command is treated as
  UNTRUSTED by the module: each display-scoped command is gated by the pure `HookGatePolicy`
  against AutoX's own display id and fails closed, so a forged/stale command cannot relax any
  per-display hook on a display that is not AutoX's.

**Call ordering (matters).** `enableTrustedDisplay()` carries no id (it is scoped by display
*name* in the module) and is written **before** `createVirtualDisplay`
(`AutoXScreen.maybeEnableTrustedDisplayHook`, gated on the stable provisional `LSPOSED`
selection), so the trusted-flag hook can act as the display is created. The id-scoped commands
— `allowInputInjection(id)`, `setDisplayImeAndDecors(id, true)`, `launchOnDisplay(id, pkg)` —
are written only **after** the display exists and its id is known
(`AutoXScreen.maybeApplyLsposedDisplayCommands`). On teardown `clearLsposedCommands` empties
the channel (`clear()`); after a process death it reconstructs a fresh writer to clear a stale
channel.

The `IpcCommand` schema gained a `LAUNCH_ON_DISPLAY` command type (relaxes the launch-on-display
caller check) and a fail-safe `displayId()` accessor that parses the `displayId` argument back
(returning `NO_DISPLAY_ID` / `-1` on absent/blank/non-parseable values, which the gate treats as
"no display known"). `InputInjectionBridge.allow()` is implemented (real per-SDK frame mutation)
but UNVERIFIED on-device — see the status block and the WS4 table.

### Xposed API dependency

`de.robv.android.xposed:api:82` is added **compileOnly** (repo `https://api.xposed.info/`);
LSPosed provides those classes at runtime in `system_server`, so they are never bundled.
If the artifact can't be resolved offline, `-PuseXposedStub=true` swaps in a thin local
compileOnly stub source set (`app/src/xposedStub/java`) so the glue still compiles; the
resulting APK is identical. The module is declared via manifest `xposedmodule` /
`xposedminversion` / `xposeddescription` / `xposedscope` (scope `android` = system_server)
meta-data plus `assets/xposed_init` naming `AutoXXposedModule`.

> The provider seam and its call-site wiring (`AutoXProviderFactory` two-phase probe →
> `AutoXScreen` reevaluate, the LSPosed `IpcCommandWriter` channel) are wired and verified here
> by unit tests + compilation. Real LSPosed/device acceptance — the trusted display, input
> injection, and per-display hooks actually working on a head unit — is **device-validation
> pending** (the `// TODO(device-verify)` markers in the bridges remain).

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

### Call-site wiring — WIRED (device-validation pending)

The routing is wired into `AutoXScreen` (excluded glue):

- **Apply** (`AutoXScreen.applyAudioRouting`, called from `createDisplay` after the guest app
  launches — skipped if the launch failed, since there is no live UID): resolves the guest
  UID via `PackageManager.getPackageUid(...)`, enumerates the live output devices via
  `AudioManager.getDevices(GET_DEVICES_OUTPUTS)`, picks the first car sink with the pure
  `CarOutputDeviceSelector.firstCarDeviceIndex(...)`, maps its type to a `CarAudioDevice` with
  the pure `AudioDeviceTypeMapper.fromAudioDeviceInfoType(...)`, runs `AudioRoutePolicy.decide`,
  and applies via `AudioRouteApplier.apply(decision, providers.audio())`.
- **Revert** (`AutoXScreen.revertAudioRouting`, called from `releaseDisplay`): clears the
  per-UID affinity via `AudioRouteApplier.revert(decision, providers.audio())`.

### Revert-on-disable guarantee (process-death safe)

`AudioRouteApplier.revert(decision, router)` is called when AutoX is disabled or the
projection session ends. The `ClearAffinity` revert step releases the binding so the
guest app's audio returns to default routing immediately — no device restart required.
If routing was never applied (NoRoute decision), `revert` returns `false` without
touching the AudioManager, so the phone's audio state is never disturbed.

**Crash-safe revert.** The transient `RouteDecision` held in `AutoXScreen` is lost on a
process death, so — consistent with the WS3/WS5 priors — the applied route is now PERSISTED in
`AutoXSettingsStore` (the `AudioRouteState` record: routed UID + `CarAudioDevice` enum name +
device address), written only when a real route was applied. On a cold-start teardown
`revertAudioRouting` reconstructs the `ClearAffinity` decision from the persisted state (via
`AudioRoutePolicy.decide`) so the affinity is still cleared, then clears the persisted state so
it is not re-reverted. The privileged `AudioPolicy`/`setPreferredDeviceForUid` reflection itself
remains device-verify pending.

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
| `AudioDeviceTypeMapper` | Pure: maps an `AudioDeviceInfo` type int → `CarAudioDevice` (BT_A2DP / USB / NONE) | 100% required |
| `CarOutputDeviceSelector` | Pure: first-match selection of a car output device from a list of device-type ints | 100% required |
| `AudioRouteState` (`AutoXSettingsStore`) | Persisted routed UID + device name/address so the clear survives process death | 100% required |
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

## WS5 — IME + Speech-to-Text on the virtual display

### §2.3 IME / STT handshake

When a user focuses a text field in the guest app running on the AutoX virtual display,
Android's `WindowManagerService` (WMS) must route the IME window and input-connection
events to that display. This requires three prerequisites, all of which AutoX configures:

#### 1. Trusted display (prerequisite for IME admission)

The virtual display must be created with
`VirtualDisplayConfig.FLAG_TRUSTED` (`VIRTUAL_DISPLAY_FLAG_TRUSTED`, value `1024`).
An *untrusted* display is silently ignored by WMS for both IME routing and system-decors
rendering, regardless of the Secure settings values below. The trusted flag is set by
`VirtualDisplayController.defaultFlags()`.

#### 2. Per-display `shouldShowSystemDecors` flag

WMS checks `Settings.Secure` key `display_should_show_system_decors_<displayId>`. When
the value is `1`, the system renders system-UI layers (status bar, nav bar, IME frame)
onto the display. AutoX must set this key **before** attempting to show the IME, because
the IME frame layer is a system-UI layer itself. Without it, the software keyboard window
is never admitted to the display surface.

#### 3. Per-display `shouldShowIme` flag

WMS checks `Settings.Secure` key `display_should_show_ime_<displayId>`. When the value is
`1`, WMS routes IME windows to this display and forwards `InputConnection` events — including
speech-to-text text injections — to the focused window on the display.

#### Full handshake sequence

```
Guest app text field focused (on VirtualDisplay)
    │
    ▼  WMS checks per-display flags
    │
    │  shouldShowSystemDecors_<id> == 1  ─── (written by SettingsApplier, SECURE)
    │  shouldShowIme_<id>          == 1  ─── (written by SettingsApplier, SECURE)
    │  display is TRUSTED               ─── (VirtualDisplayController.defaultFlags())
    ▼
WMS routes IME window to VirtualDisplay
    │
    ▼  IME renders keyboard on VirtualDisplay surface
    │  frames flow back to car head-unit via Car App SDK SurfaceCallback
    ▼
User types / speaks
    │
    │  (typed keys)
    │       ──▶  KeyEvent injected into VirtualDisplay focused window
    │
    │  (STT: speech recognised by Android STT engine)
    │       ──▶  text committed into InputConnection of focused EditText on VirtualDisplay
    ▼
Guest app EditText receives text
```

#### Focus routing note

Tap (touch) events injected via `GestureInjector.inject(GestureSpec.tap(...))` cause
`WindowManagerService` to update input focus on the virtual display, which in turn
triggers `InputMethodManager` to show the IME. The per-display flags above are the
*gating* condition; the IME does not pop up merely because the flags are set — a focused
focusable view on the display (caused by a tap gesture) is still required to trigger
`InputMethodManager#showSoftInput`.

#### STT text delivery

When the user dictates text, Android's `SpeechRecognizer` / `InputMethodService` delivers
the recognised string by committing it into the active `InputConnection`. Because WMS
routes input connections to the focused window on the virtual display (via the
`shouldShowIme` flag), the STT text lands directly in the guest app's `EditText`
without any additional wiring in AutoX.

#### Apply / Revert lifecycle

`ImeDisplaySettingsSpec.forDisplay(displayId)` builds the spec. The pure `ImeSettingsReader`
reads the prior values from the `SystemSettingsProvider` (to support a clean revert) and
returns a populated spec. `spec.applyEntries()` then yields shared `SettingsEntry` objects
that the shared instance `SettingsApplier` (constructed with `Namespace.SECURE`) writes,
in apply order (system-decors first, then IME). On session teardown (virtual display
released), `spec.revertEntries()` + `SettingsApplier.revert(...)` restores the prior values
in reverse order (IME first, then system-decors).

> Migration note: the former bespoke `ImeDisplaySettingsApplier` (and its private
> `ApplyResult`/`Entry` types) has been removed. IME settings now use the same shared
> `SettingsEntry` / `SettingsApplier` / `ApplyResult` types as the freeform/global path; the
> only IME-specific piece left is the pure `ImeSettingsReader` prior-read helper.

#### WS4 provider seam

Both keys are protected by `WRITE_SECURE_SETTINGS` (a signature-level permission).
The shared `SettingsApplier` writes them via the `SystemSettingsProvider` interface
(WS4 seam), which is backed at runtime by the **root** implementation
(`RootSystemSettingsProvider` / `settings put`). Settings writes deliberately stay on root —
it is the clean, stable path — and are NOT moved to LSPosed (only the trusted-display flag and
cross-display input injection go through LSPosed).

`cmd window set-display-settings` is a documented adb/shell fallback for debugging only
— AutoX does **not** use it at runtime. The `SystemSettingsProvider` seam is the sole
implementation path.

#### Apply / Revert call-site wiring — WIRED (device-validation pending)

The IME spec/reader/applier are wired into `AutoXScreen` at session start/stop (excluded
from the coverage gate, so device/human-validated rather than unit-tested):

- **Apply** (`AutoXScreen.applyImeSettings`, called from `createDisplay` once the
  `displayId` is known): builds `ImeDisplaySettingsSpec.forDisplay(displayId)`; on a fresh
  session (gated by `PriorCaptureGate`) it reads the genuine per-display priors via
  `new ImeSettingsReader(providers.settings()).readPriors(spec)` and persists them in
  `AutoXSettingsStore` (`setPriorShouldShowSystemDecors` / `setPriorShouldShowIme`), mapping
  the spec's sentinel-int prior to a boxed `Integer` through the pure `ImePriorCodec.toBoxedPrior`
  (`VALUE_UNSET → null`). It then writes the AutoX values via
  `new SettingsApplier(providers.settings(), Namespace.SECURE).apply(spec.applyEntries())`
  (system-decors first, then IME).
- **Revert** (`AutoXScreen.revertImeSettings`, called from `releaseDisplay`): rebuilds the
  spec from the persisted per-display priors
  (`AutoXSettingsStore.getPriorShouldShow...OrUnset` → `withPriorValues`) and calls
  `SettingsApplier.revert(spec.revertEntries())` (IME first, then decors), then clears the
  per-display priors via `clearPriorsForDisplay`.

Per-display priors are persisted in `AutoXSettingsStore` so the revert survives process
death. Device-validation of the privileged SECURE writes (and the IME actually routing to the
virtual display on a head unit) is still pending.

## Key files

| Path | Role |
|---|---|
| `app/src/main/java/com/xiddoc/androidautox/autox/` | All AutoX classes |
| `app/src/main/java/com/xiddoc/androidautox/autox/FreeformGlobalSettingsSpec.java` | WS3 pure spec: resizable/freeform keys + revert strategy (formerly `SecureSettingsSpec`) |
| `app/src/main/java/com/xiddoc/androidautox/autox/LaunchBoundsCalculator.java` | WS3 pure forced-vertical bounds computation |
| `app/src/main/java/com/xiddoc/androidautox/autox/provider/SettingsEntry.java` · `ApplyResult.java` · `SettingsApplier.java` | Shared pure entry/result + instance applier (GLOBAL/SECURE) |
| `app/src/main/java/com/xiddoc/androidautox/autox/provider/lsposed/HookGatePolicy.java` | Pure act/no-act gate scoping LSPosed hooks to AutoX's display |
| `app/src/main/java/com/xiddoc/androidautox/autox/provider/` | WS4 provider seam (pure interfaces + policy/schema/table; pure `AutoXProviders` holder) |
| `app/src/main/java/com/xiddoc/androidautox/autox/provider/AutoXProviderFactory.java` | WS4 excluded glue: two-phase `probe(Context)` → provisional bundle → `reevaluate` |
| `app/src/main/java/com/xiddoc/androidautox/autox/provider/lsposed/` | WS4 LSPosed module glue (excluded), incl. `IpcCommandWriter`, `InputInjectionBridge`, and `LsposedInputInjector` (the only AutoX `InputProvider`) |
| `app/src/main/java/com/xiddoc/androidautox/autox/AutoXScreen.java` | Excluded glue: wires WS3/WS5/WS6 + LSPosed apply/revert call-sites in `createDisplay`/`releaseDisplay` |
| `app/src/main/java/com/xiddoc/androidautox/autox/AutoXSettingsStore.java` | Prior/route-state persistence so revert survives process death (pure, JUnit-tested) |
| `app/src/main/java/com/xiddoc/androidautox/autox/PriorCaptureGate.java` · `SettingsPriorMapper.java` · `ProjectionStep.java` · `ProjectionStepPlan.java` | Pure glue-extracted helpers (WS3 / ordering) |
| `app/src/main/java/com/xiddoc/androidautox/autox/AudioDeviceTypeMapper.java` · `CarOutputDeviceSelector.java` | WS6 pure audio device-type mapping / first-match selection |
| `app/src/main/java/com/xiddoc/androidautox/autox/ime/ImePriorCodec.java` | WS5 pure sentinel-int ↔ boxed-Integer prior codec |
| `app/src/xposedStub/java/` | Local compileOnly Xposed API stub (offline `-PuseXposedStub=true` fallback) |
| `app/src/main/assets/xposed_init` | LSPosed module entry-class pointer |
| `app/src/main/java/com/xiddoc/androidautox/autox/HostAllowlist.java` | WS7: pure host allowlist (allowlist data + digest normalization + matching) |
| `app/src/main/java/com/xiddoc/androidautox/autox/ConnectionStateMachine.java` | WS7: pure connection lifecycle state machine |
| `app/src/main/java/com/xiddoc/androidautox/autox/ReconnectBackoff.java` | WS7: pure bounded exponential-backoff calculator |
| `app/src/main/res/xml/automotive_app_desc.xml` | Car app descriptor (template capability) |
| `app/src/main/AndroidManifest.xml` | Service / permission / LSPosed module declarations |
| `app/build.gradle` (`jacocoExclusions`) | Coverage gate exclusion list |
