package com.xiddoc.androidautox;

/**
 * Pure SQL-string and result-parsing helpers extracted from the two list-screen
 * Activities — {@code CarRemover} (Gearhead {@code carservicedata.db}) and
 * {@code AccountsChooser} (GMS {@code phenotype.db}). Those Activities are excluded
 * from the coverage gate (framework shells), but the SQL text they build and the
 * way they split query output into rows is pure logic worth covering.
 *
 * <p>The Activities delegate to these; behaviour (the exact SQL and parsed rows) is
 * unchanged.
 */
public final class GearheadDbQueries {

    /** Gearhead car-service database holding the paired-car list (see {@link GmsPaths#CARSERVICE_DB}). */
    public static final String CAR_DB = GmsPaths.CARSERVICE_DB;

    /** GMS phenotype database holding per-account application tags (see {@link GmsPaths#PHENOTYPE_DB}). */
    public static final String PHENOTYPE_DB = GmsPaths.PHENOTYPE_DB;

    /** Selects the make/model of each paired car (one row per car). */
    public static final String SELECT_CARS = "SELECT manufacturer,model FROM allowedcars";

    /** Selects the client vehicle id of each paired car (parallel to {@link #SELECT_CARS}). */
    public static final String SELECT_CAR_IDS = "SELECT vehicleidclient FROM allowedcars";

    /** Distinct non-empty accounts that have application tags, sorted ascending. */
    public static final String SELECT_ACCOUNTS =
            "SELECT DISTINCT user FROM ApplicationTags WHERE user != '' ORDER BY user ASC";

    private GearheadDbQueries() {
    }

    /**
     * Build the {@code DELETE} statement that removes a single paired car by its
     * client vehicle id. Mirrors the inline string in {@code CarRemover}'s FAB
     * handler (which keyed off the stored pref keys).
     */
    public static String deleteCarById(String vehicleIdClient) {
        return "DELETE FROM allowedcars WHERE vehicleidclient='" + vehicleIdClient + "'";
    }

    /**
     * Line split ({@code split("\\r?\\n")}) of a query result, used by the list
     * screens to turn raw query output into rows. The car/account loaders then apply
     * their own {@code if (!str.trim().isEmpty())} filter (and, for cars, pair ids
     * positionally), so this preserves every slot {@code String.split} returns.
     */
    public static String[] splitLines(String queryOutput) {
        if (queryOutput == null) {
            return new String[]{""};
        }
        return queryOutput.split("\\r?\\n");
    }
}
