package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Exhaustive plain-JUnit truth table for {@link TweakStatusResolver#resolve}.
 *
 * <p>Covers all 12 combinations of:
 * <ul>
 *   <li>{@code enabled} ∈ {true, false}</li>
 *   <li>{@code rebootPending} ∈ {true, false}</li>
 *   <li>{@code appliedInDb} ∈ {Boolean.TRUE, Boolean.FALSE, null}</li>
 * </ul>
 *
 * Also verifies the int-code mapping (0/1/2) produced by {@link TweakStatus#code()}.
 */
public class TweakStatusResolverTest {

    // =========================================================================
    // int-code mapping
    // =========================================================================

    @Test
    public void disabled_hasCode0() {
        assertEquals(0, TweakStatus.DISABLED.code());
    }

    @Test
    public void rebootPending_hasCode1() {
        assertEquals(1, TweakStatus.REBOOT_PENDING.code());
    }

    @Test
    public void applied_hasCode2() {
        assertEquals(2, TweakStatus.APPLIED.code());
    }

    @Test
    public void fromCode_roundTrips() {
        assertEquals(TweakStatus.DISABLED,      TweakStatus.fromCode(0));
        assertEquals(TweakStatus.REBOOT_PENDING, TweakStatus.fromCode(1));
        assertEquals(TweakStatus.APPLIED,        TweakStatus.fromCode(2));
    }

    // =========================================================================
    // Truth table — appliedInDb == Boolean.TRUE (rule 1: reality wins → APPLIED)
    // =========================================================================

    // enabled=true, rebootPending=true, appliedInDb=TRUE
    @Test
    public void tt01_enabled_rebootPending_dbTrue_isApplied() {
        assertEquals(TweakStatus.APPLIED,
                TweakStatusResolver.resolve(true, true, Boolean.TRUE));
    }

    // enabled=true, rebootPending=false, appliedInDb=TRUE
    @Test
    public void tt02_enabled_noReboot_dbTrue_isApplied() {
        assertEquals(TweakStatus.APPLIED,
                TweakStatusResolver.resolve(true, false, Boolean.TRUE));
    }

    // enabled=false, rebootPending=true, appliedInDb=TRUE
    @Test
    public void tt03_disabled_rebootPending_dbTrue_isApplied() {
        assertEquals(TweakStatus.APPLIED,
                TweakStatusResolver.resolve(false, true, Boolean.TRUE));
    }

    // enabled=false, rebootPending=false, appliedInDb=TRUE
    @Test
    public void tt04_disabled_noReboot_dbTrue_isApplied() {
        assertEquals(TweakStatus.APPLIED,
                TweakStatusResolver.resolve(false, false, Boolean.TRUE));
    }

    // =========================================================================
    // Truth table — appliedInDb == Boolean.FALSE (confirmed NOT in DB)
    // =========================================================================

    // enabled=true, rebootPending=true, appliedInDb=FALSE → REBOOT_PENDING (rule 2)
    @Test
    public void tt05_enabled_rebootPending_dbFalse_isRebootPending() {
        assertEquals(TweakStatus.REBOOT_PENDING,
                TweakStatusResolver.resolve(true, true, Boolean.FALSE));
    }

    // enabled=true, rebootPending=false, appliedInDb=FALSE → DISABLED (rule 4: drift)
    @Test
    public void tt06_enabled_noReboot_dbFalse_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(true, false, Boolean.FALSE));
    }

    // enabled=false, rebootPending=true, appliedInDb=FALSE → DISABLED (rule 4: not enabled)
    @Test
    public void tt07_disabled_rebootPending_dbFalse_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(false, true, Boolean.FALSE));
    }

    // enabled=false, rebootPending=false, appliedInDb=FALSE → DISABLED (rule 4)
    @Test
    public void tt08_disabled_noReboot_dbFalse_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(false, false, Boolean.FALSE));
    }

    // =========================================================================
    // Truth table — appliedInDb == null (unknown / no-root fallback)
    // =========================================================================

    // enabled=true, rebootPending=true, appliedInDb=null → REBOOT_PENDING (rule 2)
    @Test
    public void tt09_enabled_rebootPending_dbUnknown_isRebootPending() {
        assertEquals(TweakStatus.REBOOT_PENDING,
                TweakStatusResolver.resolve(true, true, null));
    }

    // enabled=true, rebootPending=false, appliedInDb=null → APPLIED (rule 3: optimistic)
    @Test
    public void tt10_enabled_noReboot_dbUnknown_isApplied() {
        assertEquals(TweakStatus.APPLIED,
                TweakStatusResolver.resolve(true, false, null));
    }

    // enabled=false, rebootPending=true, appliedInDb=null → DISABLED (rule 4: not enabled)
    @Test
    public void tt11_disabled_rebootPending_dbUnknown_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(false, true, null));
    }

    // enabled=false, rebootPending=false, appliedInDb=null → DISABLED (rule 4)
    @Test
    public void tt12_disabled_noReboot_dbUnknown_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(false, false, null));
    }

    // =========================================================================
    // Sanity guards
    // =========================================================================

    /** dbTrue must always beat rebootPending regardless of the enabled flag. */
    @Test
    public void dbTrue_alwaysBeatsRebootPending() {
        assertEquals(
                TweakStatus.APPLIED,
                TweakStatusResolver.resolve(true, true, Boolean.TRUE));
        assertEquals(
                TweakStatus.APPLIED,
                TweakStatusResolver.resolve(false, true, Boolean.TRUE));
    }

    /** The three possible return values are all distinct enum members. */
    @Test
    public void threeStatusesAreDistinct() {
        assertNotEquals(TweakStatus.DISABLED,      TweakStatus.REBOOT_PENDING);
        assertNotEquals(TweakStatus.DISABLED,      TweakStatus.APPLIED);
        assertNotEquals(TweakStatus.REBOOT_PENDING, TweakStatus.APPLIED);
    }

    /**
     * Regression: "optimistic null" (enabled, null DB) must return APPLIED, not DISABLED.
     * This ensures we never regress to red just because root wasn't checked yet.
     */
    @Test
    public void optimisticNull_doesNotRegessToRed() {
        TweakStatus result = TweakStatusResolver.resolve(true, false, null);
        assertNotEquals(TweakStatus.DISABLED, result);
        assertEquals(TweakStatus.APPLIED, result);
    }
}
