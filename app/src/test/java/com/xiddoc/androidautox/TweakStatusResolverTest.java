package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

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
 * Also verifies the int-code mapping (0/1/2) produced by {@link TweakStatus#code()},
 * round-trip via {@link TweakStatus#fromCode}, and invalid-code rejection.
 *
 * <h3>Expected truth table (precedence rules in {@link TweakStatusResolver})</h3>
 * <pre>
 * enabled | rebootPending | appliedInDb | expected
 * --------+---------------+-------------+-----------------
 *   false |     *         |     *       | DISABLED        (rule 1)
 *   true  |     true      |   TRUE      | REBOOT_PENDING  (rule 2 beats rule 3)
 *   true  |     true      |   FALSE     | REBOOT_PENDING  (rule 2)
 *   true  |     true      |   null      | REBOOT_PENDING  (rule 2)
 *   true  |     false     |   TRUE      | APPLIED         (rule 3)
 *   true  |     false     |   FALSE     | DISABLED        (rule 5)
 *   true  |     false     |   null      | APPLIED         (rule 4: optimistic)
 * </pre>
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
        for (TweakStatus s : TweakStatus.values()) {
            assertEquals(s, TweakStatus.fromCode(s.code()));
        }
        assertEquals(TweakStatus.DISABLED,       TweakStatus.fromCode(0));
        assertEquals(TweakStatus.REBOOT_PENDING,  TweakStatus.fromCode(1));
        assertEquals(TweakStatus.APPLIED,         TweakStatus.fromCode(2));
    }

    @Test
    public void fromCode_invalidCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TweakStatus.fromCode(99));
        assertThrows(IllegalArgumentException.class, () -> TweakStatus.fromCode(-1));
    }

    // =========================================================================
    // Truth table — enabled = false (rule 1: always DISABLED regardless of other inputs)
    // =========================================================================

    // enabled=false, rebootPending=true, appliedInDb=TRUE  → DISABLED  (rule 1)
    @Test
    public void tt01_disabled_rebootPending_dbTrue_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(false, true, Boolean.TRUE));
    }

    // enabled=false, rebootPending=false, appliedInDb=TRUE  → DISABLED  (rule 1)
    @Test
    public void tt02_disabled_noReboot_dbTrue_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(false, false, Boolean.TRUE));
    }

    // enabled=false, rebootPending=true, appliedInDb=FALSE  → DISABLED  (rule 1)
    @Test
    public void tt03_disabled_rebootPending_dbFalse_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(false, true, Boolean.FALSE));
    }

    // enabled=false, rebootPending=false, appliedInDb=FALSE  → DISABLED  (rule 1)
    @Test
    public void tt04_disabled_noReboot_dbFalse_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(false, false, Boolean.FALSE));
    }

    // enabled=false, rebootPending=true, appliedInDb=null  → DISABLED  (rule 1)
    @Test
    public void tt05_disabled_rebootPending_dbNull_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(false, true, null));
    }

    // enabled=false, rebootPending=false, appliedInDb=null  → DISABLED  (rule 1)
    @Test
    public void tt06_disabled_noReboot_dbNull_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(false, false, null));
    }

    // =========================================================================
    // Truth table — enabled=true, rebootPending=true (rule 2: REBOOT_PENDING beats all)
    // =========================================================================

    /**
     * Anti-regression: when rebootPending is true, appliedInDb=TRUE must NOT produce
     * APPLIED.  The flags were just written (DB is TRUE) but the process hasn't restarted
     * yet; we must stay yellow until the reboot clears the pending marker.
     */
    @Test
    public void tt07_enabled_rebootPending_dbTrue_isRebootPending() {
        assertEquals(TweakStatus.REBOOT_PENDING,
                TweakStatusResolver.resolve(true, true, Boolean.TRUE));
    }

    // enabled=true, rebootPending=true, appliedInDb=FALSE  → REBOOT_PENDING  (rule 2)
    @Test
    public void tt08_enabled_rebootPending_dbFalse_isRebootPending() {
        assertEquals(TweakStatus.REBOOT_PENDING,
                TweakStatusResolver.resolve(true, true, Boolean.FALSE));
    }

    // enabled=true, rebootPending=true, appliedInDb=null  → REBOOT_PENDING  (rule 2)
    @Test
    public void tt09_enabled_rebootPending_dbNull_isRebootPending() {
        assertEquals(TweakStatus.REBOOT_PENDING,
                TweakStatusResolver.resolve(true, true, null));
    }

    // =========================================================================
    // Truth table — enabled=true, rebootPending=false (rules 3–5)
    // =========================================================================

    // enabled=true, rebootPending=false, appliedInDb=TRUE  → APPLIED  (rule 3)
    @Test
    public void tt10_enabled_noReboot_dbTrue_isApplied() {
        assertEquals(TweakStatus.APPLIED,
                TweakStatusResolver.resolve(true, false, Boolean.TRUE));
    }

    // enabled=true, rebootPending=false, appliedInDb=FALSE  → DISABLED  (rule 5: confirmed gone)
    @Test
    public void tt11_enabled_noReboot_dbFalse_isDisabled() {
        assertEquals(TweakStatus.DISABLED,
                TweakStatusResolver.resolve(true, false, Boolean.FALSE));
    }

    /**
     * Anti-regression: "optimistic null" (enabled, no reboot pending, DB unreadable) must
     * return APPLIED, not DISABLED.  Prevents false-red when root/DB is momentarily
     * unavailable for a tweak that was correctly applied earlier.
     */
    @Test
    public void tt12_enabled_noReboot_dbNull_isApplied_doesNotRegressToRed() {
        TweakStatus result = TweakStatusResolver.resolve(true, false, null);
        assertEquals(TweakStatus.APPLIED, result);
        assertNotEquals(TweakStatus.DISABLED, result);
    }

    // =========================================================================
    // Sanity guards
    // =========================================================================

    /**
     * The three possible return values are all distinct enum members (no accidental
     * aliasing).
     */
    @Test
    public void threeStatusesAreDistinct() {
        assertNotEquals(TweakStatus.DISABLED,       TweakStatus.REBOOT_PENDING);
        assertNotEquals(TweakStatus.DISABLED,       TweakStatus.APPLIED);
        assertNotEquals(TweakStatus.REBOOT_PENDING, TweakStatus.APPLIED);
    }
}
