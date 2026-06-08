package com.xiddoc.androidautox.autox;

import com.xiddoc.androidautox.autox.AudioRoutePolicy.CarAudioDevice;

/**
 * Pure first-match selection of a car audio output device from a list of
 * {@code AudioDeviceInfo.getType()} integers.
 *
 * <p>The WS6 glue ({@code AutoXScreen}) enumerates the live output devices via
 * {@code AudioManager.getDevices(GET_DEVICES_OUTPUTS)} and must pick the first one that maps
 * (via {@link AudioDeviceTypeMapper}) to a real car sink (BT A2DP / USB). That first-match
 * loop is a decision, so per the AutoX "no decisions in excluded glue" rule it lives here,
 * framework-free and 100% unit-tested; the glue only resolves the {@code int} types and reads
 * the address off the chosen device.
 */
public final class CarOutputDeviceSelector {

    /** Returned by {@link #firstCarDeviceIndex} when no device maps to a car sink. */
    public static final int NONE_INDEX = -1;

    private CarOutputDeviceSelector() {
        // Static utility class; prevent instantiation.
    }

    /**
     * Returns the index of the first device type that maps to a {@link CarAudioDevice} other
     * than {@link CarAudioDevice#NONE}.
     *
     * @param deviceTypes the {@code AudioDeviceInfo.getType()} values, in enumeration order;
     *                    may be null or empty
     * @return the index of the first car-output device, or {@link #NONE_INDEX} if none match
     *         (including when {@code deviceTypes} is null or empty)
     */
    public static int firstCarDeviceIndex(int[] deviceTypes) {
        if (deviceTypes == null) {
            return NONE_INDEX;
        }
        for (int i = 0; i < deviceTypes.length; i++) {
            if (AudioDeviceTypeMapper.fromAudioDeviceInfoType(deviceTypes[i])
                    != CarAudioDevice.NONE) {
                return i;
            }
        }
        return NONE_INDEX;
    }
}
