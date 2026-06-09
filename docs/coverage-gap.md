# Coverage status

> **Status: the 100% coverage initiative is complete and enforced in CI.**
> This file was historically a live "gap map" tracking the climb from ~0.7% to 100%.
> That climb is done — the JaCoCo gate now **fails the build** if any *included* class
> drops below **100% LINE and BRANCH** (`./gradlew jacocoTestCoverageVerification`,
> run on every push/PR by `.github/workflows/build.yml`). What remains useful here is
> the **scope policy** and the **how-to-regenerate / testability-debt** notes below;
> the old per-class "worst first" gap table has been removed because it is no longer a
> backlog (everything in scope is at 100%).

This is enforced over the **debug unit-test** run (Robolectric + plain JUnit) after
applying the exclusion policy (see `app/build.gradle` `jacocoExclusions` and the
"Code coverage" section in `AGENTS.md`).

- **Counters gated:** `LINE` and `BRANCH`, both must reach 100% on included classes.
- **Generated from:** `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`.

## What is in scope (must stay at 100%)

Every hand-written logic/POJO/helper class that does **not** match an exclusion glob.
This includes all the pure-logic classes: `RootGate`, `TweakRegistry`, `PhixitTweaks`,
`PhixitEngine`, `PhixitSnapshot`, `FlagSpec`, `RootDb`, `Version`, `UtilsLibrary`, the
adapters, `RebootFabController`, `ReapplyScheduler`, and the entire AutoX pure layer
(`AutoXDisplaySpec`, `CoordinateTranslator`, `VirtualDisplayConfig`, `GestureSpec`,
`AutoXTargetApp`, `AutoXAppRegistry`, `AppLaunchPolicy`, `HookGatePolicy`,
`ProviderSelectionPolicy`, `CapabilityDecider`, `IpcCommand`, the settings/audio specs,
**and the diagnostic helpers `AutoXLog` + `AutoXDiagnostics`**).

> **Rule of thumb:** when you extract logic out of an excluded framework class into a
> new helper, that helper is **automatically in scope** (it won't match an exclusion
> glob) and must ship with tests that hit 100%. The diagnostic logger `AutoXLog` and
> the environment-report formatter `AutoXDiagnostics` are recent examples — both are
> pure enough to unit-test (the only Android touch-point, `android.util.Log`, is a
> no-op under the JVM test runtime), so both are in scope and fully covered by
> `AutoXLogTest` / `AutoXDiagnosticsTest`.

## What is excluded (and why)

The authoritative list is the `jacocoExclusions` Groovy variable in `app/build.gradle`
(commented inline). Two groups only:

1. **Generated code** — `R`/`R$*`, `BuildConfig`, `Manifest*`, `*_*Factory`, and the
   AIDL-generated bridge stubs (`IPhixitRoot*`, `Partition$*`). The hand-written
   `Partition` POJO itself is *not* excluded.
2. **Irreducible framework / Xposed glue that cannot be unit-tested off-device** — the
   Activities (`MainActivity`, `SplashActivity`, `AccountsChooser`, `CarRemover`,
   `StreamLogs`), Services (`PhixitRootService`, `ReapplyJobService`), `BootReceiver`,
   `AaxApp`, the view-only dialog wrappers, and the AutoX framework glue
   (`AutoXScreen`, `AutoXCarAppService`, `AutoXSession`, `AutoXForegroundService`,
   `VirtualDisplayController`, `AppLauncher`, `RootSystemSettingsProvider`,
   `RootAudioRouter`, `AutoXProviderFactory`, and the `lsposed/` bridges
   `AutoXXposedModule` / `TrustedFlagBridge` / `InputInjectionBridge` /
   `LsposedInputInjector` / `IpcCommandWriter` / `XposedDebug`).

These excluded classes are where the AutoX **diagnostic logging** lives — they are the
device-coupled paths, so heavy logging there carries no coverage cost (see
`docs/autox-dev-testing.md` → "Debugging & diagnostics").

## Known testability debt (carried forward)

- `PhixitEngine` reaches the shell (`MainActivity.runSuWithCmd`) and root SQLite
  (`RootDb.readPartitions`/`writePartitions`) through **static** calls, so its tests
  mock those statically (Mockito `mockStatic`). Injecting `ShellRunner` /
  `PartitionStore` seams would remove the static mocking but touches every call site;
  deliberately **out of scope**, recorded as debt for a future refactor pass.
- `RootDb.get()` and `ReapplyScheduler` reach libsu `RootService.bind` and the platform
  `JobScheduler` through hard static calls; covered today via static mocks / Robolectric
  shadows. Thin injectable seams (`RootServiceBinder` / `JobSchedulerGateway`) would make
  these testable without static mocking — a follow-up, not a gap.

## How to regenerate the report

```bash
export ANDROID_HOME=$HOME/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew jacocoTestReport --no-daemon
# HTML: app/build/reports/jacoco/jacocoTestReport/html/index.html
# XML:  app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
./gradlew jacocoTestCoverageVerification --no-daemon   # fails the build if < 100%
```

If `jacocoTestCoverageVerification` fails, open the HTML report, find the class(es)
below 100%, and either add the missing tests (in-scope logic) or — only for irreducible
new framework glue — add the class to `jacocoExclusions` with an inline justification.
