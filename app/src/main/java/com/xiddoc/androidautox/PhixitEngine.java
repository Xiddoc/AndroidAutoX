package com.xiddoc.androidautox;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI-free Phenotype "phixit" engine: applies/reverts {@link FlagSpec} overrides by
 * editing the compressed {@code flags_content} partition blobs in {@code phenotype.db}.
 *
 * <p>Extracted from {@code MainActivity} so it can run headlessly (e.g. from the
 * background re-apply job). State (the per-flag baselines and tweak toggles) is kept
 * in the same {@link SharedPreferences} file the activity uses, so both paths share it.
 */
public class PhixitEngine {

    /** Activity.getPreferences() stores under the activity's local class name. */
    public static final String PREFS = "MainActivity";

    public static final String PHENO_DB =
            "/data/data/com.google.android.gms/databases/phenotype.db";

    private final Context ctx;
    private final StringBuilder log;

    public PhixitEngine(Context ctx, StringBuilder log) {
        this.ctx = ctx.getApplicationContext();
        this.log = log != null ? log : new StringBuilder();
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Applies every spec across all of its package's partitions. */
    public boolean applySpecs(List<FlagSpec> specs, boolean captureBaseline) {
        StringBuilder sb = new StringBuilder();

        // Capture the db's owner + SELinux mode, force-stop GMS, and drop to permissive
        // (only if it was enforcing) so the root service can open the private db. All of
        // this is restored at the end.
        String owner = MainActivity.runSuWithCmd("stat -c %U:%G " + PHENO_DB).getInputStreamLog().trim();
        String policy = MainActivity.runSuWithCmd("getenforce").getInputStreamLog().trim();
        boolean wasEnforcing = policy.equalsIgnoreCase("Enforcing");
        if (wasEnforcing) MainActivity.runSuWithCmd("setenforce 0");
        MainActivity.runSuWithCmd("am force-stop com.google.android.gms");

        LinkedHashMap<String, List<FlagSpec>> byPkg = groupByPkg(specs);

        List<Partition> toWrite = new ArrayList<Partition>();
        boolean ok = true;

        for (Map.Entry<String, List<FlagSpec>> e : byPkg.entrySet()) {
            String pkg = e.getKey();
            List<FlagSpec> ps = e.getValue();

            List<Partition> raw;
            try {
                raw = RootDb.readPartitions(pkg);
            } catch (Exception ex) {
                sb.append("  [").append(pkg).append("] read ERR: ").append(ex).append("\n");
                ok = false;
                continue;
            }
            if (raw.isEmpty()) {
                sb.append("  [").append(pkg).append("] no partitions matched\n");
                ok = false;
                continue;
            }

            List<Long> ids = new ArrayList<Long>();
            List<List<PhixitSnapshot.Flag>> parts = new ArrayList<List<PhixitSnapshot.Flag>>();
            for (Partition p : raw) {
                if (p.blob == null || p.blob.length == 0) continue;
                try {
                    ids.add(p.id);
                    parts.add(PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(p.blob)));
                } catch (Exception ex) {
                    sb.append("  [").append(pkg).append("] partition ").append(p.id)
                            .append(" decode EXCEPTION ").append(ex).append("\n");
                    ok = false;
                }
            }

            if (captureBaseline) {
                for (FlagSpec s : ps) captureBaselineIfAbsent(pkg, s.name, parts);
            }

            for (int i = 0; i < ids.size(); i++) {
                List<PhixitSnapshot.Flag> flags = parts.get(i);
                for (FlagSpec s : ps) applySpecToList(flags, s);
                byte[] blob = PhixitSnapshot.deflateRaw(PhixitSnapshot.encode(flags));
                toWrite.add(new Partition(ids.get(i), blob));
            }
            sb.append("  [").append(pkg).append("] ").append(ids.size()).append(" partitions updated\n");
        }

        if (toWrite.isEmpty()) {
            ok = false;
        } else {
            int servingVersion = (int) (System.currentTimeMillis() / 1000L);
            try {
                RootDb.writePartitions(toWrite, servingVersion);
            } catch (Exception ex) {
                sb.append("  apply ERR: ").append(ex).append("\n");
                ok = false;
            }
        }

        // Restore ownership/SELinux context on the db (root edits keep the owner, but be
        // safe), then make GMS re-read the edited snapshot from the DB and restart fresh.
        if (owner.contains(":")) MainActivity.runSuWithCmd("chown " + owner + " " + PHENO_DB);
        MainActivity.runSuWithCmd("restorecon " + PHENO_DB);
        MainActivity.runSuWithCmd("rm -rf /data/data/com.google.android.gms/files/phenotype");
        MainActivity.runSuWithCmd("am force-stop com.google.android.gms");
        if (wasEnforcing) MainActivity.runSuWithCmd("setenforce 1");

        log.append(sb);
        return ok;
    }

    private static LinkedHashMap<String, List<FlagSpec>> groupByPkg(List<FlagSpec> specs) {
        LinkedHashMap<String, List<FlagSpec>> byPkg = new LinkedHashMap<String, List<FlagSpec>>();
        for (FlagSpec s : specs) {
            List<FlagSpec> l = byPkg.get(s.pkg);
            if (l == null) { l = new ArrayList<FlagSpec>(); byPkg.put(s.pkg, l); }
            l.add(s);
        }
        return byPkg;
    }

    /** Restores each spec's flag to the baseline captured at apply time. */
    public boolean revertSpecs(List<FlagSpec> applied) {
        SharedPreferences sp = prefs();
        List<FlagSpec> restore = new ArrayList<FlagSpec>();
        for (FlagSpec s : applied) {
            String b = sp.getString(baselineKey(s.pkg, s.name), null);
            if (b == null || b.equals("A")) {
                restore.add(FlagSpec.remove(s.pkg, s.name)); // was absent -> drop it
            } else {
                restore.add(deserializeBaseline(s.pkg, s.name, b));
            }
        }
        boolean ok = applySpecs(restore, false);
        SharedPreferences.Editor ed = sp.edit();
        for (FlagSpec s : applied) ed.remove(baselineKey(s.pkg, s.name));
        ed.apply();
        return ok;
    }

    /** True if a baseline was captured for any of the tweak's flags. */
    public boolean hasBaseline(String tweakKey) {
        List<FlagSpec> specs = PhixitTweaks.specs(tweakKey);
        if (specs == null) return false;
        SharedPreferences sp = prefs();
        for (FlagSpec s : specs) {
            if (sp.contains(baselineKey(s.pkg, s.name))) return true;
        }
        return false;
    }

    /**
     * Read-only check: returns true if every spec's flag already holds the desired
     * value in <em>all</em> of its package's partitions. Used by the re-apply job to
     * avoid restarting GMS when nothing has drifted. No GMS restart, no SELinux change.
     */
    public boolean isApplied(List<FlagSpec> specs) {
        // Lenient wrapper around the strict core: any structural unavailability
        // (no root, unbound RootDb, empty partitions, decode failure) that the strict
        // variant surfaces as an exception is swallowed here and reported as "not applied".
        // ReapplyJobService relies on this false-on-error behaviour to conservatively
        // trigger a re-apply when the DB can't be read.
        try {
            return isAppliedStrict(specs);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Strict variant of {@link #isApplied}: performs the same flag comparison but
     * RETHROWS on structural unavailability instead of swallowing errors.
     *
     * <p>Unlike {@code isApplied}, which catches every exception and returns {@code false}
     * (so a temporarily unreadable DB looks like "confirmed not applied"), this method
     * only returns {@code true}/{@code false} when it could actually read and compare the
     * partition data.  When the DB is structurally unavailable (no root, RootDb not bound,
     * empty partition list, decode failure) the exception propagates to the caller, which
     * should treat it as {@code null}/UNKNOWN rather than FALSE.
     *
     * <p><b>Use this method from {@link TweakAppliedChecker} so that a transiently
     * unreadable DB never drives a correctly-applied tweak toward "confirmed gone" (red).
     * Use {@link #isApplied} from {@link ReapplyJobService} — it already handles errors
     * conservatively by triggering a re-apply.</b>
     *
     * @param specs the flag specs to check; must not be null
     * @return {@code true} if every spec is confirmed applied in every partition,
     *         {@code false} if at least one spec is confirmed NOT applied
     * @throws Exception if the partitions could not be read or decoded (structural
     *                   unavailability — caller must treat as UNKNOWN, not FALSE)
     */
    public boolean isAppliedStrict(List<FlagSpec> specs) throws Exception {
        LinkedHashMap<String, List<FlagSpec>> byPkg = groupByPkg(specs);
        for (Map.Entry<String, List<FlagSpec>> e : byPkg.entrySet()) {
            // Let RootDb.readPartitions throw: no root / service not bound -> UNKNOWN.
            List<Partition> raw = RootDb.readPartitions(e.getKey());
            // Empty partition list: DB not readable structurally -> throw as UNKNOWN.
            if (raw.isEmpty()) {
                throw new Exception("No partitions returned for package: " + e.getKey());
            }
            boolean sawPartition = false;
            for (Partition p : raw) {
                if (p.blob == null || p.blob.length == 0) continue;
                // Let decode failure throw: corrupt/unreadable snapshot -> UNKNOWN.
                List<PhixitSnapshot.Flag> flags =
                        PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(p.blob));
                sawPartition = true;
                for (FlagSpec s : e.getValue()) {
                    PhixitSnapshot.Flag f = findFlag(flags, s.name);
                    if (s.remove) {
                        if (f != null) return false;
                    } else if (f == null || !valueEquals(f, s.flag)) {
                        return false;
                    }
                }
            }
            // No decodable partition seen -> DB not readable structurally -> UNKNOWN.
            if (!sawPartition) {
                throw new Exception("No decodable partitions for package: " + e.getKey());
            }
        }
        return true;
    }

    private static PhixitSnapshot.Flag findFlag(List<PhixitSnapshot.Flag> flags, String name) {
        for (PhixitSnapshot.Flag f : flags) {
            if (!f.numericName && name.equals(f.name)) return f;
        }
        return null;
    }

    private static boolean valueEquals(PhixitSnapshot.Flag a, PhixitSnapshot.Flag b) {
        if (a.type != b.type) return false;
        switch (b.type) {
            case PhixitSnapshot.TYPE_LONG:   return a.longValue == b.longValue;
            case PhixitSnapshot.TYPE_DOUBLE: return a.doubleBits == b.doubleBits;
            case PhixitSnapshot.TYPE_STRING: return a.stringValue != null && a.stringValue.equals(b.stringValue);
            case PhixitSnapshot.TYPE_BYTES:  return java.util.Arrays.equals(a.bytesValue, b.bytesValue);
            default:                         return true; // bool value is encoded in the type
        }
    }

    // --- Android Auto projection detection (used to defer GMS-restarting re-applies) ---

    private static final String MARK_SERVICE_RECORD  = "servicerecord";
    private static final String MARK_IS_FOREGROUND   = "isforeground=true";
    private static final String MARK_FG_SERVICE_TYPE = "foregroundservicetype";

    /**
     * Best-effort root check for "Android Auto is actively projecting right now", used to
     * avoid force-stopping GMS (which restarts Android Auto) in the middle of a drive.
     *
     * <p>Gearhead hosts a <em>foreground</em> service for the whole projection session, so we
     * dump its services and look for a foreground marker:
     * <pre>dumpsys activity services com.google.android.projection.gearhead</pre>
     * The foreground-service lifetime tracks the projection session closely — far better than
     * a RESUMED-activity check (misses screen-off-while-projecting) or a bare process check
     * (gearhead lingers cached after a drive). {@code dumpsys car_service} is absent on most
     * non-AAOS phones.
     *
     * <p><b>Conservative by design.</b> The ONLY path that returns {@code false} ("safe to
     * re-apply") is a dump that clearly shows gearhead has no running services. Everything
     * ambiguous — empty/unreadable output, or service records present without a recognized
     * foreground marker (dumpsys format varies across Android versions) — returns {@code true}
     * (defer). Rationale: a briefly un-applied tweak is far less bad than Android Auto
     * restarting mid-drive. Trade-off: on devices where gearhead keeps idle services alive,
     * this over-defers until they stop or projection ends. Note stderr is intentionally NOT
     * treated as fatal — {@code su}/{@code dumpsys} emit benign notices to stderr on success
     * (matching the lenient stdout-only parsing used elsewhere in this class).
     */
    public boolean isAndroidAutoProjecting() {
        StreamLogs r = MainActivity.runSuWithCmd("dumpsys activity services " + FlagSpec.PKG_GEARHEAD);
        String out = r.getInputStreamLog();
        if (out == null || out.trim().isEmpty()) {
            return true; // cannot read -> assume projecting (conservative)
        }
        String lower = out.toLowerCase();
        if (lower.contains(MARK_IS_FOREGROUND) || lower.contains(MARK_FG_SERVICE_TYPE)) {
            return true; // a live foreground service == active projection session
        }
        if (!lower.contains(MARK_SERVICE_RECORD)) {
            return false; // gearhead has no running services at all -> not projecting
        }
        // Services exist but no foreground marker matched. Rather than risk a false negative
        // (and a mid-drive restart) on an unfamiliar dumpsys format, defer.
        return true;
    }

    /** Reads a long-valued flag's current value from the snapshot, or {@code def}. */
    public long readLong(String pkg, String name, long def) {
        List<Partition> raw;
        try {
            raw = RootDb.readPartitions(pkg);
        } catch (Exception e) {
            return def;
        }
        for (Partition p : raw) {
            if (p.blob == null || p.blob.length == 0) continue;
            try {
                for (PhixitSnapshot.Flag f :
                        PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(p.blob))) {
                    if (!f.numericName && name.equals(f.name) && f.type == PhixitSnapshot.TYPE_LONG) {
                        return f.longValue;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private void applySpecToList(List<PhixitSnapshot.Flag> flags, FlagSpec s) {
        int idx = -1;
        for (int i = 0; i < flags.size(); i++) {
            PhixitSnapshot.Flag f = flags.get(i);
            if (!f.numericName && s.name.equals(f.name)) { idx = i; break; }
        }
        if (s.remove) {
            if (idx >= 0) flags.remove(idx);
            return;
        }
        if (idx >= 0) {
            copyValue(flags.get(idx), s.flag);
        } else {
            PhixitSnapshot.Flag add = new PhixitSnapshot.Flag();
            add.name = s.name;
            add.numericName = false;
            copyValue(add, s.flag);
            flags.add(add);
        }
    }

    private static void copyValue(PhixitSnapshot.Flag dst, PhixitSnapshot.Flag src) {
        dst.type = src.type;
        dst.longValue = src.longValue;
        dst.doubleBits = src.doubleBits;
        dst.stringValue = src.stringValue;
        dst.bytesValue = src.bytesValue;
    }

    private void captureBaselineIfAbsent(String pkg, String name, List<List<PhixitSnapshot.Flag>> parts) {
        SharedPreferences sp = prefs();
        String key = baselineKey(pkg, name);
        if (sp.contains(key)) return;
        PhixitSnapshot.Flag cur = null;
        for (List<PhixitSnapshot.Flag> p : parts) {
            for (PhixitSnapshot.Flag f : p) {
                if (!f.numericName && name.equals(f.name)) { cur = f; break; }
            }
            if (cur != null) break;
        }
        sp.edit().putString(key, serializeBaseline(cur)).apply();
    }

    static String baselineKey(String pkg, String name) {
        return "phixit_base|" + pkg + "|" + name;
    }

    private static String serializeBaseline(PhixitSnapshot.Flag f) {
        if (f == null) return "A";
        switch (f.type) {
            case PhixitSnapshot.TYPE_BOOL_FALSE: return "B0";
            case PhixitSnapshot.TYPE_BOOL_TRUE:  return "B1";
            case PhixitSnapshot.TYPE_LONG:       return "L" + f.longValue;
            case PhixitSnapshot.TYPE_DOUBLE:     return "D" + f.doubleBits;
            case PhixitSnapshot.TYPE_STRING:
                try {
                    return "S" + android.util.Base64.encodeToString(
                            f.stringValue.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
                } catch (Exception e) { return "S"; }
            case PhixitSnapshot.TYPE_BYTES:
                return "X" + PhixitSnapshot.bytesToHex(f.bytesValue);
            default: return "A";
        }
    }

    private static FlagSpec deserializeBaseline(String pkg, String name, String b) {
        char tag = b.charAt(0);
        String body = b.substring(1);
        switch (tag) {
            case 'B': return FlagSpec.bool(pkg, name, body.equals("1"));
            case 'L': return FlagSpec.lng(pkg, name, Long.parseLong(body));
            case 'D': return FlagSpec.dbl(pkg, name, Double.longBitsToDouble(Long.parseLong(body)));
            case 'S':
                try {
                    return FlagSpec.str(pkg, name,
                            new String(android.util.Base64.decode(body, android.util.Base64.NO_WRAP), "UTF-8"));
                } catch (Exception e) { return FlagSpec.str(pkg, name, ""); }
            case 'X': return FlagSpec.bytes(pkg, name, PhixitSnapshot.hexToBytes(body));
            default:  return FlagSpec.remove(pkg, name);
        }
    }
}
