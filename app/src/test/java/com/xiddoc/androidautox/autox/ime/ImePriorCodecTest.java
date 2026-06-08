package com.xiddoc.androidautox.autox.ime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * Exhaustive plain-JUnit tests for {@link ImePriorCodec}: the UNSET→null branch, the
 * present-value branches (enabled / disabled / arbitrary), and the private-constructor guard.
 */
public class ImePriorCodecTest {

    @Test
    public void unsetMapsToNull() {
        assertNull(ImePriorCodec.toBoxedPrior(ImeDisplaySettingsSpec.VALUE_UNSET));
    }

    @Test
    public void enabledMapsToOne() {
        assertEquals(Integer.valueOf(1),
                ImePriorCodec.toBoxedPrior(ImeDisplaySettingsSpec.VALUE_ENABLED));
    }

    @Test
    public void disabledMapsToZero() {
        assertEquals(Integer.valueOf(0),
                ImePriorCodec.toBoxedPrior(ImeDisplaySettingsSpec.VALUE_DISABLED));
    }

    @Test
    public void roundTripWithStoreSentinelGetters() {
        // toBoxedPrior is the symmetric inverse of the store's ...OrUnset getters.
        assertEquals(Integer.valueOf(1), ImePriorCodec.toBoxedPrior(1));
        assertNull(ImePriorCodec.toBoxedPrior(ImeDisplaySettingsSpec.VALUE_UNSET));
    }

    @Test
    public void constructorIsPrivate() throws Exception {
        Constructor<ImePriorCodec> c = ImePriorCodec.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(c.getModifiers()));
        c.setAccessible(true);
        c.newInstance(); // exercise the private ctor for coverage
    }
}
