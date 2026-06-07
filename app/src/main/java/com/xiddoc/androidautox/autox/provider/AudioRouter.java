package com.xiddoc.androidautox.autox.provider;

/**
 * Privileged-provider seam for routing a guest app's audio to a specific output device
 * by setting (or clearing) a per-UID device affinity.
 *
 * <p>WS6 (audio routing for projected apps) depends on this interface. The underlying
 * mechanism — {@code AudioManager#setPreferredDeviceForStrategy} / the {@code @hide}
 * {@code setUidDeviceAffinity} on {@code AudioPolicy}, or an LSPosed hook in the audio
 * service — is hidden behind the seam. Kept deliberately minimal: WS6 only needs to bind
 * a UID to a device and later release it.
 *
 * <p>Best-effort and non-throwing: methods return {@code false} when the privilege is
 * absent rather than raising {@link SecurityException}.
 */
public interface AudioRouter {

    /**
     * Pins all audio produced by {@code uid} to the audio output device identified by
     * {@code deviceAddress}.
     *
     * @param uid           the application UID whose audio should be routed
     * @param deviceAddress the target output device address (e.g. a bus address for a
     *                      car head-unit audio bus); must not be null/blank
     * @return {@code true} if the affinity was set
     */
    boolean setUidAffinity(int uid, String deviceAddress);

    /**
     * Clears any per-UID device affinity previously set for {@code uid}, returning its
     * audio to default routing.
     *
     * @param uid the application UID whose affinity should be removed
     * @return {@code true} if an affinity was cleared (or none was present)
     */
    boolean clearUidAffinity(int uid);
}
