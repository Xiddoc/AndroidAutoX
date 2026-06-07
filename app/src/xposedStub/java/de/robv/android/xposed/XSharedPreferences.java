package de.robv.android.xposed;

/**
 * Local compileOnly stub of {@code de.robv.android.xposed.XSharedPreferences}.
 * See {@link de.robv.android.xposed.IXposedHookLoadPackage} for why this stub exists.
 *
 * <p>Only the small surface the WS4 module glue uses is modeled.
 */
public class XSharedPreferences {

    /** Real ctor opens the module package's prefs file by name. */
    public XSharedPreferences(String packageName, String prefFileName) {
    }

    /** Re-reads the backing file if it changed (the IPC poll). */
    public boolean hasFileChanged() {
        return false;
    }

    /** Forces a reload of the backing file. */
    public void reload() {
    }

    /** Returns a string pref, or {@code defValue} if absent. */
    public String getString(String key, String defValue) {
        return defValue;
    }

    /** Returns a boolean pref, or {@code defValue} if absent. */
    public boolean getBoolean(String key, boolean defValue) {
        return defValue;
    }
}
