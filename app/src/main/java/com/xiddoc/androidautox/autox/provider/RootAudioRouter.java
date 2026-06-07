package com.xiddoc.androidautox.autox.provider;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Root-reflection implementation of {@link AudioRouter}.
 *
 * <h2>WS6 — per-UID device affinity via {@code AudioPolicy}</h2>
 * <p>An earlier draft of this class targeted {@code AudioManager#setPreferredDeviceForUid},
 * but that method <b>does not exist</b> on API 31–34 — it was never added to
 * {@code AudioManager}. The supported hidden path for pinning a UID's output to a specific
 * device on these API levels is the {@code @hide} {@code AudioPolicy} API:
 *
 * <ul>
 *   <li>Build an {@code android.media.audiopolicy.AudioPolicy} via its hidden
 *       {@code AudioPolicy.Builder(Context)} and register it with
 *       {@code AudioManager#registerAudioPolicy(AudioPolicy)}.</li>
 *   <li>Pin a UID with
 *       {@code AudioPolicy#setUidDeviceAffinity(int uid, List<AudioDeviceInfo> devices)}.</li>
 *   <li>Clear a UID with {@code AudioPolicy#removeUidDeviceAffinity(int uid)}.</li>
 * </ul>
 *
 * <p>All of these are {@code @hide}/{@code @SystemApi} and require
 * {@code MODIFY_AUDIO_ROUTING} (signature permission), which is only obtainable from a
 * root / platform-signed process. Every call is made through best-effort reflection.
 *
 * <p><b>Fails closed:</b> if any reflection step fails, if a class/method is absent, if the
 * call throws, or if a reflective return value has an <b>unknown</b> type that cannot be
 * positively interpreted as success, the method returns {@code false} and never propagates
 * the exception. An {@code int} return is interpreted as the documented
 * {@code AudioManager.SUCCESS} (= 0) contract; anything else (including {@code null} or a
 * non-{@code Integer} object) is treated as <b>failure</b>.
 *
 * <p>// TODO(device-verify): confirm on a real rooted API 31–34 device the exact
 * {@code AudioPolicy.Builder} construction (some builds require
 * {@code setIsAudioFocusPolicy}/{@code setLooper} before {@code build()}), that an empty
 * policy can be registered, and the concrete return contract of
 * {@code setUidDeviceAffinity}/{@code removeUidDeviceAffinity} (int SUCCESS vs void).
 *
 * <p>Excluded from the JaCoCo coverage gate because every code path depends on live
 * Android framework classes ({@link AudioManager}, {@link AudioDeviceInfo}, and the hidden
 * {@code AudioPolicy}) that are unavailable in the JVM unit-test environment. All routing
 * <em>decisions</em> live in the pure, fully-tested {@code AudioRoutePolicy} /
 * {@code AudioRouteApplier}; this class only performs the privileged framework calls.
 */
public final class RootAudioRouter implements AudioRouter {

    private static final String AUDIO_POLICY_CLASS = "android.media.audiopolicy.AudioPolicy";
    private static final String AUDIO_POLICY_BUILDER_CLASS =
            "android.media.audiopolicy.AudioPolicy$Builder";

    private static final String METHOD_SET_UID_AFFINITY = "setUidDeviceAffinity";
    private static final String METHOD_REMOVE_UID_AFFINITY = "removeUidDeviceAffinity";
    private static final String METHOD_REGISTER_POLICY = "registerAudioPolicy";

    /**
     * Numeric value of the {@code @hide} constant {@code AudioManager.SUCCESS} (= 0).
     * Defined here because the constant is not part of the public SDK surface.
     */
    private static final int AUDIO_SUCCESS = 0;

    private final Context context;
    private final AudioManager audioManager;

    /** Lazily-built, registered {@code AudioPolicy} instance (reflected). */
    private Object audioPolicy;
    /** {@code true} once {@link #audioPolicy} has been registered with the framework. */
    private boolean policyRegistered;

    /**
     * @param context      a non-null application/service {@link Context} used to build the
     *                     hidden {@code AudioPolicy}
     * @param audioManager a non-null {@link AudioManager} obtained from the system context;
     *                     must be the system service, not a mocked instance
     */
    public RootAudioRouter(Context context, AudioManager audioManager) {
        this.context = context;
        this.audioManager = audioManager;
    }

    /**
     * Attempts to route all audio from {@code uid} to the device whose address matches
     * {@code deviceAddress} using {@code AudioPolicy#setUidDeviceAffinity}.
     *
     * <p>The device is located by iterating {@link AudioManager#getDevices(int)} with
     * {@code GET_DEVICES_OUTPUTS} and matching on {@link AudioDeviceInfo#getAddress()}.
     * If no device matches, returns {@code false}.
     *
     * @return {@code true} iff the affinity was successfully set; {@code false} on any
     *         failure (permission denied, reflection error, no device, unknown return type)
     */
    @Override
    public boolean setUidAffinity(int uid, String deviceAddress) {
        if (context == null || audioManager == null
                || deviceAddress == null || deviceAddress.trim().isEmpty()) {
            return false;
        }
        try {
            AudioDeviceInfo target = findOutputDevice(deviceAddress);
            if (target == null) {
                return false;
            }
            Object policy = ensureRegisteredPolicy();
            if (policy == null) {
                return false;
            }
            List<AudioDeviceInfo> devices = new ArrayList<>(1);
            devices.add(target);
            Method m = policy.getClass().getMethod(
                    METHOD_SET_UID_AFFINITY, int.class, List.class);
            m.setAccessible(true);
            Object result = m.invoke(policy, uid, devices);
            return interpretResult(result);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Clears any per-UID device affinity previously set for {@code uid} via
     * {@code AudioPolicy#removeUidDeviceAffinity}.
     *
     * @return {@code true} iff the affinity was successfully cleared; {@code false} on any
     *         failure (permission denied, reflection error, no prior policy, unknown return)
     */
    @Override
    public boolean clearUidAffinity(int uid) {
        if (context == null || audioManager == null) {
            return false;
        }
        try {
            Object policy = ensureRegisteredPolicy();
            if (policy == null) {
                return false;
            }
            Method m = policy.getClass().getMethod(METHOD_REMOVE_UID_AFFINITY, int.class);
            m.setAccessible(true);
            Object result = m.invoke(policy, uid);
            return interpretResult(result);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Builds (once) and registers a hidden {@code AudioPolicy} via reflection, caching it
     * for reuse. Returns {@code null} on any failure.
     */
    private Object ensureRegisteredPolicy() {
        if (policyRegistered && audioPolicy != null) {
            return audioPolicy;
        }
        try {
            Class<?> builderCls = Class.forName(AUDIO_POLICY_BUILDER_CLASS);
            Object builder = builderCls.getConstructor(Context.class).newInstance(context);
            Method build = builderCls.getMethod("build");
            build.setAccessible(true);
            Object policy = build.invoke(builder);
            if (policy == null) {
                return null;
            }
            Class<?> policyCls = Class.forName(AUDIO_POLICY_CLASS);
            Method register = AudioManager.class.getMethod(METHOD_REGISTER_POLICY, policyCls);
            register.setAccessible(true);
            Object regResult = register.invoke(audioManager, policy);
            // registerAudioPolicy returns an int status (SUCCESS = 0). Any other interpreted
            // result (or unknown type) is a failure — fail closed.
            if (!interpretResult(regResult)) {
                return null;
            }
            audioPolicy = policy;
            policyRegistered = true;
            return audioPolicy;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Interprets a reflective return value against the framework's {@code int SUCCESS}
     * contract. Any value that is not an {@link Integer} equal to {@link #AUDIO_SUCCESS}
     * (including {@code null} and unknown object types) is treated as <b>failure</b>.
     */
    private static boolean interpretResult(Object result) {
        if (result instanceof Integer) {
            return ((Integer) result) == AUDIO_SUCCESS;
        }
        // UNKNOWN reflective return type — fail closed.
        return false;
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
