package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.xiddoc.androidautox.autox.provider.SettingsResult;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * Exhaustive plain-JUnit tests for {@link SettingsPriorMapper}: every {@link SettingsResult}
 * status branch plus the null-result branch, and the private-constructor guard.
 */
public class SettingsPriorMapperTest {

    @Test
    public void okResultMapsToItsValue() {
        assertEquals(Integer.valueOf(1), SettingsPriorMapper.toPrior(SettingsResult.ok(1)));
    }

    @Test
    public void okResultZeroMapsToBoxedZero_distinctFromAbsent() {
        // present-0 must NOT collapse to null (that would lose RESTORE_PRIOR vs WRITE_DEFAULT).
        assertEquals(Integer.valueOf(0), SettingsPriorMapper.toPrior(SettingsResult.ok(0)));
    }

    @Test
    public void notFoundMapsToNull() {
        assertNull(SettingsPriorMapper.toPrior(SettingsResult.notFound()));
    }

    @Test
    public void deniedMapsToNull() {
        assertNull(SettingsPriorMapper.toPrior(SettingsResult.denied()));
    }

    @Test
    public void nullResultMapsToNull() {
        assertNull(SettingsPriorMapper.toPrior(null));
    }

    @Test
    public void constructorIsPrivate() throws Exception {
        Constructor<SettingsPriorMapper> c =
                SettingsPriorMapper.class.getDeclaredConstructor();
        assertEquals(true, Modifier.isPrivate(c.getModifiers()));
        c.setAccessible(true);
        c.newInstance(); // exercise the private ctor for coverage
    }
}
