package de.robv.android.xposed;

/**
 * Local compileOnly stub of {@code de.robv.android.xposed.XC_MethodHook}.
 * See {@link de.robv.android.xposed.IXposedHookLoadPackage} for why this stub exists.
 */
public abstract class XC_MethodHook {

    /** Override to run before the hooked method. */
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    /** Override to run after the hooked method. */
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    /** Stub of {@code XC_MethodHook.MethodHookParam}. */
    public static class MethodHookParam {
        /** The arguments passed to the hooked method. */
        public Object[] args;

        /** Returns the hooked call's arguments. */
        public Object[] getArgs() {
            return args;
        }

        /** Overrides the return value and skips the original method. */
        public void setResult(Object result) {
        }

        /** Returns the current (possibly already-overridden) result. */
        public Object getResult() {
            return null;
        }

        /** Returns the throwable the method raised, if any. */
        public Throwable getThrowable() {
            return null;
        }
    }
}
