package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * Plain-JUnit tests for {@link CarOutputDeviceSelector}.
 */
public class CarOutputDeviceSelectorTest {

    @Test
    public void nullArrayReturnsNoneIndex() {
        assertEquals(CarOutputDeviceSelector.NONE_INDEX,
                CarOutputDeviceSelector.firstCarDeviceIndex(null));
    }

    @Test
    public void emptyArrayReturnsNoneIndex() {
        assertEquals(CarOutputDeviceSelector.NONE_INDEX,
                CarOutputDeviceSelector.firstCarDeviceIndex(new int[0]));
    }

    @Test
    public void noCarDevicesReturnsNoneIndex() {
        // 2 (builtin speaker), 3 (wired headset) — neither is a car sink.
        assertEquals(CarOutputDeviceSelector.NONE_INDEX,
                CarOutputDeviceSelector.firstCarDeviceIndex(new int[]{2, 3}));
    }

    @Test
    public void picksFirstBtA2dp() {
        assertEquals(0, CarOutputDeviceSelector.firstCarDeviceIndex(
                new int[]{AudioDeviceTypeMapper.TYPE_BLUETOOTH_A2DP, 2}));
    }

    @Test
    public void picksFirstUsbWhenEarlierAreNonCar() {
        assertEquals(2, CarOutputDeviceSelector.firstCarDeviceIndex(
                new int[]{2, 3, AudioDeviceTypeMapper.TYPE_USB_DEVICE}));
    }

    @Test
    public void picksTheEarliestCarDeviceWhenMultiple() {
        assertEquals(1, CarOutputDeviceSelector.firstCarDeviceIndex(new int[]{
                2,
                AudioDeviceTypeMapper.TYPE_USB_HEADSET,
                AudioDeviceTypeMapper.TYPE_BLUETOOTH_A2DP}));
    }

    @Test
    public void constructorIsPrivate() throws Exception {
        Constructor<CarOutputDeviceSelector> c =
                CarOutputDeviceSelector.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(c.getModifiers()));
        c.setAccessible(true);
        c.newInstance(); // exercise the private ctor for coverage
    }
}
