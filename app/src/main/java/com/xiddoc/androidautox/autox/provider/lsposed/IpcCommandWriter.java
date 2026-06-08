package com.xiddoc.androidautox.autox.provider.lsposed;

import android.content.Context;
import android.content.SharedPreferences;

import com.xiddoc.androidautox.autox.provider.IpcCommand;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EXCLUDED GLUE: the app-side writer that publishes {@link IpcCommand}s into the shared-prefs
 * channel the {@link AutoXXposedModule} reads via {@code XSharedPreferences}. Touches the
 * Android {@link Context}/{@link SharedPreferences} API and the (deprecated, security-sensitive)
 * world-readable prefs mode, so it cannot be meaningfully unit-tested off-device; it is listed
 * in {@code jacocoExclusions}. All command construction/validation is delegated to the pure,
 * 100%-tested {@link IpcCommand}.
 *
 * <h2>Channel contract (must match {@link AutoXXposedModule})</h2>
 * <ul>
 *   <li><b>Prefs file name:</b> {@link AutoXXposedModule#IPC_PREFS_FILE}
 *       ({@code "autox_lsposed_ipc"}).</li>
 *   <li><b>Key:</b> {@link AutoXXposedModule#IPC_PREFS_KEY} ({@code "commands"}).</li>
 *   <li><b>Value:</b> newline-separated {@link IpcCommand#encode() encoded} commands, one per
 *       line. The module splits on {@code "\n"}, {@link IpcCommand#decode(String) decodes} each
 *       line, and skips malformed lines.</li>
 * </ul>
 *
 * <h2>World-readable shared-prefs requirement (READ THIS)</h2>
 * <p>The module runs inside {@code system_server} (a <em>different</em> process and UID from the
 * app). It reads the channel with {@code XSharedPreferences}, which opens the app's prefs
 * <b>file directly off disk</b> — it does <em>not</em> go through the app's
 * {@code SharedPreferences} cache or any IPC. For {@code system_server} to be able to read that
 * file, the file must be <b>world-readable</b>. There are two ways to satisfy this, in order of
 * preference:
 * <ol>
 *   <li><b>LSPosed remote-prefs mechanism (preferred):</b> when the app is itself an active
 *       LSPosed module, LSPosed intercepts {@code getSharedPreferences(name, MODE_WORLD_READABLE)}
 *       and routes writes through its own world-readable storage that {@code XSharedPreferences}
 *       can read, side-stepping the SELinux/{@code targetSdk>=24} restrictions on raw
 *       world-readable files. This is the mechanism intended for production.</li>
 *   <li><b>Raw {@code MODE_WORLD_READABLE} (fallback):</b> on a rooted device the app's data
 *       dir + prefs file can be {@code chmod}'d so {@code system_server} can read it. Note that
 *       since {@code targetSdk 24} the platform throws {@link SecurityException} from
 *       {@code MODE_WORLD_READABLE} unless the LSPosed hook above neutralises the check, hence
 *       the preference for option (1).</li>
 * </ol>
 * <p>This writer always requests {@code MODE_WORLD_READABLE} so that, when the app is loaded as
 * an LSPosed module, the LSPosed hook makes the file readable from {@code system_server}. The
 * write is wrapped fail-closed: any {@link SecurityException} / {@link Throwable} from the
 * platform is swallowed and surfaced as a {@code false} return rather than crashing the app.
 *
 * <h2>State model</h2>
 * <p>The channel is a <b>set of currently-active commands</b>, persisted as the union of:
 * one optional {@link IpcCommand.Type#ENABLE_TRUSTED_DISPLAY} command, plus at most one
 * display-scoped command of each display-scoped type ({@link IpcCommand.Type#ALLOW_INPUT_INJECTION},
 * {@link IpcCommand.Type#SET_DISPLAY_IME}, {@link IpcCommand.Type#LAUNCH_ON_DISPLAY}) keyed by the
 * AutoX display id. Each {@code enable*}/{@code set*}/{@code launch*} call rewrites the channel so
 * the module always sees the latest scoping; {@link #clear()} empties it on AutoX teardown.
 */
@SuppressWarnings("deprecation") // MODE_WORLD_READABLE is intentional — see class Javadoc.
public final class IpcCommandWriter {

    private final Context context;

    /**
     * @param context any Android {@link Context} owned by the app process (e.g. the application
     *                context). Must not be null.
     */
    public IpcCommandWriter(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.context = context;
    }

    /**
     * Enables the trusted-display hook (AutoX displays keep {@code VIRTUAL_DISPLAY_FLAG_TRUSTED}).
     *
     * <p>This command is scoped by display <b>name</b> in the module (the display id does not
     * exist yet at {@code createVirtualDisplay} time), so it carries no display id.
     *
     * @return {@code true} if the channel was written
     */
    public boolean enableTrustedDisplay() {
        return upsert(IpcCommand.Type.ENABLE_TRUSTED_DISPLAY,
                IpcCommand.of(IpcCommand.Type.ENABLE_TRUSTED_DISPLAY));
    }

    /**
     * Allows {@code injectInputEvent} to target the given AutoX display.
     *
     * @param displayId the AutoX virtual-display id ({@code >= 0})
     * @return {@code true} if the channel was written
     */
    public boolean allowInputInjection(int displayId) {
        return upsert(IpcCommand.Type.ALLOW_INPUT_INJECTION,
                IpcCommand.forDisplay(IpcCommand.Type.ALLOW_INPUT_INJECTION, displayId));
    }

    /**
     * Sets whether the IME and system decors may show on the given AutoX display.
     *
     * <p>When {@code enabled} is {@code true} the module forces {@code shouldShowImeLocked} and
     * {@code shouldShowSystemDecorsLocked} to {@code true} for this display id. When
     * {@code false}, the command is removed so the platform's own decision stands again.
     *
     * @param displayId the AutoX virtual-display id ({@code >= 0})
     * @param enabled   {@code true} to enable IME/decors on the display, {@code false} to revert
     * @return {@code true} if the channel was written
     */
    public boolean setDisplayImeAndDecors(int displayId, boolean enabled) {
        if (!enabled) {
            return removeType(IpcCommand.Type.SET_DISPLAY_IME);
        }
        return upsert(IpcCommand.Type.SET_DISPLAY_IME,
                IpcCommand.forDisplay(IpcCommand.Type.SET_DISPLAY_IME, displayId));
    }

    /**
     * Permits launching {@code pkg} on the given AutoX display (relaxes the launch-on-display
     * caller check in {@code ActivityTaskManagerService}).
     *
     * @param displayId the AutoX virtual-display id ({@code >= 0})
     * @param pkg       the guest app package to launch; must not be null/blank or contain a
     *                  reserved separator ({@code | ; =})
     * @return {@code true} if the channel was written
     */
    public boolean launchOnDisplay(int displayId, String pkg) {
        Map<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("pkg", pkg);
        return upsert(IpcCommand.Type.LAUNCH_ON_DISPLAY,
                IpcCommand.forDisplay(IpcCommand.Type.LAUNCH_ON_DISPLAY, displayId, extra));
    }

    /**
     * Clears every command from the channel (AutoX disabled / session torn down). After this the
     * module sees an empty blob and every gate returns "not enabled", so the platform behaviour
     * is fully restored.
     *
     * @return {@code true} if the channel was cleared
     */
    public boolean clear() {
        try {
            prefs().edit().putString(AutoXXposedModule.IPC_PREFS_KEY, "").commit();
            return true;
        } catch (Throwable t) {
            return false; // fail closed — never crash the app on a prefs write
        }
    }

    /**
     * Replaces any existing command of {@code type} with {@code replacement} and persists the
     * full channel. Returns false (fail-closed) on any platform error.
     */
    private boolean upsert(IpcCommand.Type type, IpcCommand replacement) {
        try {
            Map<IpcCommand.Type, IpcCommand> current = readCommands();
            current.put(type, replacement);
            return write(current);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Removes any command of {@code type} and persists the channel. */
    private boolean removeType(IpcCommand.Type type) {
        try {
            Map<IpcCommand.Type, IpcCommand> current = readCommands();
            current.remove(type);
            return write(current);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Parses the current channel into a type-keyed map (malformed lines are skipped). */
    private Map<IpcCommand.Type, IpcCommand> readCommands() {
        Map<IpcCommand.Type, IpcCommand> out =
                new LinkedHashMap<IpcCommand.Type, IpcCommand>();
        String blob = prefs().getString(AutoXXposedModule.IPC_PREFS_KEY, "");
        if (blob == null || blob.isEmpty()) {
            return out;
        }
        for (String line : blob.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            try {
                IpcCommand cmd = IpcCommand.decode(line);
                out.put(cmd.getType(), cmd);
            } catch (IllegalArgumentException ignored) {
                // skip malformed line
            }
        }
        return out;
    }

    /** Encodes the command map (newline-separated) and commits it to the world-readable file. */
    private boolean write(Map<IpcCommand.Type, IpcCommand> commands) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (IpcCommand cmd : commands.values()) {
            if (!first) {
                sb.append('\n');
            }
            sb.append(cmd.encode());
            first = false;
        }
        return prefs().edit()
                .putString(AutoXXposedModule.IPC_PREFS_KEY, sb.toString())
                .commit();
    }

    /**
     * Opens the channel prefs file in {@link Context#MODE_WORLD_READABLE} (see class Javadoc).
     * When the app is loaded as an LSPosed module, LSPosed's hook makes the resulting file
     * readable from {@code system_server} via {@code XSharedPreferences}.
     */
    private SharedPreferences prefs() {
        return context.getSharedPreferences(
                AutoXXposedModule.IPC_PREFS_FILE, Context.MODE_WORLD_READABLE);
    }
}
