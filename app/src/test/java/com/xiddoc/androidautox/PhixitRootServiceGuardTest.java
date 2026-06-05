package com.xiddoc.androidautox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.system.OsConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests the pure path-safety guard and the symlink-skip decision for {@code deleteRecursive}.
 * The binder method and the actual {@code File} delete run in the root process and aren't
 * exercised here; only the pure decisions (which paths are too dangerous to delete
 * recursively, and whether to descend into a given {@code lstat} mode) are.
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
    public void rejectsParentTraversal() {
        // A `..` component could escape the allowlist if naively resolved.
        assertTrue(PhixitRootService.isUnsafeDeletePath(
                "/data/data/com.google.android.gms/files/../../../system"));
        assertTrue(PhixitRootService.isUnsafeDeletePath(
                "/data/data/com.google.android.gms/.."));
        assertTrue(PhixitRootService.isUnsafeDeletePath("../etc"));
        assertTrue(PhixitRootService.isUnsafeDeletePath(".."));
    }

    @Test
    public void rejectsPathsOutsideGmsAllowlist() {
        assertTrue(PhixitRootService.isUnsafeDeletePath("/data/data/com.evil.app/files"));
        assertTrue(PhixitRootService.isUnsafeDeletePath("/system/bin"));
        assertTrue(PhixitRootService.isUnsafeDeletePath("/sdcard/Download"));
        // Sneaky prefix that is NOT actually nested under the GMS dir.
        assertTrue(PhixitRootService.isUnsafeDeletePath(
                "/data/data/com.google.android.gms.evil/files"));
    }

    @Test
    public void allowsRealCacheDir() {
        assertFalse(PhixitRootService.isUnsafeDeletePath(GmsPaths.PHENO_CACHE_DIR));
        assertFalse(PhixitRootService.isUnsafeDeletePath(
                "/data/data/com.google.android.gms/files/phenotype"));
        // The allowlist base itself is allowed.
        assertFalse(PhixitRootService.isUnsafeDeletePath("/data/data/com.google.android.gms"));
    }

    // --- symlink-skip decision (deleteTree must not descend symlinks) ----

    @Test
    public void shouldDescend_onlyIntoRealDirectories() {
        // A real directory: descend.
        assertTrue(PhixitRootService.shouldDescend(OsConstants.S_IFDIR | 0755));
        // A regular file: don't descend (it gets deleted, not walked).
        assertFalse(PhixitRootService.shouldDescend(OsConstants.S_IFREG | 0644));
        // A symlink (even one pointing at a dir): NEVER descend -- deleting through it would
        // remove the link's target tree outside the requested subtree.
        assertFalse(PhixitRootService.shouldDescend(OsConstants.S_IFLNK | 0777));
    }
}
