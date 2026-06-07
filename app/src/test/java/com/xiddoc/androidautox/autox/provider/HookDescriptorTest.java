package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Tests for the pure {@link HookDescriptor} data record. */
public class HookDescriptorTest {

    @Test
    public void validConstruction_storesFields() {
        HookDescriptor d = new HookDescriptor(
                HookDescriptor.Target.LAUNCH_ON_DISPLAY, "a.B", "m");
        assertEquals(HookDescriptor.Target.LAUNCH_ON_DISPLAY, d.target);
        assertEquals("a.B", d.className);
        assertEquals("m", d.methodName);
    }

    @Test
    public void nullTarget_throws() {
        try {
            new HookDescriptor(null, "a.B", "m");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("target"));
        }
    }

    @Test
    public void blankClassName_throws() {
        for (String bad : new String[]{null, "", "  "}) {
            try {
                new HookDescriptor(HookDescriptor.Target.LAUNCH_ON_DISPLAY, bad, "m");
                fail("expected rejection for className '" + bad + "'");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("className"));
            }
        }
    }

    @Test
    public void blankMethodName_throws() {
        for (String bad : new String[]{null, "", "  "}) {
            try {
                new HookDescriptor(HookDescriptor.Target.LAUNCH_ON_DISPLAY, "a.B", bad);
                fail("expected rejection for methodName '" + bad + "'");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("methodName"));
            }
        }
    }

    @Test
    public void equalsHashCodeToString_contract() {
        HookDescriptor a = new HookDescriptor(
                HookDescriptor.Target.INPUT_INJECT_DISPLAY, "a.B", "m");
        HookDescriptor b = new HookDescriptor(
                HookDescriptor.Target.INPUT_INJECT_DISPLAY, "a.B", "m");
        HookDescriptor diffTarget = new HookDescriptor(
                HookDescriptor.Target.LAUNCH_ON_DISPLAY, "a.B", "m");
        HookDescriptor diffClass = new HookDescriptor(
                HookDescriptor.Target.INPUT_INJECT_DISPLAY, "a.C", "m");
        HookDescriptor diffMethod = new HookDescriptor(
                HookDescriptor.Target.INPUT_INJECT_DISPLAY, "a.B", "n");

        assertTrue(a.equals(a));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diffTarget);
        assertNotEquals(a, diffClass);
        assertNotEquals(a, diffMethod);
        assertFalse(a.equals(null));
        assertFalse(a.equals("x"));
        assertTrue(a.toString().contains("INPUT_INJECT_DISPLAY"));
    }

    @Test
    public void targetEnum_isExercised() {
        assertEquals(HookDescriptor.Target.DISPLAY_TRUSTED_FLAG,
                HookDescriptor.Target.valueOf("DISPLAY_TRUSTED_FLAG"));
        assertEquals(5, HookDescriptor.Target.values().length);
    }
}
