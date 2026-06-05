package com.xiddoc.androidautox;

/**
 * Single source of truth for the on-device paths of the GMS / Gearhead databases
 * and caches this app edits. Previously these literals were duplicated across
 * {@link PhixitEngine}, {@link DbBackup}, {@link com.xiddoc.androidautox.CarRemoverActivity.CarRemover}
 * (and {@code MainActivity}); centralizing them keeps the auto-backup choke point and
 * the engine in agreement about exactly which files get the safety net.
 *
 * <p>NOTE: {@code MainActivity} still holds its own copy of the phenotype DB path
 * (it is owned by another agent and out of scope here); migrating it to this holder
 * is a follow-up.
 */
public final class GmsPaths {

    private GmsPaths() {}

    /** GMS Phenotype feature-flag database. */
    public static final String PHENO_DB =
            "/data/data/com.google.android.gms/databases/phenotype.db";

    /** GMS's phenotype cache dir, cleared after an edit so GMS re-reads the DB. */
    public static final String PHENO_CACHE_DIR =
            "/data/data/com.google.android.gms/files/phenotype";

    /** Gearhead car-service settings DB (paired cars, allowed-cars list, etc.). */
    public static final String CARSERVICE_DB =
            "/data/data/com.google.android.projection.gearhead/databases/carservicedata.db";

    /** Allowlist base under which recursive deletes are permitted. */
    public static final String GMS_DATA_DIR = "/data/data/com.google.android.gms/";
}
