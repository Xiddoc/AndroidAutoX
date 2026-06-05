package com.xiddoc.androidautox;

import android.content.Context;

import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Objects;

/**
 * Stateless, side-effect-free query: "are a tweak's flags actually applied in the DB right now?"
 *
 * <h3>Design</h3>
 * The class is fully injectable for off-device testing.  Two seams are injected at construction
 * time:
 * <ul>
 *   <li><b>{@link AppliedProbe}</b> — wraps the real {@link PhixitEngine#isAppliedStrict} call
 *       (or a fake in tests).  Isolates the root-process/DB dependency from callers.  The strict
 *       variant is used so that a temporarily unreadable DB propagates as an exception (→ null /
 *       UNKNOWN) rather than silently returning {@code false} (→ DISABLED / confirmed-not-applied),
 *       which would wrongly drive a correctly-applied tweak toward red.
 *   <li><b>{@link SpecResolver}</b> — maps a tweak key to its {@link FlagSpec} list (or a fake).
 *       Wraps {@link TweakRegistry#specsFor} for production, avoiding a hard {@link Context}
 *       dependency in the method body.
 * </ul>
 *
 * <h3>Return value of {@link #appliedState(String)}</h3>
 * <ul>
 *   <li>{@link Boolean#TRUE}  — every spec is confirmed applied in the DB.
 *   <li>{@link Boolean#FALSE} — at least one spec is confirmed NOT applied.
 *   <li>{@code null}          — UNKNOWN: specs are absent (null or empty), or the probe threw
 *       (e.g. no root, RootDb unavailable, AIDL error).  Callers must treat null as
 *       "cannot determine" rather than either applied or not-applied.
 * </ul>
 *
 * <h3>Empty-specs policy</h3>
 * An empty list means "nothing to assert".  Rather than vacuously returning TRUE (which would
 * incorrectly report a tweak as applied when there are no flags to verify), we return {@code null}
 * (UNKNOWN).  This is safer: callers fall back to stored state rather than silently masking a
 * missing spec set.
 *
 * <h3>Thread safety</h3>
 * The object itself is stateless; all state lives in the injected collaborators.  Concurrency
 * is the caller's responsibility.  {@link #appliedState(String)} performs blocking root IPC and
 * MUST be called off the main thread.
 */
public final class TweakAppliedChecker {

    // -------------------------------------------------------------------------
    // Public interfaces (seams for testing)
    // -------------------------------------------------------------------------

    /**
     * Probe that checks whether a set of flag specs is applied in the DB.
     * Mirrors {@link PhixitEngine#isAppliedStrict(List)}.
     */
    public interface AppliedProbe {
        /**
         * Returns {@code true} if every spec in {@code specs} is confirmed applied.
         *
         * @throws Exception if the DB is unavailable (no root, AIDL failure, etc.) —
         *                   callers must treat this as UNKNOWN, not FALSE
         */
        boolean isApplied(List<FlagSpec> specs) throws Exception;
    }

    /**
     * Resolves a tweak key to its list of {@link FlagSpec}s.
     * Mirrors {@link TweakRegistry#specsFor(Context, String)}.
     */
    public interface SpecResolver {
        /**
         * Returns the specs for {@code key}, or {@code null} if the key is unknown.
         */
        List<FlagSpec> specsFor(String key);
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final AppliedProbe probe;
    private final SpecResolver resolver;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Full injection constructor — use this in tests with fake collaborators.
     *
     * @param probe    reads the DB to determine whether specs are applied; must not be null
     * @param resolver maps a tweak key to its list of {@link FlagSpec}s; must not be null
     */
    public TweakAppliedChecker(AppliedProbe probe, SpecResolver resolver) {
        Objects.requireNonNull(probe, "probe must not be null");
        Objects.requireNonNull(resolver, "resolver must not be null");
        this.probe = probe;
        this.resolver = resolver;
    }

    /**
     * Production convenience constructor.  Wires {@link PhixitEngine#isAppliedStrict} (as the
     * probe) and {@link TweakRegistry#specsFor(Context, String)} (as the resolver).
     *
     * <p>Using {@code isAppliedStrict} (rather than {@code isApplied}) ensures that a
     * transiently unreadable DB propagates as an exception → null/UNKNOWN, preventing a
     * correctly-applied tweak from being shown as red while root/DB is momentarily unavailable.
     *
     * @param ctx any Android {@link Context}; {@code getApplicationContext()} is called to
     *            avoid holding a reference to an Activity; must not be null
     * @throws NullPointerException if {@code ctx} is null
     */
    public TweakAppliedChecker(Context ctx) {
        this(buildProbe(ctx), buildResolver(ctx));
    }

    private static AppliedProbe buildProbe(Context ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        final Context appCtx = ctx.getApplicationContext();
        final PhixitEngine engine = new PhixitEngine(appCtx, null);
        return engine::isAppliedStrict;
    }

    private static SpecResolver buildResolver(Context ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        final Context appCtx = ctx.getApplicationContext();
        return key -> TweakRegistry.specsFor(appCtx, key);
    }

    // -------------------------------------------------------------------------
    // Main query
    // -------------------------------------------------------------------------

    /**
     * Checks whether the flags for tweak key {@code key} are currently applied in the DB.
     *
     * <ul>
     *   <li>{@link Boolean#TRUE}  — probe confirmed every flag is applied.
     *   <li>{@link Boolean#FALSE} — probe confirmed at least one flag is NOT applied.
     *   <li>{@code null}          — UNKNOWN: specs were null/empty, or the probe threw.
     * </ul>
     *
     * <p>This method performs blocking root IPC; it MUST be called off the main thread.
     *
     * @param key tweak identifier (e.g. {@code "bluetooth_pairing_off"})
     * @return tri-state result; never throws
     */
    @WorkerThread
    public Boolean appliedState(String key) {
        List<FlagSpec> specs;
        try {
            specs = resolver.specsFor(key);
        } catch (Exception e) {
            // Resolver failure is treated as UNKNOWN, same as probe failure.
            return null;
        }

        if (specs == null || specs.isEmpty()) {
            // Nothing to assert: return UNKNOWN rather than vacuous TRUE.
            return null;
        }

        try {
            return probe.isApplied(specs) ? Boolean.TRUE : Boolean.FALSE;
        } catch (Exception e) {
            // Probe failure (no root, AIDL error, structurally unreadable DB) -> UNKNOWN.
            // Critically: we use isAppliedStrict so that "DB not readable" arrives here as
            // an exception rather than false, preventing false DISABLED results.
            return null;
        }
    }
}
