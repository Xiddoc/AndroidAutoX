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
 * <h2>Write integrity (security)</h2>
 * <p>The channel is world-<b>READABLE</b> but <b>NOT world-writable</b>. The backing prefs file
 * stays owned by the app's own UID at mode {@code 0644} (and, on the preferred LSPosed
 * remote-prefs path, the remote store is app-private to <em>write</em>) — so although
 * {@code system_server} can read the channel, no other app can forge or tamper with the commands
 * in it. As a second, independent line of defence the module treats <b>every</b> command as
 * UNTRUSTED: each display-scoped command is gated by {@link HookGatePolicy} against AutoX's own
 * display id and <b>fails closed</b>. Consequently a forged or stale command cannot relax input
 * injection (or any per-display hook) on a display that is not AutoX's — the display-id scoping is
 * the second line of defence behind the file's write-protection.
 *
 * <h2>State model — single active AutoX display</h2>
 * <p>The channel is a <b>set of currently-active commands</b>, persisted as the union of:
 * one optional {@link IpcCommand.Type#ENABLE_TRUSTED_DISPLAY} command, plus at most one
 * display-scoped command of each display-scoped type ({@link IpcCommand.Type#ALLOW_INPUT_INJECTION},
 * {@link IpcCommand.Type#SET_DISPLAY_IME}, {@link IpcCommand.Type#LAUNCH_ON_DISPLAY}). The channel
 * therefore models <b>exactly one active AutoX display at a time</b>: re-projecting onto a new
 * display id overwrites the prior command of that type, and the module honors the (single) command
 * of each type it finds. Each {@code enable*}/{@code set*}/{@code launch*} call rewrites the channel
 * so the module always sees the latest scoping; {@link #clear()} empties it on AutoX teardown, and
 * {@link #clearType(IpcCommand.Type)} retracts a single concern.
 *
 * <h2>Call ordering &amp; Wave-2 seam</h2>
 * <p>{@link #enableTrustedDisplay()} is scoped by display <b>name</b> (the display id does not
 * exist yet) and MUST be written <b>before</b> {@code createVirtualDisplay}, so the trusted-flag
 * hook can act as the display is created. The id-scoped commands —
 * {@link #allowInputInjection(int)}, {@link #setDisplayImeAndDecors(int, boolean)},
 * {@link #launchOnDisplay(int, String)} — may only be written <b>after</b> the virtual display
 * exists and its id is known. Wiring {@code Provider.LSPOSED -> new IpcCommandWriter(context)} is
 * the Wave-2 call-site's responsibility and is <b>not</b> done here.
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
     * @throws IllegalArgumentException if {@code pkg} is null/blank or contains a reserved
     *                                  separator. NIT 11: a bad package is a programming error in
     *                                  the call-site, so it is thrown <b>eagerly</b> and kept
     *                                  distinct from the fail-closed {@code false} a disk/prefs
     *                                  error returns — callers must pass a valid package.
     */
    public boolean launchOnDisplay(int displayId, String pkg) {
        Map<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("pkg", pkg);
        // Build (and validate) the command OUTSIDE the fail-closed upsert: an invalid package is a
        // programming error and must surface as IllegalArgumentException, not be swallowed as a
        // false return that looks like a transient disk failure.
        IpcCommand cmd =
                IpcCommand.forDisplay(IpcCommand.Type.LAUNCH_ON_DISPLAY, displayId, extra);
        return upsert(IpcCommand.Type.LAUNCH_ON_DISPLAY, cmd);
    }

    /**
     * Retracts a single command {@code type} from the channel, leaving every other active command
     * in place (SHOULD 5: symmetric, per-command revert). After this the module sees no command of
     * {@code type} and its corresponding gate returns "not enabled", so <em>that one</em> platform
     * behaviour is restored while the rest of the AutoX session stays scoped.
     *
     * <p>This is the general-purpose counterpart to the write-only {@link #allowInputInjection(int)}
     * / {@link #launchOnDisplay(int, String)} / {@link #enableTrustedDisplay()} setters and the
     * {@code setDisplayImeAndDecors(id, false)} disable; it lets a Wave-2 call-site retract one
     * concern without {@link #clear() clearing} the whole channel. Fail-closed: returns
     * {@code false} (never throws) on a null type or any platform/prefs error.
     *
     * @param type the command type to retract; a {@code null} type is a no-op returning
     *             {@code false}
     * @return {@code true} if the channel was rewritten without that command type
     */
    public boolean clearType(IpcCommand.Type type) {
        if (type == null) {
            return false; // fail closed
        }
        return removeType(type);
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
            // apply() (async) is sufficient: the module re-reads via XSharedPreferences change
            // detection (hasFileChanged/reload), so durability-before-return is not required. See
            // NIT 12. apply() never throws on the calling thread, so a true return means enqueued.
            prefs().edit().putString(AutoXXposedModule.IPC_PREFS_KEY, "").apply();
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

    /**
     * Encodes the command map (newline-separated) and writes it to the world-readable file via
     * {@code apply()} (async). The module re-reads through {@code XSharedPreferences} change
     * detection, so durability-before-return is not required (NIT 12); {@code apply()} also never
     * throws on the calling thread, so a {@code true} return means the write was enqueued.
     */
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
        prefs().edit()
                .putString(AutoXXposedModule.IPC_PREFS_KEY, sb.toString())
                .apply();
        return true;
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
