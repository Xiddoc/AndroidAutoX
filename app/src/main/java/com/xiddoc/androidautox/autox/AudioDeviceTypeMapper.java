package com.xiddoc.androidautox.autox;

import com.xiddoc.androidautox.autox.AudioRoutePolicy.CarAudioDevice;

/**
 * Pure helper that maps an Android {@code AudioDeviceInfo} <em>type</em> integer onto the
 * framework-free {@link AudioRoutePolicy.CarAudioDevice} enum consumed by the WS6 routing
 * policy.
 *
 * <h2>Why this exists (and is pure)</h2>
 * <p>The WS6 glue ({@code AutoXScreen}) inspects the live car output device via
 * {@code AudioManager.getDevices(GET_DEVICES_OUTPUTS)} and must translate each device's
 * {@code AudioDeviceInfo.getType()} into a {@link CarAudioDevice} so the pure
 * {@link AudioRoutePolicy#decide} can run. That translation is an enum mapping with several
 * branches — exactly the kind of decision the AutoX coverage rules require to live in a pure,
 * fully-tested class rather than buried in excluded glue.
 *
 * <p>The {@code AudioDeviceInfo.TYPE_*} constants are stable platform integers; their numeric
 * values are referenced here directly (no Android imports) so the class stays JVM-unit-testable.
 * The values match {@code android.media.AudioDeviceInfo} on API 31–34:
 * <ul>
 *   <li>{@code TYPE_BLUETOOTH_A2DP} = 8 → {@link CarAudioDevice#BT_A2DP}</li>
 *   <li>{@code TYPE_USB_DEVICE} = 11, {@code TYPE_USB_ACCESSORY} = 12,
 *       {@code TYPE_USB_HEADSET} = 22 → {@link CarAudioDevice#USB}</li>
 *   <li>anything else → {@link CarAudioDevice#NONE} (not a car audio sink AutoX routes to)</li>
 * </ul>
 *
 * <p>// TODO(device-verify): confirm on a real head unit which {@code AudioDeviceInfo} type the
 * car's audio bus actually reports — some USB head units enumerate as
 * {@code TYPE_USB_ACCESSORY}, others as {@code TYPE_USB_DEVICE}/{@code TYPE_USB_HEADSET}; all
 * three are mapped to USB here so the policy can route regardless, but the precise type can
 * only be observed on-device.
 */
public final class AudioDeviceTypeMapper {

    /** {@code AudioDeviceInfo.TYPE_BLUETOOTH_A2DP}. */
    public static final int TYPE_BLUETOOTH_A2DP = 8;

    /** {@code AudioDeviceInfo.TYPE_USB_DEVICE}. */
    public static final int TYPE_USB_DEVICE = 11;

    /** {@code AudioDeviceInfo.TYPE_USB_ACCESSORY}. */
    public static final int TYPE_USB_ACCESSORY = 12;

    /** {@code AudioDeviceInfo.TYPE_USB_HEADSET}. */
    public static final int TYPE_USB_HEADSET = 22;

    private AudioDeviceTypeMapper() {
        // Static utility class; prevent instantiation.
    }

    /**
     * Maps an {@code AudioDeviceInfo} type integer to a {@link CarAudioDevice}.
     *
     * @param audioDeviceInfoType the value of {@code AudioDeviceInfo.getType()}
     * @return {@link CarAudioDevice#BT_A2DP} for Bluetooth A2DP, {@link CarAudioDevice#USB} for
     *         any USB audio type, otherwise {@link CarAudioDevice#NONE}
     */
    public static CarAudioDevice fromAudioDeviceInfoType(int audioDeviceInfoType) {
        switch (audioDeviceInfoType) {
            case TYPE_BLUETOOTH_A2DP:
                return CarAudioDevice.BT_A2DP;
            case TYPE_USB_DEVICE:
            case TYPE_USB_ACCESSORY:
            case TYPE_USB_HEADSET:
                return CarAudioDevice.USB;
            default:
                return CarAudioDevice.NONE;
        }
    }
}
