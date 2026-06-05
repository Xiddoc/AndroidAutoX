package com.xiddoc.androidautox;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pure JUnit tests for {@link GearheadDbQueries} — the SQL strings and result
 * parsing extracted from {@code CarRemover} and {@code AccountsChooser}.
 */
public class GearheadDbQueriesTest {

    // ------------------------------------------------------------------
    // Constants (pin the exact SQL/paths that used to be inlined)
    // ------------------------------------------------------------------

    @Test
    public void carDbPath_pinned() {
        assertEquals("/data/data/com.google.android.projection.gearhead/databases/carservicedata.db",
                GearheadDbQueries.CAR_DB);
    }

    @Test
    public void phenotypeDbPath_pinned() {
        assertEquals("/data/data/com.google.android.gms/databases/phenotype.db",
                GearheadDbQueries.PHENOTYPE_DB);
    }

    @Test
    public void selectCars_pinned() {
        assertEquals("SELECT manufacturer,model FROM allowedcars", GearheadDbQueries.SELECT_CARS);
    }

    @Test
    public void selectCarIds_pinned() {
        assertEquals("SELECT vehicleidclient FROM allowedcars", GearheadDbQueries.SELECT_CAR_IDS);
    }

    @Test
    public void selectAccounts_pinned() {
        assertEquals("SELECT DISTINCT user FROM ApplicationTags WHERE user != '' ORDER BY user ASC",
                GearheadDbQueries.SELECT_ACCOUNTS);
    }

    // ------------------------------------------------------------------
    // deleteCarById
    // ------------------------------------------------------------------

    @Test
    public void deleteCarById_buildsDelete() {
        assertEquals("DELETE FROM allowedcars WHERE vehicleidclient='abc123'",
                GearheadDbQueries.deleteCarById("abc123"));
    }

    // ------------------------------------------------------------------
    // splitLines (positional split — keeps blanks)
    // ------------------------------------------------------------------

    @Test
    public void splitLines_keepsAllSlots() {
        // matches String.split("\\r?\\n"): trailing empties dropped by split, interior kept
        String[] out = GearheadDbQueries.splitLines("a\n\nb");
        assertArrayEquals(new String[]{"a", "", "b"}, out);
    }

    @Test
    public void splitLines_crlf() {
        assertArrayEquals(new String[]{"a", "b"}, GearheadDbQueries.splitLines("a\r\nb"));
    }

    @Test
    public void splitLines_single() {
        assertArrayEquals(new String[]{"solo"}, GearheadDbQueries.splitLines("solo"));
    }

    @Test
    public void splitLines_null_returnsSingleEmpty() {
        assertArrayEquals(new String[]{""}, GearheadDbQueries.splitLines(null));
    }

    @Test
    public void splitLines_empty_returnsSingleEmpty() {
        // String.split on "" yields {""}
        assertArrayEquals(new String[]{""}, GearheadDbQueries.splitLines(""));
    }
}
