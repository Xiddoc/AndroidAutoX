package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.xiddoc.androidautox.autox.provider.SettingsEntry;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/** Tests for the pure {@link FreeformGlobalSettingsSpec} (formerly SecureSettingsSpec). */
public class FreeformGlobalSettingsSpecTest {

    @Test
    public void managedKeys_orderAndContents() {
        assertEquals(Arrays.asList(
                        FreeformGlobalSettingsSpec.KEY_FORCE_RESIZABLE,
                        FreeformGlobalSettingsSpec.KEY_ENABLE_FREEFORM),
                FreeformGlobalSettingsSpec.managedKeys());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void managedKeys_unmodifiable() {
        FreeformGlobalSettingsSpec.managedKeys().add("x");
    }

    @Test
    public void applyList_bothAbsent_usesWriteDefault() {
        List<SettingsEntry> list = FreeformGlobalSettingsSpec.applyList(null, null);
        assertEquals(2, list.size());
        assertEquals(FreeformGlobalSettingsSpec.KEY_FORCE_RESIZABLE, list.get(0).key);
        assertEquals(FreeformGlobalSettingsSpec.KEY_ENABLE_FREEFORM, list.get(1).key);
        for (SettingsEntry e : list) {
            assertEquals(FreeformGlobalSettingsSpec.ENABLED_VALUE, e.applyValue);
            assertEquals(SettingsEntry.RevertStrategy.WRITE_DEFAULT, e.revertStrategy);
        }
    }

    @Test
    public void applyList_bothPresent_usesRestorePrior() {
        List<SettingsEntry> list = FreeformGlobalSettingsSpec.applyList(0, 1);
        assertEquals(SettingsEntry.RevertStrategy.RESTORE_PRIOR, list.get(0).revertStrategy);
        assertEquals(0, list.get(0).priorValue);
        assertEquals(SettingsEntry.RevertStrategy.RESTORE_PRIOR, list.get(1).revertStrategy);
        assertEquals(1, list.get(1).priorValue);
    }

    @Test
    public void applyList_mixed() {
        List<SettingsEntry> list = FreeformGlobalSettingsSpec.applyList(null, 1);
        assertEquals(SettingsEntry.RevertStrategy.WRITE_DEFAULT, list.get(0).revertStrategy);
        assertEquals(SettingsEntry.RevertStrategy.RESTORE_PRIOR, list.get(1).revertStrategy);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void applyList_unmodifiable() {
        FreeformGlobalSettingsSpec.applyList(null, null).add(
                SettingsEntry.forAbsentKey("x", 1));
    }

    @Test
    public void revertList_isReverseOrder() {
        List<SettingsEntry> list = FreeformGlobalSettingsSpec.revertList(0, 1);
        // Reverse: freeform first, then force-resizable.
        assertEquals(FreeformGlobalSettingsSpec.KEY_ENABLE_FREEFORM, list.get(0).key);
        assertEquals(FreeformGlobalSettingsSpec.KEY_FORCE_RESIZABLE, list.get(1).key);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void revertList_unmodifiable() {
        FreeformGlobalSettingsSpec.revertList(null, null).add(
                SettingsEntry.forAbsentKey("x", 1));
    }

    @Test
    public void constants() {
        assertEquals("force_resizable_activities",
                FreeformGlobalSettingsSpec.KEY_FORCE_RESIZABLE);
        assertEquals("enable_freeform_support",
                FreeformGlobalSettingsSpec.KEY_ENABLE_FREEFORM);
        assertEquals(1, FreeformGlobalSettingsSpec.ENABLED_VALUE);
        assertTrue(true);
    }

    @Test
    public void privateConstructor_isInvocableForCoverage() throws Exception {
        Constructor<FreeformGlobalSettingsSpec> c =
                FreeformGlobalSettingsSpec.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(c.getModifiers()));
        c.setAccessible(true);
        c.newInstance();
    }
}
