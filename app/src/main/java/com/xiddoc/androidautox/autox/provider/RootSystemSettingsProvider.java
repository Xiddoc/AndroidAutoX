package com.xiddoc.androidautox.autox.provider;

import android.content.ContentResolver;
import android.provider.Settings;
import android.util.Log;

/**
 * EXCLUDED GLUE: best-effort {@link SystemSettingsProvider} backed by the platform
 * {@code Settings.Global}/{@code Settings.Secure} APIs. On a rooted / platform-signed
 * process the writes succeed; otherwise they throw {@link SecurityException}, which this
 * class catches and reports as {@link SettingsResult#denied()} (it never throws).
 *
 * <p>All decision/result modelling lives in the pure {@link SettingsResult}; this class is
 * only the Android-API plumbing, so it is listed in {@code jacocoExclusions} and cannot be
 * meaningfully unit-tested off-device.
 */
public final class RootSystemSettingsProvider implements SystemSettingsProvider {

    private static final String TAG = "AndroidAutoX";

    private final ContentResolver resolver;

    /**
     * @param resolver the content resolver to read/write settings through; obtain via
     *                 {@code context.getContentResolver()}
     */
    public RootSystemSettingsProvider(ContentResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        this.resolver = resolver;
    }

    @Override
    public SettingsResult putGlobalInt(String key, int value) {
        try {
            boolean ok = Settings.Global.putInt(resolver, key, value);
            return ok ? SettingsResult.ok() : SettingsResult.denied();
        } catch (SecurityException e) {
            Log.w(TAG, "putGlobalInt(" + key + ") denied", e);
            return SettingsResult.denied();
        }
    }

    @Override
    public SettingsResult getGlobalInt(String key) {
        try {
            return SettingsResult.ok(Settings.Global.getInt(resolver, key));
        } catch (Settings.SettingNotFoundException e) {
            return SettingsResult.notFound();
        } catch (SecurityException e) {
            Log.w(TAG, "getGlobalInt(" + key + ") denied", e);
            return SettingsResult.denied();
        }
    }

    @Override
    public SettingsResult putSecureInt(String key, int value) {
        try {
            boolean ok = Settings.Secure.putInt(resolver, key, value);
            return ok ? SettingsResult.ok() : SettingsResult.denied();
        } catch (SecurityException e) {
            Log.w(TAG, "putSecureInt(" + key + ") denied", e);
            return SettingsResult.denied();
        }
    }

    @Override
    public SettingsResult getSecureInt(String key) {
        try {
            return SettingsResult.ok(Settings.Secure.getInt(resolver, key));
        } catch (Settings.SettingNotFoundException e) {
            return SettingsResult.notFound();
        } catch (SecurityException e) {
            Log.w(TAG, "getSecureInt(" + key + ") denied", e);
            return SettingsResult.denied();
        }
    }
}
