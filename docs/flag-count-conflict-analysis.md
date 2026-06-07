# GMS background crash: "Encountered conflicting flags. Expected flag count N, but was M" — Analysis & Fix

**Status:** root-cause analysis + implemented mitigation. Honest about residual risk (closed-source GMS).
**Symptom:** every now and then, GMS's persistent process crashes silently in the background:

```
java.lang.IllegalArgumentException: Encountered conflicting flags. Expected flag count 453, but was 440.
  at gjww.c(...)  eztp.b(...)  ... HeterodyneSyncTaskChimeraService.d(...) ...
```

The crash is inside GMS's Phenotype **HeterodyneSyncTaskChimeraService** during a periodic flag sync.
"Expected flag count 453, but was 440" is a 13-flag discrepancy: the flag set GMS reconstructs from
`phenotype.db` does not match the count GMS persisted in its committed-config metadata.

---

## 1. How this app edits flags (the relevant code path)

AndroidAutoX edits Android Auto's Phenotype flags by rewriting the compressed `flags_content` blobs in
`param_partitions` of `/data/data/com.google.android.gms/databases/phenotype.db`:

- `PhixitSnapshot` decodes a partition's `flags_content` (raw-DEFLATE → a hand-rolled protobuf-style
  list of `{name, type, value}` flag entries) and re-encodes it.
- `PhixitEngine.applySpecs(...)` decodes each partition, applies the `FlagSpec`s, re-encodes, and writes
  the blobs back via `RootDb.writePartitions(toWrite, servingVersion)` (bumping `last_fetch.serving_version`
  to epoch seconds), then clears the phenotype **file** cache dir and force-stops GMS.

The load-bearing detail is in `PhixitEngine.applySpecToList(...)`:

> When the target flag name is **not already present** in a partition, the engine **APPENDS a brand-new
> flag entry**. On revert it **REMOVES** entries.

So an apply/revert can change a partition's **real flag count** — and that is exactly what Heterodyne
later validates.

## 2. Root cause

Phenotype is a client-pull config system. The server delivers a config "snapshot" per config-package; GMS
stores it as the compressed `param_partitions.flags_content` blobs and **commits** it into a served
configuration. Alongside the blobs, GMS keeps committed-config **metadata** — including how many flags the
committed configuration contains (and/or a digest derived from it).

`HeterodyneSyncTaskChimeraService` runs periodically. On each sync it **reconstructs** the package's flag
set from the on-disk `param_partitions` blobs and **cross-checks it against that persisted expected count**.
The check is the `gjww`/`eztp` frames in the stack: reconstructed `M` vs. expected `N`. If they disagree it
throws `IllegalArgumentException: Encountered conflicting flags. Expected flag count N, but was M.`

When this app **adds** a flag that the server-delivered config never shipped, the partition now decodes to
**one more** entry than before — while the committed-config metadata GMS recorded at commit time still says
the old count. The next sync reconstructs the larger set, compares it to the stale expected count, and
throws. (Symmetrically, a **revert** drops flags and the reconstructed set is *smaller* than expected — the
"expected 453, was 440" direction in the report.) The 13-flag gap is consistent with a batch of tweaks that
each changed a partition's entry count.

Why "every now and then" / silent / background: the check only runs on Heterodyne's own schedule (and on
server re-sync events, which the project's `reboot-to-apply-analysis.md` already notes happen roughly daily),
not at edit time — so the crash is decoupled from the user action that caused it and surfaces later in GMS's
persistent process with no UI.

### What is *not* the cause
- It is **not** the value edits. Changing an existing flag's value in place keeps the count identical, so a
  value-only tweak never trips this check.
- It is **not** `serving_version` alone. Bumping `last_fetch.serving_version` makes GMS treat the snapshot as
  fresh, but it does not reconcile the **count** metadata Heterodyne validates against; a fresh serving
  version over a count-drifted snapshot still fails the count check.

## 3. The fix (implemented)

After a **count-changing** edit, **drop the stale Heterodyne committed-config bookkeeping for the affected
package(s)** so GMS rebuilds a self-consistent expected count from the configuration that is *now on disk*
(our edit) on its next sync — instead of validating our count-drifted snapshot against a stale expected
count. This is the DB-side analogue of the phenotype **file**-cache clear the engine already performs
(`deleteRecursive(PHENO_CACHE_DIR)`); that clear flushes the consumer cache but does **not** touch the
committed-config count metadata that Heterodyne keys on, which is why the crash persisted.

We deliberately did **not** try to "fix up the expected count to match" (write a number into it): the count
lives in closed-source committed-config bookkeeping whose exact representation (a raw count column, a
serialized blob, a digest, or several of these) is not stable across GMS versions, so writing a number into
it is more likely to *manufacture* a different inconsistency than to remove one. Deleting the stale
bookkeeping and letting GMS rebuild it is self-healing and version-tolerant.

### Non-destructive: preserve the just-applied override (issue #25, risk #1)

The original candidate (`claude/fix-flag-count-6PnPT`) also **forced a server re-fetch** (zeroing
`last_fetch.last_update_time`). That is unsafe here: the `param_partitions.flags_content` blob *is* the
committed serving set, so forcing a fresh server fetch+commit overwrites the override we just applied —
exactly the same mechanism that resets flags after ~24h (`reboot-to-apply-analysis.md`), and precisely the
count-changing "pre-activate" tweaks this path targets.

So the implemented `HeterodyneSyncState.clearSyncSql(...)` is **purely committed-config `DELETE`s**: it never
touches `param_partitions` and never zeroes a fetch timestamp. GMS rebuilds the expected count from the
on-disk (edited) configuration, keeping the override intact. The coherent re-commit is driven by what the
engine already does after the write — bump `last_fetch.serving_version`, clear the phenotype file cache, and
force-stop GMS — not by a forced network pull. (Should a particular GMS build still choose to re-fetch a
fresh server config on its own, the app's existing `ReapplyJobService` re-establishes the tweak on its next
pass, as it already does against the ~24h reset.)

### Gate on the write, not the global status (issue #25, risk #3)

`applySpecs` collects every package's partitions into a single `toWrite` and persists them in one
`RootDb.writePartitions` call. The clear is now gated on **that write succeeding** (`writeOk`), not on the
global `ok` flag. The global `ok` also goes false when *another* package fails to read/decode; gating the
clear on it would skip healing a package whose count-changing blob *did* commit — leaving exactly the crash
condition. Gating on the write outcome heals every successfully-written count-changed package regardless of
unrelated per-package failures.

### Where the logic lives (unit-testable, in the coverage gate)

New pure class **`HeterodyneSyncState`** (no Android imports; 100% line+branch covered):

- `countChanged(before, after)` / `countChanged(int, int)` — did the edit change a partition's flag count
  (flag added/removed), i.e. could it trip Heterodyne? A value-only edit returns `false`.
- `clearSyncSql(packages)` — the ordered, de-duplicated committed-config `DELETE`s that invalidate the stale
  expected-count bookkeeping for the affected packages. Per package it deletes from `committed_configurations`
  keyed two ways for cross-version tolerance — via the **verified** `static_config_packages` linkage this app
  already uses to locate a package's `param_partitions`, and via an alternate `config_packages` linkage —
  plus the legacy flat `Configurations` table keyed by package name.

`PhixitEngine.applySpecs(...)` tracks, per partition, whether the flag **count** changed
(`HeterodyneSyncState.countChanged(countBefore, flags.size())`). If any did and the write succeeded, it runs
`HeterodyneSyncState.clearSyncSql(...)` through `RootDb.exec(PHENO_DB, ...)` — best-effort: a failure is
logged and never fails the apply (the flag edit already landed), and it is skipped entirely when the write
failed or when nothing count-changed.

### Why the clear SQL is safe across GMS versions

The exact name/linkage of Heterodyne's committed-config table varies between GMS builds and some of the
targeted tables may not exist on a given device. `RootDb.exec` runs through the root service's **lenient**
executor (`PhixitRootService.execStatements`, mirroring `sqlite3 -batch` with no `.bail`): a statement
against a missing table is logged and skipped, the rest of the batch still runs. The statements are
`DELETE`s that are harmless no-ops when the row/table is absent, so on a build missing a table we simply
clear the ones present — we never corrupt the DB and never throw. Package names are emitted as quote-doubled
SQL string literals; Phenotype config-package names are reverse-DNS identifiers, so this is belt-and-braces.

## 4. Residual risk (be honest)

- **Closed-source target.** The committed-config table name (`committed_configurations`) and its linkage are
  inferred from Phenotype's observable behaviour and prior art, not from documentation. The keying via
  `static_config_packages` matches the linkage this app already uses successfully for `param_partitions`, so
  it is the most likely match — but on a GMS build where the table is named differently, the lenient executor
  turns the clear into a no-op and the original failure mode could recur. The change is *strictly safer than
  before* (it adds best-effort, non-destructive invalidation and never makes things worse), but it is a
  mitigation, not a guaranteed cure on every GMS version. **Device check:** dump the real schema
  (`SELECT name FROM sqlite_master WHERE type='table'`) and confirm the committed-config table name/linkage;
  align `clearSyncSql` if it differs.
- **Timing.** Invalidation makes the **next** Heterodyne sync rebuild a coherent count; there is a small
  window between our edit and that sync. The engine already force-stops GMS after the edit, which tends to
  trigger a fresh commit promptly, narrowing the window.
- **Value-only edits are intentionally untouched.** They cannot change the count, so they cannot trip this
  check; not clearing sync state for them avoids unnecessary churn.

## 5. Verification

- `./gradlew assembleDebug` — passes.
- `./gradlew testDebugUnitTest` — passes (new `HeterodyneSyncStateTest`; extended `PhixitEngineTest` covering
  add-clears, remove-clears, value-only-skips, clear-throws-swallowed, write-fails-skips-clear, and the
  gating-bug regression where one package's write succeeds while another fails to decode).
- `./gradlew jacocoTestCoverageVerification` — 100% line+branch maintained (`HeterodyneSyncState` and the new
  `PhixitEngine` branches are in scope and fully covered).
- **Still needs on-device confirmation:** that the Heterodyne crash no longer reproduces, the committed-config
  table name/linkage matches, and a count-changing tweak still persists after the clear.
