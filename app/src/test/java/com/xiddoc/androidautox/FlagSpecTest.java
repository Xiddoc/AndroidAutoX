package com.xiddoc.androidautox;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain-JUnit tests for the {@link FlagSpec} static factory helpers. Each helper
 * builds a {@link PhixitSnapshot.Flag} carrying the right type/value and records
 * the owning config package, name, and remove-flag.
 */
public class FlagSpecTest {

    private static final String PKG = FlagSpec.PKG_GEARHEAD;

    @Test
    public void packageConstants() {
        assertEquals("com.google.android.projection.gearhead", FlagSpec.PKG_GEARHEAD);
        assertEquals("com.google.android.gms.car", FlagSpec.PKG_CAR);
    }

    @Test
    public void bool_true() {
        FlagSpec s = FlagSpec.bool(PKG, "f", true);
        assertEquals(PKG, s.pkg);
        assertEquals("f", s.name);
        assertFalse(s.remove);
        assertEquals("f", s.flag.name);
        assertFalse(s.flag.numericName);
        assertEquals(PhixitSnapshot.TYPE_BOOL_TRUE, s.flag.type);
        assertTrue(s.flag.boolValue());
    }

    @Test
    public void bool_false() {
        FlagSpec s = FlagSpec.bool(FlagSpec.PKG_CAR, "g", false);
        assertEquals(FlagSpec.PKG_CAR, s.pkg);
        assertEquals(PhixitSnapshot.TYPE_BOOL_FALSE, s.flag.type);
        assertFalse(s.flag.boolValue());
        assertFalse(s.remove);
    }

    @Test
    public void lng() {
        FlagSpec s = FlagSpec.lng(PKG, "num", 9001L);
        assertEquals("num", s.name);
        assertEquals(PhixitSnapshot.TYPE_LONG, s.flag.type);
        assertEquals(9001L, s.flag.longValue);
        assertFalse(s.remove);
    }

    @Test
    public void dbl() {
        FlagSpec s = FlagSpec.dbl(PKG, "d", 2.5);
        assertEquals(PhixitSnapshot.TYPE_DOUBLE, s.flag.type);
        assertEquals(Double.doubleToRawLongBits(2.5), s.flag.doubleBits);
        assertEquals(2.5, Double.longBitsToDouble(s.flag.doubleBits), 0.0);
        assertFalse(s.remove);
    }

    @Test
    public void str() {
        FlagSpec s = FlagSpec.str(PKG, "s", "value");
        assertEquals(PhixitSnapshot.TYPE_STRING, s.flag.type);
        assertEquals("value", s.flag.stringValue);
        assertFalse(s.remove);
    }

    @Test
    public void bytes() {
        byte[] data = {1, 2, 3};
        FlagSpec s = FlagSpec.bytes(PKG, "b", data);
        assertEquals(PhixitSnapshot.TYPE_BYTES, s.flag.type);
        assertArrayEquals(data, s.flag.bytesValue);
        assertFalse(s.remove);
    }

    @Test
    public void remove_marksRemoveAndNullFlag() {
        FlagSpec s = FlagSpec.remove(PKG, "gone");
        assertEquals(PKG, s.pkg);
        assertEquals("gone", s.name);
        assertTrue(s.remove);
        assertNull(s.flag);
    }
}
