package de.robv.android.xposed.callbacks;

/**
 * Local compileOnly stub of {@code de.robv.android.xposed.callbacks.XC_LoadPackage}.
 * See {@link de.robv.android.xposed.IXposedHookLoadPackage} for why this stub exists.
 */
public class XC_LoadPackage {

    /** Stub of {@code XC_LoadPackage.LoadPackageParam}. */
    public static class LoadPackageParam {
        /** Package name of the loaded process (e.g. {@code android} for system_server). */
        public String packageName;
        /** Class loader of the loaded package; used to resolve hook targets. */
        public ClassLoader classLoader;
    }
}
