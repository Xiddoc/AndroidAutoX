package com.xiddoc.androidautox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-logic helper that decides how to keep GMS's Phenotype <em>Heterodyne</em> sync
 * state coherent after this app rewrites the {@code param_partitions.flags_content}
 * blobs in {@code phenotype.db}.
 *
 * <h2>The bug this guards against</h2>
 * GMS's {@code HeterodyneSyncTaskChimeraService} periodically reconstructs each config
 * package's committed configuration from {@code phenotype.db} and validates the
 * reconstructed flag set against a count/digest it persists in committed-config
 * metadata. When that reconstruction sees a different number of flags than the metadata
 * says it should, the sync throws
 * <pre>java.lang.IllegalArgumentException: Encountered conflicting flags.
 *   Expected flag count N, but was M.</pre>
 * and GMS crashes silently in the background.
 *
 * <p>{@link PhixitEngine#applySpecs} can change a partition's real flag count: it
 * <em>adds</em> a brand-new flag when the flag name isn't already present in the
 * partition (and, on revert, <em>drops</em> flags). That makes the on-disk count drift
 * from the committed-config metadata Heterodyne validates against — the exact trigger
 * for the "conflicting flags" exception. A pure value edit (changing an existing flag's
 * value in place) keeps the count stable and is therefore safe.
 *
 * <h2>The fix</h2>
 * <ol>
 *   <li>{@link #countChanged(List, List)} reports whether a single partition's edit
 *       changed its flag count — i.e. whether it could trip Heterodyne. Callers use
 *       this to do the committed-config invalidation only when it is actually needed.</li>
 *   <li>{@link #clearSyncSql(Iterable)} returns the SQL that drops Heterodyne's stale
 *       committed-config bookkeeping (and therefore the stale expected flag count) for
 *       the affected packages, so the next sync re-derives a self-consistent count from
 *       the configuration currently on disk instead of validating our edited snapshot
 *       against a stale expected count. This is the DB-side analogue of the phenotype
 *       <em>file</em> cache clear the engine already performs.</li>
 * </ol>
 *
 * <h3>Non-destructive by design (issue #25, risk #1)</h3>
 * We deliberately do <em>not</em> force a server re-fetch here (e.g. by zeroing
 * {@code last_fetch.last_update_time}). The {@code param_partitions.flags_content} blob
 * <em>is</em> the committed serving set, so forcing a fresh server fetch would overwrite
 * the override we just applied — precisely the count-changing "pre-activate" tweaks this
 * path targets. Instead we only drop the stale committed-config <em>bookkeeping</em> so
 * GMS rebuilds the expected count from the configuration already on disk (our edit),
 * keeping the override intact. The engine separately bumps {@code last_fetch.serving_version}
 * (in {@link PhixitRootService.Impl#writePartitions}), clears the phenotype file cache, and
 * force-stops GMS, which together prompt that coherent re-commit on GMS's next start.
 *
 * <h3>Why the SQL is deliberately defensive</h3>
 * The exact name/linkage of Heterodyne's committed-config table varies across GMS
 * versions and is not documented, so we emit the same {@code DELETE} against the linkage
 * this app already uses for {@code param_partitions} ({@code static_config_packages}) plus
 * two version fallbacks (an alternate {@code config_packages} linkage and the legacy
 * {@code Configurations} table). They run through {@link RootDb#exec(String, java.util.List)}
 * whose root-side executor is <em>lenient</em> (it logs and skips a statement whose table
 * doesn't exist rather than aborting the batch). So on a build missing a given table we
 * simply clear the ones present; a wrong guess degrades to a harmless logged no-op and we
 * never corrupt the DB or throw. This class is a pure, unit-testable description of
 * <em>what</em> to clear — the device interaction stays in the engine.
 */
public final class HeterodyneSyncState {

    private HeterodyneSyncState() {
    }

    /**
     * True when applying the edit changed the partition's flag count, i.e. a flag was
     * added or removed (the situation that makes the on-disk count drift from the
     * committed-config count Heterodyne expects). A pure value edit — same number of
     * flags before and after — returns {@code false}.
     *
     * <p>{@code null} lists are treated as empty (size 0).
     */
    public static boolean countChanged(List<PhixitSnapshot.Flag> before,
                                       List<PhixitSnapshot.Flag> after) {
        int b = before == null ? 0 : before.size();
        int a = after == null ? 0 : after.size();
        return countChanged(b, a);
    }

    /**
     * Count-only overload: true when the flag count differs before vs. after the edit.
     * Used by {@link PhixitEngine} which already has the sizes to hand and need not
     * retain the pre-edit flag list.
     */
    public static boolean countChanged(int beforeCount, int afterCount) {
        return beforeCount != afterCount;
    }

    /**
     * SQL statements that drop Heterodyne's stale committed-config bookkeeping for the
     * given config packages, so GMS rebuilds a self-consistent expected flag count from
     * the configuration currently on disk (our edit) on its next sync — instead of
     * validating the count-drifted snapshot against the stale expected count and throwing
     * "Encountered conflicting flags".
     *
     * <p>For each package we delete its rows from the committed-configuration table,
     * keyed three ways for cross-version tolerance:
     * <ul>
     *   <li>via the {@code static_config_packages} linkage this app already uses to find
     *       a package's {@code param_partitions} (the most likely match on current GMS);</li>
     *   <li>via an alternate {@code config_packages} linkage; and</li>
     *   <li>against the legacy flat {@code Configurations} table keyed by package name.</li>
     * </ul>
     *
     * <p>It does <em>not</em> touch {@code param_partitions} (our override) or force a
     * server re-fetch, so the applied tweak is preserved (issue #25, risk #1). Every
     * statement is a no-op when its table/rows are absent on the running GMS build
     * (executed leniently), so this degrades gracefully across schema versions. Package
     * names are bound as SQL string literals with embedded single-quotes doubled;
     * Phenotype config-package names are reverse-DNS identifiers (letters, digits, dots,
     * underscores) so no exotic escaping is required, but the quote-doubling keeps the
     * statements well-formed regardless.
     *
     * @param packages the affected Phenotype config-package names (e.g.
     *                  {@code com.google.android.projection.gearhead}); {@code null}
     *                  entries and duplicates are ignored, order is preserved
     * @return an ordered, de-duplicated list of SQL statements; empty when {@code packages}
     *         is {@code null}/empty or contains only {@code null}s
     */
    public static List<String> clearSyncSql(Iterable<String> packages) {
        List<String> out = new ArrayList<String>();
        if (packages == null) return out;
        Set<String> seen = new LinkedHashSet<String>();
        for (String pkg : packages) {
            if (pkg == null) continue;
            if (!seen.add(pkg)) continue;
            String lit = sqlLiteral(pkg);
            // Modern linkage: the table this app already joins param_partitions through.
            out.add("DELETE FROM committed_configurations WHERE static_config_package_id IN "
                    + "(SELECT static_config_package_id FROM static_config_packages WHERE name=" + lit + ")");
            // Alternate linkage seen on some GMS builds.
            out.add("DELETE FROM committed_configurations WHERE config_package IN "
                    + "(SELECT id FROM config_packages WHERE name=" + lit + ")");
            // Legacy flat table keyed by package name.
            out.add("DELETE FROM Configurations WHERE packageName=" + lit);
        }
        return out;
    }

    /** Quotes {@code s} as a SQL string literal, doubling embedded single-quotes. */
    static String sqlLiteral(String s) {
        return "'" + s.replace("'", "''") + "'";
    }
}
