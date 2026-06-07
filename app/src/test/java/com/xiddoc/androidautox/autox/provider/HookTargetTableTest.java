package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Tests for the per-SDK {@link HookTargetTable} resolver and the {@link HookTargetSet}
 * it returns, including the unknown-SDK fallback branch.
 */
public class HookTargetTableTest {

    @Test
    public void resolveFor_eachKnownSdk_isCompleteAndResolved() {
        for (int sdk = HookTargetTable.MIN_SDK; sdk <= HookTargetTable.MAX_SDK; sdk++) {
            HookTargetSet set = HookTargetTable.resolveFor(sdk);
            assertTrue("sdk " + sdk + " should resolve", set.resolved);
            assertEquals(sdk, set.sdkInt);
            assertTrue("sdk " + sdk + " should have all targets", set.isComplete());
            // every logical target is present and indexed
            for (HookDescriptor.Target t : HookDescriptor.Target.values()) {
                assertNotNull(set.get(t));
                assertEquals(t, set.get(t).target);
            }
        }
    }

    @Test
    public void resolveFor_unknownSdkBelowRange_unresolvedEmpty() {
        HookTargetSet set = HookTargetTable.resolveFor(HookTargetTable.MIN_SDK - 1);
        assertFalse(set.resolved);
        assertEquals(HookTargetTable.MIN_SDK - 1, set.sdkInt);
        assertTrue(set.all().isEmpty());
        assertFalse(set.isComplete());
        assertNull(set.get(HookDescriptor.Target.LAUNCH_ON_DISPLAY));
    }

    @Test
    public void resolveFor_unknownSdkAboveRange_unresolved() {
        HookTargetSet set = HookTargetTable.resolveFor(HookTargetTable.MAX_SDK + 1);
        assertFalse(set.resolved);
        assertTrue(set.all().isEmpty());
    }

    @Test
    public void supportedSdks_coversTheRange() {
        List<Integer> sdks = HookTargetTable.supportedSdks();
        assertEquals(HookTargetTable.MAX_SDK - HookTargetTable.MIN_SDK + 1, sdks.size());
        assertTrue(sdks.contains(31));
        assertTrue(sdks.contains(32));
        assertTrue(sdks.contains(33));
        assertTrue(sdks.contains(34));
    }

    @Test
    public void isSupported_trueInRange_falseOutside() {
        assertTrue(HookTargetTable.isSupported(31));
        assertTrue(HookTargetTable.isSupported(34));
        assertFalse(HookTargetTable.isSupported(30));
        assertFalse(HookTargetTable.isSupported(35));
    }

    @Test
    public void hookTargetSet_toString_mentionsSdkAndResolved() {
        HookTargetSet set = HookTargetTable.resolveFor(34);
        assertTrue(set.toString().contains("34"));
        assertTrue(set.toString().contains("resolved=true"));
    }

    @Test
    public void all_isUnmodifiable() {
        HookTargetSet set = HookTargetTable.resolveFor(34);
        try {
            set.all().clear();
            org.junit.Assert.fail("all() must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}
