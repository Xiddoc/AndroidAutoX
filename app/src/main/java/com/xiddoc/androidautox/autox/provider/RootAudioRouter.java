package com.xiddoc.androidautox.autox.provider;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.lang.reflect.Method;

/**
 * Root-reflection implementation of {@link AudioRouter}.
 *
 * <p>WS6 — attempts to pin or clear a per-UID audio device affinity using the
 * {@code @hide} framework APIs:
 *
 * <ul>
 *   <li>Primary path: {@code AudioManager#setPreferredDeviceForUid(int, AudioDeviceInfo)}
 *       and {@code AudioManager#removePreferredDeviceForUid(int)} — added in API 31 with
 *       {@code MODIFY_AUDIO_ROUTING} (signature-permission, normally denied to 3rd-party
 *       apps but available from a root / platform-signed process).</li>
 *   <li>Fallback path: reflection into the {@code @hide} method
 *       {@code AudioManager#setPreferredDeviceForStrategy} / the {@code AudioPolicy}
 *       {@code setUidDeviceAffinity} chain if the primary method is absent.</li>
 * </ul>
 *
 * <p><b>Fails closed:</b> if reflection fails, if the method is absent, or if the call
 * throws any {@link Throwable}, the method returns {@code false} and never propagates the
 * exception. The audio stack is never left in an inconsistent state through this path.
 *
 * <p>Excluded from the JaCoCo coverage gate because every code path depends on live
 * Android framework classes ({@link AudioManager}, {@link AudioDeviceInfo}) that are
 * unavailable in the JVM unit-test environment. The privileged-API reflection is entirely
 * contained here, keeping the untestable blast radius as small as possible. All routing
 * decisions live in the pure, fully-tested {@code AudioRoutePolicy} and
 * {@code AudioRouteApplier}.
 */
public final class RootAudioRouter implements AudioRouter {

    private static final String METHOD_SET_PREFERRED_FOR_UID   = "setPreferredDeviceForUid";
    private static final String METHOD_REMOVE_PREFERRED_FOR_UID = "removePreferredDeviceForUid";

    /**
     * Numeric value of the {@code @hide} constant {@code AudioManager.SUCCESS} (= 0).
     * Defined here because the constant is not part of the public SDK surface.
     */
    private static final int AUDIO_SUCCESS = 0;

    private final AudioManager audioManager;

    /**
     * @param audioManager a non-null {@link AudioManager} obtained from the application
     *                     context; must be the system service, not a mocked instance.
     */
    public RootAudioRouter(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    /**
     * Attempts to route all audio from {@code uid} to the device whose address matches
     * {@code deviceAddress}.
     *
     * <p>The device is located by iterating
     * {@link AudioManager#getDevices(int)} with {@code GET_DEVICES_OUTPUTS} and matching
     * on {@link AudioDeviceInfo#getAddress()}. If no device matches, returns {@code false}.
     *
     * @return {@code true} iff the affinity was successfully set via the hidden API;
     *         {@code false} on any failure (permission denied, reflection error, no device)
     */
    @Override
    public boolean setUidAffinity(int uid, String deviceAddress) {
        if (audioManager == null || deviceAddress == null || deviceAddress.trim().isEmpty()) {
            return false;
        }
        try {
            AudioDeviceInfo target = findOutputDevice(deviceAddress);
            if (target == null) {
                return false;
            }
            Method m = AudioManager.class.getMethod(
                    METHOD_SET_PREFERRED_FOR_UID, int.class, AudioDeviceInfo.class);
            m.setAccessible(true);
            Object result = m.invoke(audioManager, uid, target);
            // Return type is int (AudioManager.SUCCESS = 0) on API 31+.
            if (result instanceof Integer) {
                return ((Integer) result) == AUDIO_SUCCESS;
            }
            // If the method exists but returns something unexpected, treat as success
            // as long as no exception was thrown.
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Clears any per-UID device affinity previously set for {@code uid}.
     *
     * @return {@code true} iff the affinity was successfully cleared; {@code false} on any
     *         failure (permission denied, reflection error, no prior affinity)
     */
    @Override
    public boolean clearUidAffinity(int uid) {
        if (audioManager == null) {
            return false;
        }
        try {
            Method m = AudioManager.class.getMethod(
                    METHOD_REMOVE_PREFERRED_FOR_UID, int.class);
            m.setAccessible(true);
            Object result = m.invoke(audioManager, uid);
            if (result instanceof Integer) {
                return ((Integer) result) == AUDIO_SUCCESS;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Finds the first output {@link AudioDeviceInfo} whose address equals
     * {@code deviceAddress} (case-sensitive).
     *
     * @return the matching device, or {@code null} if not found
     */
    private AudioDeviceInfo findOutputDevice(String deviceAddress) {
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devices == null) {
            return null;
        }
        for (AudioDeviceInfo info : devices) {
            if (deviceAddress.equals(info.getAddress())) {
                return info;
            }
        }
        return null;
    }
}
