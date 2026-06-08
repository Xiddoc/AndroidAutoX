package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;

import com.xiddoc.androidautox.autox.AudioRoutePolicy.CarAudioDevice;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * Exhaustive plain-JUnit tests for {@link AudioDeviceTypeMapper}: the BT_A2DP branch, all three
 * USB branches, the default (NONE) branch including representative non-car types, and the
 * private-constructor guard.
 */
public class AudioDeviceTypeMapperTest {

    @Test
    public void bluetoothA2dpMapsToBtA2dp() {
        assertEquals(CarAudioDevice.BT_A2DP,
                AudioDeviceTypeMapper.fromAudioDeviceInfoType(
                        AudioDeviceTypeMapper.TYPE_BLUETOOTH_A2DP));
    }

    @Test
    public void usbDeviceMapsToUsb() {
        assertEquals(CarAudioDevice.USB,
                AudioDeviceTypeMapper.fromAudioDeviceInfoType(
                        AudioDeviceTypeMapper.TYPE_USB_DEVICE));
    }

    @Test
    public void usbAccessoryMapsToUsb() {
        assertEquals(CarAudioDevice.USB,
                AudioDeviceTypeMapper.fromAudioDeviceInfoType(
                        AudioDeviceTypeMapper.TYPE_USB_ACCESSORY));
    }

    @Test
    public void usbHeadsetMapsToUsb() {
        assertEquals(CarAudioDevice.USB,
                AudioDeviceTypeMapper.fromAudioDeviceInfoType(
                        AudioDeviceTypeMapper.TYPE_USB_HEADSET));
    }

    @Test
    public void unknownTypeMapsToNone() {
        // TYPE_BUILTIN_SPEAKER = 2, TYPE_WIRED_HEADPHONES = 4 — phone-side sinks, not car.
        assertEquals(CarAudioDevice.NONE, AudioDeviceTypeMapper.fromAudioDeviceInfoType(2));
        assertEquals(CarAudioDevice.NONE, AudioDeviceTypeMapper.fromAudioDeviceInfoType(4));
    }

    @Test
    public void zeroAndNegativeMapToNone() {
        assertEquals(CarAudioDevice.NONE, AudioDeviceTypeMapper.fromAudioDeviceInfoType(0));
        assertEquals(CarAudioDevice.NONE, AudioDeviceTypeMapper.fromAudioDeviceInfoType(-1));
        assertEquals(CarAudioDevice.NONE,
                AudioDeviceTypeMapper.fromAudioDeviceInfoType(Integer.MAX_VALUE));
    }

    @Test
    public void constructorIsPrivate() throws Exception {
        Constructor<AudioDeviceTypeMapper> c =
                AudioDeviceTypeMapper.class.getDeclaredConstructor();
        assertEquals(true, Modifier.isPrivate(c.getModifiers()));
        c.setAccessible(true);
        c.newInstance(); // exercise the private ctor for coverage
    }
}
