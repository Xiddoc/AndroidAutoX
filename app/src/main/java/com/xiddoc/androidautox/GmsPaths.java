package com.xiddoc.androidautox;

/**
 * Single source of truth for the on-device paths of the GMS / Gearhead databases
 * and caches this app edits, so the same absolute path is never spelled out in more
 * than one place. The phenotype DB path in particular was previously duplicated
 * across {@link GmsCommandBuilder}, {@link GearheadDbQueries}, {@link PhixitEngine},
 * {@link DbBackup}, {@link com.xiddoc.androidautox.CarRemoverActivity.CarRemover}
 * (and {@code MainActivity}); those now all reference the constants here, keeping the
 * auto-backup choke point and the engine in agreement about exactly which files get
 * the safety net.
 *
 * <p>Pure constants holder — no behaviour, no device access.
 */
public final class GmsPaths {

    private GmsPaths() {
    }

    /**
     * Absolute path to the GMS phenotype feature-flag database.
     *
     * <p>{@link #PHENO_DB} is a synonym kept for the call sites that spell it that
     * way; both point at the same literal.
     */
    public static final String PHENOTYPE_DB =
            "/data/data/com.google.android.gms/databases/phenotype.db";

    /** Synonym of {@link #PHENOTYPE_DB} (same literal). */
    public static final String PHENO_DB = PHENOTYPE_DB;

    /** GMS's phenotype cache dir, cleared after an edit so GMS re-reads the DB. */
    public static final String PHENO_CACHE_DIR =
            "/data/data/com.google.android.gms/files/phenotype";

    /** Gearhead car-service settings DB (paired cars, allowed-cars list, etc.). */
    public static final String CARSERVICE_DB =
            "/data/data/com.google.android.projection.gearhead/databases/carservicedata.db";

    /** Allowlist base under which recursive deletes are permitted. */
    public static final String GMS_DATA_DIR = "/data/data/com.google.android.gms/";
}
