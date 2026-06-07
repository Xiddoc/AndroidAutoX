package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exhaustive tests for the pure {@link IpcCommand} wire schema. */
public class IpcCommandTest {

    private static Map<String, String> args(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void encodeDecode_noArgs_roundTrips() {
        IpcCommand c = IpcCommand.of(IpcCommand.Type.ENABLE_TRUSTED_DISPLAY);
        assertEquals("ENABLE_TRUSTED_DISPLAY", c.encode());
        assertEquals(c, IpcCommand.decode(c.encode()));
        assertTrue(c.getArgs().isEmpty());
    }

    @Test
    public void encodeDecode_withArgs_roundTripsInOrder() {
        IpcCommand c = IpcCommand.of(IpcCommand.Type.SET_UID_AFFINITY,
                args("uid", "10123", "device", "bus0"));
        assertEquals("SET_UID_AFFINITY|uid=10123;device=bus0", c.encode());
        IpcCommand back = IpcCommand.decode(c.encode());
        assertEquals(c, back);
        assertEquals("10123", back.arg("uid"));
        assertEquals("bus0", back.arg("device"));
        assertNull(back.arg("missing"));
    }

    @Test
    public void allTypes_roundTrip() {
        for (IpcCommand.Type t : IpcCommand.Type.values()) {
            IpcCommand c = IpcCommand.of(t, args("k", "v"));
            assertEquals(c, IpcCommand.decode(c.encode()));
        }
        // exercise enum synthetic methods
        assertEquals(IpcCommand.Type.SET_DISPLAY_IME,
                IpcCommand.Type.valueOf("SET_DISPLAY_IME"));
        assertEquals(4, IpcCommand.Type.values().length);
    }

    @Test
    public void getArgs_isUnmodifiable() {
        IpcCommand c = IpcCommand.of(IpcCommand.Type.ALLOW_INPUT_INJECTION, args("a", "b"));
        try {
            c.getArgs().put("x", "y");
            fail("args map must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void of_nullType_throws() {
        try {
            IpcCommand.of(null, args());
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("type"));
        }
    }

    @Test
    public void of_nullArgs_throws() {
        try {
            IpcCommand.of(IpcCommand.Type.SET_DISPLAY_IME, null);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("args"));
        }
    }

    @Test
    public void of_nullKey_throws() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put(null, "v");
        try {
            IpcCommand.of(IpcCommand.Type.SET_DISPLAY_IME, m);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("key"));
        }
    }

    @Test
    public void of_emptyValue_throws() {
        try {
            IpcCommand.of(IpcCommand.Type.SET_DISPLAY_IME, args("k", ""));
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("value"));
        }
    }

    @Test
    public void of_reservedSeparatorInKey_throws() {
        for (String bad : new String[]{"a|b", "a;b", "a=b"}) {
            try {
                IpcCommand.of(IpcCommand.Type.SET_DISPLAY_IME, args(bad, "v"));
                fail("expected rejection for key '" + bad + "'");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("reserved separator"));
            }
        }
    }

    @Test
    public void decode_nullOrBlank_throws() {
        for (String bad : new String[]{null, "", "   "}) {
            try {
                IpcCommand.decode(bad);
                fail("expected rejection for '" + bad + "'");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("wire"));
            }
        }
    }

    @Test
    public void decode_unknownType_throws() {
        try {
            IpcCommand.decode("NOPE|k=v");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown command type"));
        }
    }

    @Test
    public void decode_argWithoutEquals_throws() {
        try {
            IpcCommand.decode("SET_DISPLAY_IME|justkey");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("no '='"));
        }
    }

    @Test
    public void decode_argWithEmptyKey_throws() {
        try {
            IpcCommand.decode("SET_DISPLAY_IME|=v");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("key"));
        }
    }

    @Test
    public void decode_trimsSurroundingWhitespace() {
        IpcCommand c = IpcCommand.decode("  ENABLE_TRUSTED_DISPLAY  ");
        assertEquals(IpcCommand.Type.ENABLE_TRUSTED_DISPLAY, c.getType());
    }

    @Test
    public void equalsHashCodeToString_contract() {
        IpcCommand a = IpcCommand.of(IpcCommand.Type.SET_DISPLAY_IME, args("k", "v"));
        IpcCommand b = IpcCommand.of(IpcCommand.Type.SET_DISPLAY_IME, args("k", "v"));
        IpcCommand diffType = IpcCommand.of(IpcCommand.Type.SET_UID_AFFINITY, args("k", "v"));
        IpcCommand diffArgs = IpcCommand.of(IpcCommand.Type.SET_DISPLAY_IME, args("k", "w"));

        assertTrue(a.equals(a));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diffType);
        assertNotEquals(a, diffArgs);
        assertFalse(a.equals(null));
        assertFalse(a.equals("x"));
        assertTrue(a.toString().contains("SET_DISPLAY_IME"));
    }
}
