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
        final String path = ctx.getApplicationInfo().dataDir;
        final String filesDir = ctx.getFilesDir().getAbsolutePath();
        StringBuilder sb = new StringBuilder();

        String policy = MainActivity.runSuWithCmd("getenforce").getInputStreamLog();
        MainActivity.runSuWithCmd("setenforce 0");
        MainActivity.runSuWithCmd("am force-stop com.google.android.gms");

        LinkedHashMap<String, List<FlagSpec>> byPkg = new LinkedHashMap<String, List<FlagSpec>>();
        for (FlagSpec s : specs) {
            List<FlagSpec> l = byPkg.get(s.pkg);
            if (l == null) { l = new ArrayList<FlagSpec>(); byPkg.put(s.pkg, l); }
            l.add(s);
        }

        StringBuilder script = new StringBuilder();
        boolean ok = true;

        for (Map.Entry<String, List<FlagSpec>> e : byPkg.entrySet()) {
            String pkg = e.getKey();
            List<FlagSpec> ps = e.getValue();

            StreamLogs r = MainActivity.runSuWithCmd(
                    path + "/sqlite3 -batch " + PHENO_DB + " " +
                            "'SELECT param_partition_id, hex(flags_content) FROM param_partitions " +
                            "WHERE static_config_package_id IN (SELECT static_config_package_id " +
                            "FROM static_config_packages WHERE name=\"" + pkg + "\");'");
            if (!r.getErrorStreamLog().isEmpty()) {
                sb.append("  [").append(pkg).append("] read ERR: ").append(r.getErrorStreamLog()).append("\n");
                ok = false;
            }
            String out = r.getInputStreamLog();
            if (out.isEmpty()) {
                sb.append("  [").append(pkg).append("] no partitions matched\n");
                ok = false;
                continue;
            }

            List<String> pids = new ArrayList<String>();
            List<List<PhixitSnapshot.Flag>> parts = new ArrayList<List<PhixitSnapshot.Flag>>();
            for (String line : out.split("\\r?\\n")) {
                int bar = line.indexOf('|');
                if (bar <= 0) continue;
                String pid = line.substring(0, bar).trim();
                String hex = line.substring(bar + 1).trim();
                if (hex.isEmpty()) continue;
                try {
                    pids.add(pid);
                    parts.add(PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(PhixitSnapshot.hexToBytes(hex))));
                } catch (Exception ex) {
                    sb.append("  [").append(pkg).append("] partition ").append(pid)
                            .append(" decode EXCEPTION ").append(ex).append("\n");
                    ok = false;
                }
            }

            if (captureBaseline) {
                for (FlagSpec s : ps) captureBaselineIfAbsent(pkg, s.name, parts);
            }

            for (int i = 0; i < pids.size(); i++) {
                List<PhixitSnapshot.Flag> flags = parts.get(i);
                for (FlagSpec s : ps) applySpecToList(flags, s);
                byte[] blob = PhixitSnapshot.deflateRaw(PhixitSnapshot.encode(flags));
                script.append("UPDATE param_partitions SET flags_content=x'")
                        .append(PhixitSnapshot.bytesToHex(blob))
                        .append("' WHERE param_partition_id=").append(pids.get(i)).append(";\n");
            }
            sb.append("  [").append(pkg).append("] ").append(pids.size()).append(" partitions updated\n");
        }

        if (script.length() == 0) {
            ok = false;
        } else {
            int servingVersion = (int) (System.currentTimeMillis() / 1000L);
            script.append("UPDATE last_fetch SET serving_version=").append(servingVersion)
                    .append(" WHERE type=1;\n");
            String sqlFile = filesDir + "/phixit_apply.sql";
            try {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(sqlFile);
                fos.write(script.toString().getBytes("UTF-8"));
                fos.close();
                StreamLogs w = MainActivity.runSuWithCmd(path + "/sqlite3 -batch " + PHENO_DB + " < " + sqlFile);
                if (!w.getErrorStreamLog().isEmpty()) {
                    sb.append("  apply ERR: ").append(w.getErrorStreamLog()).append("\n");
                    ok = false;
                }
                MainActivity.runSuWithCmd("rm -f " + sqlFile);
            } catch (Exception ex) {
                sb.append("  write ERR: ").append(ex).append("\n");
                ok = false;
            }
        }

        // Make GMS re-read the edited snapshot from the DB and restart fresh.
        MainActivity.runSuWithCmd("rm -rf /data/data/com.google.android.gms/files/phenotype");
        MainActivity.runSuWithCmd("am force-stop com.google.android.gms");
        if (!policy.equalsIgnoreCase("Permissive")) MainActivity.runSuWithCmd("setenforce 1");

        log.append(sb);
        return ok;
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
        final String path = ctx.getApplicationInfo().dataDir;
        LinkedHashMap<String, List<FlagSpec>> byPkg = new LinkedHashMap<String, List<FlagSpec>>();
        for (FlagSpec s : specs) {
            List<FlagSpec> l = byPkg.get(s.pkg);
            if (l == null) { l = new ArrayList<FlagSpec>(); byPkg.put(s.pkg, l); }
            l.add(s);
        }
        for (Map.Entry<String, List<FlagSpec>> e : byPkg.entrySet()) {
            StreamLogs r = MainActivity.runSuWithCmd(
                    path + "/sqlite3 -batch " + PHENO_DB + " " +
                            "'SELECT param_partition_id, hex(flags_content) FROM param_partitions " +
                            "WHERE static_config_package_id IN (SELECT static_config_package_id " +
                            "FROM static_config_packages WHERE name=\"" + e.getKey() + "\");'");
            String out = r.getInputStreamLog();
            if (out.trim().isEmpty()) return false;
            boolean sawPartition = false;
            for (String line : out.split("\\r?\\n")) {
                int bar = line.indexOf('|');
                if (bar <= 0) continue;
                String hex = line.substring(bar + 1).trim();
                if (hex.isEmpty()) continue;
                List<PhixitSnapshot.Flag> flags;
                try {
                    flags = PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(PhixitSnapshot.hexToBytes(hex)));
                } catch (Exception ex) {
                    return false;
                }
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
            if (!sawPartition) return false;
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
        String path = ctx.getApplicationInfo().dataDir;
        StreamLogs r = MainActivity.runSuWithCmd(
                path + "/sqlite3 -batch " + PHENO_DB + " " +
                        "'SELECT param_partition_id, hex(flags_content) FROM param_partitions " +
                        "WHERE static_config_package_id IN (SELECT static_config_package_id " +
                        "FROM static_config_packages WHERE name=\"" + pkg + "\");'");
        for (String line : r.getInputStreamLog().split("\\r?\\n")) {
            int bar = line.indexOf('|');
            if (bar <= 0) continue;
            String hex = line.substring(bar + 1).trim();
            if (hex.isEmpty()) continue;
            try {
                for (PhixitSnapshot.Flag f :
                        PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(PhixitSnapshot.hexToBytes(hex)))) {
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
