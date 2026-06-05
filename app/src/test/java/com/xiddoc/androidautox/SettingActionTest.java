package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Plain-JUnit (no Robolectric) coverage for {@link SettingAction}: declaration order,
 * dev-only flags, and the {@link SettingAction#visibleActions(boolean)} filtering for both
 * branches.
 */
public class SettingActionTest {

    @Test
    public void isDevOnly_matchesExpectationForEveryConstant() {
        for (SettingAction action : SettingAction.values()) {
            boolean expectedDevOnly =
                    action == SettingAction.PHIXIT_APPLY_TEST
                            || action == SettingAction.PHIXIT_DUMP_ALL;
            assertEquals(action.name(), expectedDevOnly, action.isDevOnly());
        }
    }

    @Test
    public void visibleActions_devMode_returnsAllInDeclarationOrder() {
        List<SettingAction> expected = Arrays.asList(
                SettingAction.RESET_TWEAKS,
                SettingAction.AUTO_REAPPLY,
                SettingAction.AA_SETTINGS,
                SettingAction.NONDESTRUCTIVE_PATCH,
                SettingAction.AUTO_BACKUP_DBS,
                SettingAction.PHIXIT_APPLY_TEST,
                SettingAction.PHIXIT_DUMP_ALL,
                SettingAction.DEV_MODE_TOGGLE);
        assertEquals(expected, SettingAction.visibleActions(true));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void visibleActions_devMode_returnsUnmodifiableList() {
        SettingAction.visibleActions(true).add(SettingAction.RESET_TWEAKS);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void visibleActions_normalMode_returnsUnmodifiableList() {
        SettingAction.visibleActions(false).add(SettingAction.RESET_TWEAKS);
    }

    @Test
    public void visibleActions_normalMode_excludesDevOnlyButKeepsOrder() {
        List<SettingAction> expected = Arrays.asList(
                SettingAction.RESET_TWEAKS,
                SettingAction.AUTO_REAPPLY,
                SettingAction.AA_SETTINGS,
                SettingAction.NONDESTRUCTIVE_PATCH,
                SettingAction.AUTO_BACKUP_DBS,
                SettingAction.DEV_MODE_TOGGLE);
        assertEquals(expected, SettingAction.visibleActions(false));
    }

    @Test
    public void visibleActions_normalMode_containsNoDevOnlyEntries() {
        for (SettingAction action : SettingAction.visibleActions(false)) {
            assertFalse(action.name(), action.isDevOnly());
        }
    }

    @Test
    public void visibleActions_devMode_containsBothDevOnlyEntries() {
        List<SettingAction> visible = SettingAction.visibleActions(true);
        assertTrue(visible.contains(SettingAction.PHIXIT_APPLY_TEST));
        assertTrue(visible.contains(SettingAction.PHIXIT_DUMP_ALL));
    }

    @Test
    public void valueOf_roundTripsName() {
        // Exercise the implicitly-generated valueOf for coverage completeness.
        for (SettingAction action : SettingAction.values()) {
            assertEquals(action, SettingAction.valueOf(action.name()));
        }
    }
}
