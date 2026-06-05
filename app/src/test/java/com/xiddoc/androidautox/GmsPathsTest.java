package com.xiddoc.androidautox;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins the single-source-of-truth GMS path constant and confirms the other call
 * sites resolve to the same literal (no duplicated path drift). Also touches the
 * {@link GmsPaths} {@code <clinit>} so the constants holder reaches full coverage.
 */
public class GmsPathsTest {

    @Test
    public void phenotypeDb_isTheExpectedAbsolutePath() {
        assertEquals(
                "/data/data/com.google.android.gms/databases/phenotype.db",
                GmsPaths.PHENOTYPE_DB);
    }

    @Test
    public void phenotypeDb_isReusedBytheHelpers() {
        // The previously-duplicated literals now all reference the one constant.
        assertSame(GmsPaths.PHENOTYPE_DB, GmsCommandBuilder.PHENOTYPE_DB);
        assertSame(GmsPaths.PHENOTYPE_DB, GearheadDbQueries.PHENOTYPE_DB);
        assertSame(GmsPaths.PHENOTYPE_DB, PhixitEngine.PHENO_DB);
    }
}
