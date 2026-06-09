package com.xiddoc.androidautox.autox.provider.lsposed;

import de.robv.android.xposed.XposedBridge;

/**
 * EXCLUDED GLUE: thin, consistent debug-logging facade for the AutoX hooks that run inside
 * {@code system_server}.
 *
 * <h2>Why a separate sink from {@link com.xiddoc.androidautox.autox.AutoXLog}</h2>
 * <p>The LSPosed bridges ({@link AutoXXposedModule}, {@link TrustedFlagBridge},
 * {@link InputInjectionBridge}) execute in the {@code system_server} process, NOT the AutoX app
 * process — so the app-side {@link com.xiddoc.androidautox.autox.AutoXLog} ring buffer is
 * unreachable from here. The only log sink that reliably surfaces from a system_server hook is
 * {@link XposedBridge#log}, whose output is viewable in the LSPosed Manager app's log screen (and
 * in {@code logcat}). Every line is prefixed so a tester can grep the LSPosed log for
 * {@value #PREFIX} and see exactly which hidden-API guess (display-id field, arg position, mode
 * relax) was exercised — and, on failure, the captured throwable — when AutoX is tried on a real
 * device. These are the {@code TODO(device-verify)} paths most likely to break first.
 *
 * <h2>Failure semantics</h2>
 * <p>Logging must NEVER be the thing that throws out of a {@code system_server} hook (that could
 * bootloop the device), so every method here swallows any error from the sink itself.
 *
 * <p>This class is irreducible Xposed glue (it depends on the {@code compileOnly} Xposed API that
 * is absent from the unit-test runtime) and is listed in {@code jacocoExclusions}.
 */
final class XposedDebug {

    /** Grep handle for the LSPosed/logcat log: {@code XposedBridge.log} lines all start with this. */
    static final String PREFIX = "AutoX/sys";

    /**
     * Master switch for verbose hook tracing. Left {@code true} while the privileged paths are
     * device-validation pending so the first on-device runs are fully traced; flip to {@code false}
     * once the {@code TODO(device-verify)} markers are resolved and the noise is no longer wanted.
     */
    static final boolean VERBOSE = true;

    private XposedDebug() {
    }

    /** Logs a verbose trace line (suppressed when {@link #VERBOSE} is off). */
    static void v(String area, String msg) {
        if (!VERBOSE) {
            return;
        }
        emit(PREFIX + "/" + area + ": " + msg);
    }

    /** Logs an always-on milestone line (hook installed, gate fired, mutation applied/skipped). */
    static void i(String area, String msg) {
        emit(PREFIX + "/" + area + ": " + msg);
    }

    /** Logs a message plus the captured throwable (fail-closed paths). */
    static void e(String area, String msg, Throwable t) {
        emit(PREFIX + "/" + area + ": " + msg);
        if (t != null) {
            try {
                XposedBridge.log(t);
            } catch (Throwable ignored) {
                // never let logging crash a system_server hook
            }
        }
    }

    private static void emit(String line) {
        try {
            XposedBridge.log(line);
        } catch (Throwable ignored) {
            // never let logging crash a system_server hook
        }
    }
}
