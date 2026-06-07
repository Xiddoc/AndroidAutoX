package de.robv.android.xposed;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Local compileOnly stub of {@code de.robv.android.xposed.IXposedHookLoadPackage}.
 *
 * <p>This is NOT the real Xposed API — it exists only so the WS4 LSPosed glue compiles in
 * an offline sandbox where the {@code de.robv.android.xposed:api:82} artifact cannot be
 * resolved. It is wired in as a compileOnly source set ({@code -PuseXposedStub=true}); the
 * real implementations are provided by the LSPosed framework at runtime and nothing from
 * this stub is bundled into the APK.
 */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
