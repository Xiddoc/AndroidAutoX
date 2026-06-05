package com.xiddoc.androidautox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests the pure path-safety guard for {@code deleteRecursive}. The binder method and the
 * actual {@code File} delete run in the root process and aren't exercised here; only the
 * decision of which paths are too dangerous to delete recursively is.
 *
 * <p>Robolectric runner is used so loading {@link PhixitRootService} (which extends libsu's
 * {@code RootService}) resolves cleanly against the Android runtime.
 */
@RunWith(RobolectricTestRunner.class)
public class PhixitRootServiceGuardTest {

    @Test
    public void rejectsNullEmptyAndRoot() {
        assertTrue(PhixitRootService.isUnsafeDeletePath(null));
        assertTrue(PhixitRootService.isUnsafeDeletePath(""));
        assertTrue(PhixitRootService.isUnsafeDeletePath("   "));
        assertTrue(PhixitRootService.isUnsafeDeletePath("/"));
        assertTrue(PhixitRootService.isUnsafeDeletePath("  /  "));
    }

    @Test
    public void allowsRealCacheDir() {
        assertFalse(PhixitRootService.isUnsafeDeletePath(PhixitEngine.PHENO_CACHE_DIR));
        assertFalse(PhixitRootService.isUnsafeDeletePath(
                "/data/data/com.google.android.gms/files/phenotype"));
    }
}
