# Coverage gap analysis

This is the working map for the **100% test-coverage initiative**. It is generated
from JaCoCo (`./gradlew jacocoTestReport`) over the **debug unit-test** run, after
applying the exclusion policy (see `app/build.gradle` `jacocoExclusions` and the
"Code coverage" section in `AGENTS.md`).

- **Counters gated:** `LINE` and `BRANCH`, both must reach 100% on included classes.
- **Generated:** by parsing
  `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`.
- **Excluded from the gate** (NOT in this list): generated code (`R`, `BuildConfig`,
  `Manifest`, `*_*Factory`, AIDL `IPhixitRoot*`, `Partition$*` stubs) and
  framework-entry classes (`MainActivity`, `SplashActivity`, `AccountsChooser`,
  `CarRemover`, `StreamLogs`, `PhixitRootService`, `ReapplyJobService`,
  `BootReceiver`, `AaxApp`, and the view-only dialog wrappers `AboutDialog`,
  `NoRootDialog`, `NotSuccessfulDialog`, `RebootDialog`).

## Current overall coverage (included classes only)

| Counter | Covered | Total | % |
| --- | ---: | ---: | ---: |
| LINE | 9 | 1263 | **0.71%** |
| BRANCH | 10 | 435 | **2.30%** |

Only `RootGate`, `RootGate$Decision`, `RebootFabVisibility`, and the two callback
interfaces are currently at 100%. Everything else is effectively at 0% — the
existing Robolectric/JUnit suite verifies behaviour but exercises very few of the
included lines. There is a lot of green to win.

## Per-class gap table (worst first)

| Class | Line% | Branch% | Missed lines | Missed branches |
| --- | ---: | ---: | ---: | ---: |
| `PhixitTweaks` | 0% | 0% | 235 | 25 |
| `PhixitEngine` | 0% | 0% | 207 | 152 |
| `PhixitSnapshot` | 0% | 0% | 102 | 58 |
| `BottomDialog` | 0% | 0% | 84 | 40 |
| `BottomDialog$Builder` | 0% | n/a | 67 | 0 |
| `TweakRegistry` | 0% | 0% | 66 | 21 |
| `AppsList` | 0% | 0% | 36 | 8 |
| `ReapplyScheduler` | 0% | 0% | 35 | 18 |
| `AppInfo` | 0% | 0% | 35 | 12 |
| `RootDb` | 0% | 0% | 34 | 10 |
| `Version` | 0% | 0% | 30 | 24 |
| `MyAdapter` | 0% | 0% | 29 | 2 |
| `AccountAdapter` | 0% | 0% | 28 | 2 |
| `RebootFabController` | 0% | 0% | 27 | 6 |
| `FlagSpec` | 0% | 0% | 26 | 2 |
| `CarAdapter` | 0% | 0% | 25 | 2 |
| `PhixitSnapshot$Reader` | 0% | 0% | 25 | 4 |
| `PhixitSnapshot$Writer` | 0% | 0% | 21 | 4 |
| `CarInfo` | 0% | n/a | 19 | 0 |
| `UtilsLibrary` | 0% | 0% | 15 | 2 |
| `Partition` | 0% | n/a | 14 | 0 |
| `CommonPageAdapter` | 0% | 0% | 13 | 2 |
| `RecyclerItemClickListener` | 0% | 0% | 11 | 6 |
| `AccountInfo` | 0% | n/a | 10 | 0 |
| `PhixitSnapshot$Flag` | 0% | 0% | 10 | 11 |
| `BottomDialog$2` | 0% | 0% | 6 | 4 |
| `BottomDialog$1` | 0% | 0% | 6 | 4 |
| `RecyclerItemClickListener$1` | 0% | 0% | 6 | 4 |
| `RootDb$1` | 0% | n/a | 6 | 0 |
| `MyAdapter$MyViewHolder` | 0% | n/a | 6 | 0 |
| `AccountAdapter$SampleViewHolder` | 0% | n/a | 5 | 0 |
| `CarAdapter$SampleViewHolder` | 0% | n/a | 5 | 0 |
| `MyAdapter$2` | 0% | n/a | 5 | 0 |
| `AccountAdapter$1` | 0% | n/a | 4 | 0 |
| `MyAdapter$1` | 0% | n/a | 4 | 0 |
| `CarAdapter$1` | 0% | 0% | 3 | 2 |
| `RootDb$2` | 0% | n/a | 3 | 0 |
| `RecyclerItemClickListener$OnItemClickListener` | 100% | n/a | 0 | 0 |
| `BottomDialog$ButtonCallback` | 100% | n/a | 0 | 0 |
| `RootGate` | 100% | 100% | 0 | 0 |
| `RootGate$Decision` | 100% | n/a | 0 | 0 |
| `RebootFabVisibility` | 100% | 100% | 0 | 0 |

(`n/a` branch = the class/inner-class has no branching code; only LINE matters.)

## Already at 100% (no work needed)

- `RootGate` (+ `RootGate$Decision`) — pure decision logic, already covered by `RootGateTest`.
- `RebootFabVisibility` — pure visibility logic, covered by `RebootFabVisibilityTest`.
- `RecyclerItemClickListener$OnItemClickListener`, `BottomDialog$ButtonCallback` — empty marker interfaces.

---

## Work buckets (for assigning tests)

### Bucket A — pure-logic POJOs / static helpers (easy, plain JUnit, no Android runtime)

These have no Android view/Context dependency in the methods that matter and can be
tested with plain JUnit assertions. Highest value-per-effort.

| Class | Notes / uncovered methods |
| --- | --- |
| `PhixitSnapshot` (+ `$Reader`, `$Writer`, `$Flag`) | Protobuf-style encode/decode, gzip inflate/deflate, hex<->bytes, varint/fixed64. All pure byte/string logic. Round-trip tests (`encode`→`decode`) cover most of it; cover `tryParseLong`, `bytesToHex`/`hexToBytes`/`digit` edge cases, `Flag.boolValue`/`describe`. |
| `FlagSpec` | Static factory helpers `bool/lng/dbl/str/bytes/remove/base` + ctor. Pure data. |
| `Version` | `get`, ctor, `compareTo`, `equals` — string/version comparison. Note 24 branches → test ordering, equality, unequal-length versions. |
| `AppInfo` | Getters/setters + `compareTo`. Has 12 branches in `compareTo` — cover null/checked ordering. |
| `CarInfo`, `AccountInfo` | Plain getter/setter POJOs (no branches). Trivial. |
| `Partition` | Parcelable data class. The `(Parcel)` ctor + `writeToParcel` need a `Parcel` (Robolectric gives a real one cheaply), but the `(long,byte[])` ctor + fields are pure. May be split A/B. |
| `TweakRegistry` | Static `*Specs(...)` builders return `List<FlagSpec>`. Most are pure; `specsFor`, `patchedAppsSpecs`, `enabledSpecs`, `anyEnabled` take a `Context` (SharedPreferences) → light Robolectric, but the per-tweak spec builders (`hunSpecs`, `mediaHunSpecs`, `usbBitrateSpecs`, `wifiBitrateSpecs`, `batteryWarningSpecs`) are pure and drive most of the 66 missed lines / 21 branches. |
| `UtilsLibrary` | `dpToPixels` + drawable builders. `dpToPixels` needs a `Context` (Robolectric); the `createButtonBackgroundDrawableBase/Lollipop` are mostly pure GradientDrawable construction. |

### Bucket B — needs Robolectric (Android Context / View / SharedPreferences / RecyclerView)

These touch real Android types and need the Robolectric runtime (already wired:
`org.robolectric:robolectric:4.12.2`, `includeAndroidResources = true`).

| Class | Why Robolectric / uncovered surface |
| --- | --- |
| `PhixitEngine` | **Biggest target: 207 lines / 152 branches.** Takes a `Context`, reads `SharedPreferences`, builds/serializes baselines, applies/reverts specs, diff/compare flags. Heavy branching in `applySpecs`, `revertSpecs`, `isApplied`, `serializeBaseline`/`deserializeBaseline`, `valueEquals`/`copyValue`. Inject a Robolectric `Context`; the DB/root calls go through seams that can be faked. Consider asking a refactor agent if any pure helpers (`baselineKey`, `serializeBaseline`, `valueEquals`, `findFlag`, `readLong`) can be made static/package-private for plain-JUnit testing. |
| `PhixitTweaks` | 235 lines / 25 branches. `specs(...)`/`has(...)` build the master flag tables. Likely pure data but large; verify against `Context` usage. If pure, this is actually Bucket A but big. |
| `ReapplyScheduler` | `Context` + `SharedPreferences` + `JobScheduler` (`sync`, `runOnceSoon`, `scheduleDeferredRetry`). Robolectric shadow job scheduler. |
| `RootDb` (+ `$1`, `$2`) | Binds a `RootService`; `init/get/readPartitions/writePartitions/query/exec`. Needs Robolectric `Context` + mocked `IPhixitRoot` binder. The `ServiceConnection` inner classes (`$1`, `$2`) need connect/disconnect simulation. |
| `RebootFabController` | View-driven FAB controller (27 lines / 6 branches). Already has `RebootFabControllerTest` but coverage shows 0% — existing test needs extending to hit `reveal`, `onPageChanged`, `apply`, `restoreRevealed`, `resolveFabRoot`. Robolectric Views. |
| `CommonPageAdapter` | PagerAdapter over views. Has `CommonPageAdapterTest` (0% currently) — extend to cover `insertViewId`, `instantiateItem`, `destroyItem`, `getPageTitle`, `getItemPosition`. |
| `MyAdapter` (+ inner `$1`,`$2`,`$MyViewHolder`) | RecyclerView adapter — inflate rows, bind, click callbacks. Robolectric `ViewGroup`/`LayoutInflater`. |
| `AccountAdapter` (+ inner classes) | Same shape as `MyAdapter`, for the accounts list. |
| `CarAdapter` (+ inner classes) | Same shape, for the car-removal list. |
| `RecyclerItemClickListener` (+ `$1`) | GestureDetector-based touch listener. Robolectric `MotionEvent`/`RecyclerView`. |
| `BottomDialog` (+ `$Builder`, `$1`, `$2`) | Dialog builder/inflater (84 + 67 lines). `$Builder` is a fluent builder (pure-ish, no branches) → easy; `BottomDialog.initBottomDialog`/`show`/`dismiss` and the click `$1`/`$2` need a Robolectric `Context`/`Dialog`. The `Builder` setters alone reclaim 67 lines cheaply. |
| `AppsList` | An `Activity` subclass NOT excluded (it is a thin list screen). `onCreate` is the only real method (36 lines / 8 branches). Robolectric `ActivityScenario`/`buildActivity`. **If `onCreate` is too instrumentation-heavy, flag for Bucket C extraction.** |

### Bucket C — excluded framework classes with extractable logic (refactor candidates)

These are **excluded** from the gate, so they are not required to reach 100%.
However, several hold non-trivial pure logic embedded in Activities/Services that
*should* be pulled into testable helper classes (Bucket A/B) so the real logic is
covered even though the framework shell stays excluded. Recommend a refactor agent:

| Excluded class | Logic worth extracting |
| --- | --- |
| `MainActivity` (~4300 lines) | Tweak orchestration, status-image state machine, command-string building, version/update comparison, any pure string/flag computation currently inline in click handlers. Extract into helper classes (e.g. a `TweakController`/`StatusResolver`) testable without the Activity. |
| `SplashActivity` | Root-request tri-state flow already partly extracted to `RootGate` (100%). Any remaining asset-copy / disclaimer-timer logic could be extracted. |
| `PhixitRootService` | The SQLite-as-root bridge. SQL building / partition diffing could be pulled to pure helpers; the binder/service shell stays excluded. |
| `ReapplyJobService` | The actual reapply decision/iteration logic could move to a pure helper (the `JobService` shell stays excluded). Pairs with `ReapplyScheduler`. |
| `BootReceiver` | Trivial; routes boot to scheduler. Decision (should-schedule) could be a pure predicate. |
| `AaxApp` | Application init; mostly framework wiring, little to extract. |
| `AccountsChooser`, `CarRemover`, `StreamLogs` | List screens; their adapters (`AccountAdapter`, `CarAdapter`, `MyAdapter`) are already in-scope (Bucket B). Any selection/diff logic in the Activity should move to a helper. |

> Note for the refactor agent: when you extract logic out of an excluded Activity
> into a new helper class, that helper is **automatically in scope** (it won't match
> the exclusion globs) and must reach 100%. Add it to a Bucket A/B test target.

## Known testability debt

- `PhixitEngine` reaches the shell (`MainActivity.runSuWithCmd`) and root SQLite
  (`RootDb.readPartitions`/`writePartitions`) through **static** calls, so its tests
  must mock those statically (Mockito `mockStatic`). Injecting `ShellRunner` /
  `PartitionStore` seams would make the engine testable without static mocking, but
  that refactor is deliberately **out of scope** here (it touches every call site and
  risks behavior drift); recorded as pre-existing debt for a future refactor pass.
- `RootDb.get()` and `ReapplyScheduler` reach the libsu `RootService.bind` and the
  platform `JobScheduler` through hard static calls. Tests cover them today by
  mocking those statics (Mockito `mockStatic` / Robolectric shadows). Introducing
  thin injectable seams (e.g. a `RootServiceBinder` / `JobSchedulerGateway`) would
  make the bind/schedule paths testable without static mocking, but that refactor
  is out of scope for the coverage initiative and is recorded here as a follow-up.

## How to regenerate this report

```bash
export ANDROID_HOME=$HOME/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew jacocoTestReport --no-daemon
# HTML: app/build/reports/jacoco/jacocoTestReport/html/index.html
# XML:  app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
./gradlew jacocoTestCoverageVerification --no-daemon   # fails until 100%
```
