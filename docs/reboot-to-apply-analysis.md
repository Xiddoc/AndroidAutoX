# Is the "Reboot to Apply" step actually necessary? — Independent peer review

**Status:** research note / design rationale. Not a spec.
**Question studied:** Does applying or reverting an AndroidAutoX tweak *genuinely require a full
device reboot* for the changed GMS Phenotype flags / Gearhead settings to take effect on Android —
or would a narrower action suffice?
**Method:** five independent reviewers, each reasoning **from Android-platform knowledge + their own
web research, deliberately NOT from this repo's source code**, each assigned a different investigative
lens. This was an experiment to see whether independent analysis converges or diverges, and to capture
the result so we never have to re-research it.

> Scope note: this document is about the **Android platform mechanism**. For what *this app actually
> does* (force-stops `com.google.android.gms`, clears the phenotype cache, shows the reboot button —
> overwhelmingly on *reverts*), see `PhixitEngine.java`, `RebootDialog.java`, and `MainActivity`'s
> `showRebootButton()` call sites. The two are compared in "Apply vs. revert" below.

---

## TL;DR — the consensus

**A full system reboot is NOT platform-mandated for most Phenotype flag changes.** All five reviewers
independently concluded the reboot is a *sufficient, conservative, "always-works" blunt instrument* —
not a strict requirement. None found that Android intrinsically gates Phenotype flag delivery on a
kernel boot.

Phenotype is a **client-pull, commit/snapshot, per-process-cached** configuration system. The values
reach a feature only after: (1) the override is **committed** into GMS's served snapshot, and (2) every
**consuming process** restarts and re-reads that snapshot into a fresh in-memory cache. A reboot
"works" only because it incidentally does both for *every* process at once.

**The genuinely required events (the minimal sufficient action):**
1. Write the override as **`committed = 1`** (or trigger a commit) — a `committed = 0` row is *pending*
   and does nothing until a commit/sync pass folds it into the snapshot.
2. **Invalidate GMS's phenotype cache** (`rm -rf /data/data/com.google.android.gms/files/phenotype`).
3. **Force-stop the producer:** `am force-stop com.google.android.gms`.
4. **Restart the consumer(s):** force-stop `com.google.android.projection.gearhead`, **and for
   projected-display flags start a fresh projection session** (disconnect/reconnect the head unit).

**Where a reboot is genuinely (or defensibly) unavoidable** — the reviewers' honest concessions:
- Flags read inside **`system_server`** or a stubborn **persistent GMS** process, which a user cannot
  cleanly force-stop (killing `system_server` triggers a *soft reboot* anyway).
- Cases where **Gearhead caches its snapshot for its whole process lifetime** and/or current GMS builds
  don't reliably re-commit on force-stop (version-dependent; tools hedge with "reboot if it didn't
  take").
- As a deliberate **UX choice**: one instruction that works for every heterogeneous tweak beats a
  fast-but-flaky sequence that generates "didn't work" reports.

So the honest collective verdict is **"NECESSARY ONLY SOMETIMES"** — reboot is a legitimate *fallback*
and a real requirement for a *minority* of flags, but calling it universally *necessary* overstates it,
and calling it pure cargo-culting is unfair.

---

## The five verdicts at a glance

| # | Lens | Apply | Revert | Confidence | One-line takeaway |
|---|------|-------|--------|-----------|-------------------|
| 1 | Phenotype internals | NOT necessary (rarely) | NOT necessary | Med-high | Phenotype is a running-service pull/commit cache; reboot is a proxy for restarting otherwise-unrestartable system processes. |
| 2 | Process & IPC model | Sometimes | Sometimes | Medium | Minimal = commit + cache-clear + force-stop GMS **and** Gearhead. Reboot truly needed only for `system_server`-resident flags. |
| 3 | Gearhead projection lifecycle | Sometimes (mostly not) | Sometimes (mostly not) | Medium | Load-bearing step is a **fresh projection session** (reconnect head unit), not the reboot. |
| 4 | Display & system-service stack | NOT necessary | NOT necessary | Med-high | The projected display is **session-scoped, not boot-latched**; reconnect re-negotiates geometry. |
| 5 | Skeptic / minimalist | Sometimes | Sometimes | Medium | Blanket reboot is cargo-culting but a defensible fallback; genuinely needed for the Gearhead session + a few init-cached flags. |

**Net:** 2 reviewers say "not necessary," 3 say "necessary only sometimes." **Zero** say a full reboot
is universally required. No reviewer found an apply-vs-revert asymmetry at the platform level.

---

## Where the reviewers AGREED (strong consensus)

1. **Not boot-gated.** Phenotype is delivered by a *running service* (GMS) via a client pull, cached
   per-process. Nothing in the chain depends on the kernel boot sequence.
2. **The real propagation chain** is: edit `phenotype.db` → **commit** into the served snapshot → GMS
   regenerates its cached `.pb` → **consumer process re-reads on (re)start**.
3. **`committed = 0` is pending, not "applies on next boot."** R1, R2 and R5 independently flagged that
   writing uncommitted rows and relying on a later sync/reboot to flip them live is a *workaround for
   not committing properly*, not a platform requirement — and is the most plausible reason a reboot
   ever appears "needed."
4. **Reboot is sufficient-but-not-minimal.** It is the laziest way to guarantee *every* long-lived
   consumer (GMS persistent, `system_server`, the projection host) restarts at once. Comparable
   community tools (GMS-Flags, GAppsMod, AA-Tweaker) all frame it as **"force-stop / reopen a few
   times; reboot only if it didn't take."**
5. **No apply/revert asymmetry** in the platform mechanism — reverting is the same pipeline run in the
   opposite direction.

## Where the reviewers DISAGREED (the interesting part)

- **Do display/orientation flags require `system_server` (and therefore effectively a reboot)?**
  - **R2** conceded that *if* a flag is read inside `system_server`, you can't force-stop it without a
    soft reboot — so those flags make reboot genuinely necessary.
  - **R4 directly disputes this for the display flags.** Its key finding: the Android Auto projected
    surface is **not** a `system_server`-latched physical display whose density is computed once at
    boot. It is a **per-session `VirtualDisplay`** created by the Gearhead app, whose geometry
    (resolution/DPI/breakpoint dp) is **re-negotiated by the AAP protocol every time the head unit
    connects**. So disconnect+reconnect re-evaluates all geometry — *no reboot, no `system_server`
    restart required*. This contradicts the intuitive "layout flags are latched by the window manager
    at boot" story.
  - **Unresolved:** which specific flags (if any) are actually read by `system_server`/persistent GMS
    vs. the Gearhead app process. This single fact decides "sometimes" vs. "never." (See open questions.)

- **What is the load-bearing minimal step?** Reviewers weighted it differently by lens:
  - R1 → the **commit** (`committed = 1`) step.
  - R2 → **force-stop granularity** (force-stop the *right* packages; `kill`/`am crash` don't suffice
    because GMS respawns with stale state).
  - R3 / R4 → the **fresh projection session** (reconnect the head unit) for projected-display flags.

- **How big is the "sometimes"?** R1 and R4 lean "rarely / basically never for these flags"; R3 and R5
  give more weight to version-dependent GMS commit behavior and Gearhead snapshot caching making reboot
  the only *reliable* user-accessible action in a non-trivial minority of cases.

---

## The minimal-action ladder (synthesized)

From cheapest to most reliable. Stop at the first rung that makes the change take effect.

1. **Phone-UI-only flags:** clear phenotype cache → force-stop `com.google.android.gms` → force-stop /
   reopen `com.google.android.projection.gearhead`.
2. **Projected-display flags (CoolWalk, widescreen/portrait breakpoints, density, video bitrate,
   layout):** do (1), then **disconnect and reconnect the head unit** to force a cold projection session
   that re-reads the snapshot and re-runs the AAP video-config negotiation. *The reconnect, not the
   reboot, is the load-bearing step here* (R3, R4).
3. **Flags read by `system_server` / persistent GMS, or stubborn/version-dependent GMS builds:** a
   **full reboot** is the only reliable user-accessible way to restart those consumers (killing
   `system_server` is a soft reboot anyway). This is the legitimate kernel of truth in "you need a
   reboot."

**Correctness prerequisite for all rungs:** the override must be **committed** (rung 0). A `committed =
0` row will not apply via force-stop alone — it waits for GMS's commit/sync cycle.

---

## Apply vs. revert — and how this squares with what the app does

The reviewers found **no platform-level reason** reverts need a reboot more than applies. Yet in this
repo, `showRebootButton()` fires on almost every **revert** but only a handful of **applies**. Two
non-contradictory explanations, consistent with the consensus mechanism:

- On **apply**, Android Auto is usually *not running*, so the next cold connection naturally re-reads
  the new committed snapshot — no reboot prompt needed.
- On **revert**, a consumer may have *already committed the tweaked value into its own persisted
  snapshot* during a prior session; restoring the DB + bouncing GMS doesn't guarantee that consumer
  re-reads the baseline until it cold-starts — so the conservative reboot is offered.

This is a *UX/robustness* choice, not a platform mandate — exactly matching reviewer R5's "defensible
fallback" framing. It also raises the **`committed = 0` question** (below): if the app writes pending
overrides and leans on reboot/sync to commit them, that — not a platform requirement — could be why a
reboot ends up "needed."

---

## Open questions / empirical tests that would settle this

These are the experiments the reviewers said would convert "sometimes" into a definitive answer. Worth
running on a real rooted device + head unit (or DHU):

1. **Force-stop-only test:** apply a visible tweak (e.g. CoolWalk/widescreen), then do
   cache-clear + force-stop GMS + force-stop Gearhead + **reconnect — with NO reboot**. Does it take?
   This is the single cleanest test of the whole question.
2. **`committed` test:** write `committed = 0`, force-stop, observe (expected: no effect); vs
   `committed = 1`, force-stop (expected: effect). Confirms whether our pending-row writes need an
   explicit commit. Check whether this app ever issues a commit, or relies on the 24h GMS self-sync.
3. **Per-flag consumer map:** for each tweak, identify the *reading process* (Gearhead app vs.
   persistent GMS vs. `system_server`) via `dumpsys`. This decides "sometimes" vs. "never" and which
   flags legitimately need rung 3.
4. **Reconnect-only for projected flags:** toggle a breakpoint flag, force-stop, reconnect the head
   unit *without* killing the phone — does layout change? Directly tests R4's "session-scoped, not
   boot-latched" claim.
5. **Wired vs. wireless:** wireless adapters (AAWireless/MA1) may keep a session/process warm longer,
   so "reconnect" may be less reliable than a USB unplug. Test both.

A recurring caveat (R3, R1): GMS periodically **re-syncs flags from Google's servers (~24h)** and can
overwrite local overrides. That stickiness problem is *orthogonal to the reboot question* and a reboot
does not fix it — don't conflate the two.

---

## Appendix A — the five reviews in full

### Reviewer 1 — Phenotype internals
**Verdict:** Apply NOT necessary (occasionally, for flags consumed deep in long-lived processes);
Revert NOT necessary. **Confidence: medium-high.**
Phenotype is GMS's client-pull configuration delivery, not a push/boot facility. Each config package's
serving state lives in `phenotype.db` (modern schema: compressed `flags_content` blobs in
`param_partitions`; older schema: `Flags`/`FlagOverrides`). A client calls
`getConfigurationSnapshot`/`commitToConfiguration` (modern: `getConfigurationSnapshotWithToken` +
`commitToConfiguration(token)` promotes the snapshot to the **committed serving set**); microG mirrors
this with `getCommitedConfiguration`. **`committed = 1`** = live serving set; **`committed = 0`** =
pending override not yet promoted, which is exactly why writing `committed = 0` rows doesn't take effect
until a commit/sync. GMS writes the committed config into a per-client store (the phenotype `.pb` cache
and/or the client's SharedPreferences-backed `PhenotypeFlag` store), read into process memory at process
start. Nothing in that chain is gated on a kernel boot. "Smoking gun": GMS-Phixit reports flags reset
after 24h — a server re-sync/re-commit event, orthogonal to reboots, confirming a running-service pull
loop delivers flags. **Legitimate kernel of truth:** for flags read inside `system_server` or persistent
GMS, the only *user-accessible* way to restart those readers is a reboot (a `system_server` kill is a
soft/Zygote restart, ≈ reboot). So reboot is *sufficient and convenient*, not *necessary*, except as a
practical proxy for restarting otherwise-unrestartable processes.

### Reviewer 2 — Process & IPC model
**Verdict:** Apply NECESSARY ONLY SOMETIMES; Revert NECESSARY ONLY SOMETIMES (symmetric).
**Confidence: medium.**
State lives in three tiers, each with its own lifetime: (1) on-disk `phenotype.db` — necessary but no
consumer reads it live; (2) per-package **committed snapshot** exposed via `IPhenotypeService`
(`getConfigurationSnapshot`/`commitForUser`), materialized as the consumer-side `.pb` (e.g.
`/data/data/com.google.android.projection.gearhead/files/phenotype/.../*.pb`) — a `committed = 0` row is
staged and changes nothing until committed; (3) **in-process RAM cache** — each consumer reads flags once
via `PhenotypeFlag`/`ProcessStablePhenotypeFlag` and caches for the process lifetime (process-stable
flags are deliberately frozen to a SharedPreferences snapshot). The chain to flush is DB → snapshot
recommit → consumer re-read. `am force-stop com.google.android.gms` tears down the whole UID's process
group (incl. `:persistent`, `:unstable`) and clears stopped-stickiness — the correct tool; a bare
`kill`/`am crash` of one PID just respawns with stale state; re-broadcasting can't force a consumer's RAM
re-read unless it's coded to listen. **Reboot genuinely required only when a flag is read by
`system_server`** (cannot be force-stopped → killing it is a soft reboot, *less* clean than a real one)
or a stubborn persistent/process-stable case. Minimal sufficient action: write `committed = 1` → delete
consumer's stale `.pb` + GMS cache → force-stop GMS → force-stop Gearhead.

### Reviewer 3 — Gearhead projection lifecycle
**Verdict:** Apply & Revert NECESSARY ONLY SOMETIMES — mostly NOT strictly necessary.
**Confidence: medium.**
Two stages: **GMS side (commit)** — editing raw DB blobs isn't enough; values must be committed into the
served snapshot and GMS's in-memory state + phenotype cache invalidated (hence "edit DB → clear cache →
force-stop GMS"). **Consumer side (Gearhead)** — Gearhead is itself a Phenotype consumer that fetches a
snapshot and caches flags in its own store, read at **projection-session cold start** (and process
start), not continuously mid-session. **Phone-UI flags** take effect when Gearhead's process re-reads
them (reopen app); **projected-display flags** (CoolWalk, layout, video, app allow-listing) additionally
need a **new projection session** (physical disconnect/reconnect) because Gearhead binds projected config
at session start. XDA CoolWalk reports match: "restart phone, connect to AA, enable CoolWalk on head
unit" — the *connect* (new session) is load-bearing, the *restart phone* is the convenient guarantee.
Important caveat: reports of flags reverting on disconnect/ignition-off reflect GMS **server re-sync**
overwriting local edits — orthogonal to reboot, and a reboot doesn't fix it. Honest uncertainty: whether
Gearhead re-fetches purely on reconnect or caches the snapshot for its process lifetime (if the latter, a
Gearhead force-stop is mandatory when skipping reboot). Couldn't find a clean first-hand "no reboot,
reconnect only, *phenotype* flag changed" report (most "reconnect-only" reports are about in-app
Developer Settings, not phenotype).

### Reviewer 4 — Display & system-service stack
**Verdict:** Apply & Revert NOT necessary (for the display/geometry category, and almost certainly any
of these flags). **Confidence: medium-high.**
Key finding that **disputes the intuitive "window manager latches layout at boot" story**: the AA
projected surface is **not** a `system_server`-owned physical display whose density is computed once at
boot. It is a **per-session `VirtualDisplay`** created by the Gearhead app; its width/height/density are
computed at **session creation** from the **AAP-negotiated VideoConfiguration**, which the head unit
sends **every connection**. So disconnect+reconnect destroys and recreates the display and re-evaluates
every geometry input — including breakpoint dp thresholds, which are pure layout-logic constants read by
the renderer in the *gearhead app process*, not `system_server`. Therefore "config computed at display/
session creation" is satisfied by a **new session, not a new boot**, and the classic "system_server
latches this → only a reboot clears it" trap does **not** apply. Phenotype flags are snapshot-read at
process start, so force-stop of the consumer is the real requirement. The SELinux permissive transition
is transient and orthogonal (needed to *write* the DB, not to make changes take effect). Reboot is a
legitimate conservative fallback (subsumes any case where force-stop missed a process), not a platform
requirement. Verified-empirically suggestion: toggle a breakpoint flag, force-stop, reconnect *without*
reboot, observe layout.

### Reviewer 5 — Skeptic / minimalist
**Verdict:** Apply & Revert NECESSARY ONLY SOMETIMES; a blanket unconditional reboot is cargo-culting
but a defensible fallback. **Confidence: medium.**
Steelman that reboot is overkill: flags are *pulled, not pushed* — they take effect when the *consumer*
re-reads the snapshot, which happens on process restart, not boot. Comparable tools avoid mandatory
reboots: **GAppsMod** ("force close and reopen the Google apps a few times"; reboot only "you may also
need to"), **GMS-Flags** ("1 to 3 force-stops… if still not applied, reset app data or wait 24h" — no
reboot in the primary path; *notably the same author/engine lineage — `polodarb` GMS-Flags → GMS-Phixit
— as this app's `PhixitRootService`/`PhixitEngine`*), **Google Messages phenotype scripts** ("force-close
a few times"). The `committed = 0` detail supports the lighter path: it's a *pending* override the design
expects a commit/sync to pick up, reachable via `am broadcast -a
com.google.android.gms.phenotype.FLAG_OVERRIDE`. Where reboot is genuinely unavoidable: (1) the
**Gearhead/AA projected session** — not an ordinary app you can just reopen; XDA AA AIO Tweaker guidance
is "force stop and re-open Android Auto, **or** reboot if CoolWalk/taskbar still showing"; (2) flags read
once at init by long-lived/cached processes a plain force-stop won't respawn; (3) a "works for every
heterogeneous flag" guarantee — one action that always works, a defensible UX choice over a fast-but-
flaky sequence.

---

## Appendix B — consolidated sources

Independently surfaced by multiple reviewers (reliability notes are the reviewers').

- **microG GmsCore — Phenotype reimplementation** — `PhenotypeService.kt`, issue #1691, PR #3025.
  *High reliability for API semantics:* snapshot vs. committed config
  (`getConfigurationSnapshot`/`commitToConfiguration`/`getCommitedConfiguration`/`syncAfterOperation`),
  and the core model "apps call `getConfigurationSnapshot` and update their own local SQLite DB with
  flags from GMS." Clean-room reimplementation (shape/intent, some stubs).
  - https://github.com/microg/GmsCore/issues/1691
  - https://github.com/microg/GmsCore/blob/master/play-services-core/src/main/kotlin/org/microg/gms/phenotype/PhenotypeService.kt
  - https://github.com/microg/GmsCore/pull/3025
- **polodarb / GMS-Flags & GMS-Phixit** — *High; most analogous (same engine lineage as this app).*
  Primary path is "1–3 force-stops; if not, reset app data or wait 24h" (no mandatory reboot);
  GMS-Phixit "flags reset after 24h" confirms the server-sync/re-commit pull model and the modern schema.
  - https://github.com/polodarb/GMS-Flags
  - https://github.com/polodarb/GMS-Phixit
- **jacopotediosi / GAppsMod** — *Medium-high.* "Force close and reopen the Google apps a few times…
  you may also need to reboot" — frames apply as consumer restart, reboot as fallback.
  - https://github.com/jacopotediosi/GAppsMod
- **shmykelsa / AA-Tweaker** + XDA "Root only AA AIO Tweaker" thread — *Medium (AA-specific, community).*
  Confirms the gearhead `.pb` snapshot path; guidance is "force stop and re-open Android Auto; reboot
  only if CoolWalk/taskbar still shows" — reboot as fallback for flags cached in long-lived processes.
  - https://github.com/shmykelsa/AA-Tweaker
  - https://xdaforums.com/t/root-only-aa-aio-tweaker-the-ultimate-android-auto-utility.4194239/
- **ohmybahgosh / Google-Messages-Phenotype-Flag-Enabler** — *Medium-high.* Real script using
  `FlagOverrides … committed=0`; user step is force-close, no reboot. Concrete confirmation of the
  `committed=0` + force-stop pattern, app-agnostic.
  - https://github.com/ohmybahgosh/Google-Messages-Phenotype-Flag-Enabler
- **Android Auto Head Unit Integration Guide (mirror)** + DHU docs — *High for AAP mechanics.*
  Display resolution/DPI/density are negotiated **per-session at connection**, head-unit-driven —
  the load-bearing source for R4's "session-scoped, not boot-latched" verdict.
  - https://milek7.pl/.stuff/galdocs/huig13_cache.html
  - https://developer.android.com/training/cars/testing/dhu
- **XDA Google Cast / CoolWalk thread** — *Medium (community).* Real-world "restart phone, connect to
  AA, enable on head unit," and flag reversion on disconnect/ignition-off.
  - https://xdaforums.com/t/google-cast-coolwalk.4396533/
- **AOSP zygote / process-init** — *High (indirect).* Supports the "some configs read once at init"
  caveat. https://source.android.com/docs/core/runtime/zygote
- **thespandroid blog on GAppsMod/Phenotype** — *Low-medium (secondary).* Corroborates "apps call
  `getConfigurationSnapshot` and cache flags in their own local SQLite DB."
  - https://thespandroid.blogspot.com/2023/09/what-is-GappsMod-app-and-how-to-use-it.html

---

*Generated from a 5-way independent review. Reviewers reasoned from Android-platform knowledge and web
research, not from this repository's source, by design. Treat the empirical tests in "Open questions" as
the way to convert the remaining "sometimes" into a definitive, device-verified answer.*
