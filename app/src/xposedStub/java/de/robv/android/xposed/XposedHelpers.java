package de.robv.android.xposed;

/**
 * Local compileOnly stub of {@code de.robv.android.xposed.XposedHelpers}.
 * See {@link de.robv.android.xposed.IXposedHookLoadPackage} for why this stub exists.
 */
public final class XposedHelpers {

    private XposedHelpers() {
    }

    /**
     * Stub of {@code findAndHookMethod}. The real method resolves {@code className} via the
     * class loader, finds the method by name + parameter types (the trailing varargs end
     * with the {@link XC_MethodHook} callback) and installs the hook.
     */
    public static Object findAndHookMethod(String className, ClassLoader classLoader,
                                           String methodName, Object... parameterTypesAndCallback) {
        return null;
    }
}
