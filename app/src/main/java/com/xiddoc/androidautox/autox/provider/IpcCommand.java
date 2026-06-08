package com.xiddoc.androidautox.autox.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure, serializable model of a single app&harr;LSPosed-module command carried over
 * {@code XSharedPreferences}.
 *
 * <p>The app (which has the UI) writes commands into a shared-prefs file; the LSPosed
 * hook (running in {@code system_server}) reads them back via {@code XSharedPreferences}.
 * Because the two sides are in different processes with no AIDL, the contract is a plain
 * string/key-value encoding modeled <b>here</b>, with zero Android imports, so it can be
 * unit tested to 100% and shared verbatim by both sides.
 *
 * <p>Wire format ({@link #encode()} / {@link #decode(String)}):
 * <pre>
 *   type|key1=val1;key2=val2
 * </pre>
 * The {@link Type} token comes first, then a {@code ;}-separated list of {@code key=value}
 * arguments. Empty argument list encodes as just the bare type token. Encoding is
 * deterministic (insertion-ordered args) so two equal commands encode identically.
 */
public final class IpcCommand {

    /**
     * Sentinel returned by {@link #displayId()} when no valid display id is present. Equal to
     * {@code HookGatePolicy.NO_DISPLAY_ID} (-1, never a valid Android display id) so the gate
     * treats it as "no display known"; defined locally to keep this class import-free.
     */
    public static final int NO_DISPLAY_ID = -1;

    /** Reserved separator between the type token and the argument list. */
    private static final char TYPE_SEP = '|';
    /** Reserved separator between key=value argument pairs. */
    private static final char ARG_SEP = ';';
    /** Reserved separator between an argument key and its value. */
    private static final char KV_SEP = '=';

    /**
     * Argument key carrying the AutoX virtual-display id a command is scoped to.
     *
     * <p>The display-scoped commands ({@link Type#ALLOW_INPUT_INJECTION},
     * {@link Type#SET_DISPLAY_IME}, {@link Type#LAUNCH_ON_DISPLAY}) all carry this key so the
     * LSPosed hooks can gate on AutoX's own display id (see
     * {@code HookGatePolicy.shouldActForDisplayId}). The value is the decimal display id as a
     * string; {@link #displayId()} parses it back.
     */
    public static final String ARG_DISPLAY_ID = "displayId";

    /** The set of commands the app can issue to the LSPosed module. */
    public enum Type {
        /** Honor {@code VIRTUAL_DISPLAY_FLAG_TRUSTED} for AutoX displays. */
        ENABLE_TRUSTED_DISPLAY,
        /** Allow {@code injectInputEvent} to target a specific display. */
        ALLOW_INPUT_INJECTION,
        /** Set whether the IME (and system decors) may show on a given display. */
        SET_DISPLAY_IME,
        /** Permit launching an arbitrary activity on a specific (AutoX) display. */
        LAUNCH_ON_DISPLAY,
        /** Bind a UID's audio to a specific output device. */
        SET_UID_AFFINITY
    }

    private final Type type;
    private final Map<String, String> args;

    private IpcCommand(Type type, Map<String, String> args) {
        this.type = type;
        // Defensive, order-preserving, unmodifiable copy.
        this.args = Collections.unmodifiableMap(new LinkedHashMap<String, String>(args));
    }

    /**
     * Creates a command with the given type and arguments.
     *
     * @param type the command type; must not be null
     * @param args ordered key/value arguments; must not be null (may be empty). Keys and
     *             values must not be null/blank or contain a reserved separator
     *             ({@code |}, {@code ;}, {@code =}).
     * @throws IllegalArgumentException on a null type, null args map, or an invalid
     *                                  key/value
     */
    public static IpcCommand of(Type type, Map<String, String> args) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (args == null) {
            throw new IllegalArgumentException("args must not be null");
        }
        for (Map.Entry<String, String> e : args.entrySet()) {
            validateToken("key", e.getKey());
            validateToken("value", e.getValue());
        }
        return new IpcCommand(type, args);
    }

    /** Creates an argument-less command. */
    public static IpcCommand of(Type type) {
        return of(type, Collections.<String, String>emptyMap());
    }

    /**
     * Creates a display-scoped command: the given {@code displayId} is stored under
     * {@link #ARG_DISPLAY_ID} (first, so it leads the encoded arg list), followed by any
     * {@code extraArgs} in their iteration order.
     *
     * @param type      the command type; must not be null
     * @param displayId the AutoX virtual-display id this command is scoped to; must be
     *                  {@code >= 0} (a valid Android display id)
     * @param extraArgs additional key/value arguments; must not be null (may be empty) and
     *                  must not redefine {@link #ARG_DISPLAY_ID}
     * @return a command whose {@link #displayId()} returns {@code displayId}
     * @throws IllegalArgumentException on a null type/extraArgs, a negative {@code displayId},
     *                                  an {@code extraArgs} entry re-using {@link #ARG_DISPLAY_ID},
     *                                  or an otherwise invalid key/value
     */
    public static IpcCommand forDisplay(Type type, int displayId, Map<String, String> extraArgs) {
        if (extraArgs == null) {
            throw new IllegalArgumentException("extraArgs must not be null");
        }
        if (displayId < 0) {
            throw new IllegalArgumentException("displayId must be >= 0: " + displayId);
        }
        if (extraArgs.containsKey(ARG_DISPLAY_ID)) {
            throw new IllegalArgumentException(
                    "extraArgs must not redefine reserved key '" + ARG_DISPLAY_ID + "'");
        }
        Map<String, String> merged = new LinkedHashMap<String, String>();
        merged.put(ARG_DISPLAY_ID, Integer.toString(displayId));
        merged.putAll(extraArgs);
        return of(type, merged);
    }

    /** Creates a display-scoped command with no extra arguments beyond the display id. */
    public static IpcCommand forDisplay(Type type, int displayId) {
        return forDisplay(type, displayId, Collections.<String, String>emptyMap());
    }

    private static void validateToken(String what, String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be null/empty");
        }
        if (token.indexOf(TYPE_SEP) >= 0
                || token.indexOf(ARG_SEP) >= 0
                || token.indexOf(KV_SEP) >= 0) {
            throw new IllegalArgumentException(
                    what + " must not contain a reserved separator (| ; =): '" + token + "'");
        }
    }

    /** @return the command type. */
    public Type getType() {
        return type;
    }

    /**
     * @return an unmodifiable, insertion-ordered view of the command arguments.
     */
    public Map<String, String> getArgs() {
        return args;
    }

    /**
     * @param key argument name
     * @return the argument value, or {@code null} if absent
     */
    public String arg(String key) {
        return args.get(key);
    }

    /**
     * Returns the display id this command is scoped to, parsed from its {@link #ARG_DISPLAY_ID}
     * argument.
     *
     * <p>Fail-safe (never throws): returns {@link #NO_DISPLAY_ID} when the argument is absent,
     * blank, or not a parseable non-negative integer. This is the exact value the LSPosed gate
     * ({@code HookGatePolicy}) treats as "no display known", so a malformed/absent id makes the
     * hook no-op rather than acting on an unintended display.
     *
     * @return the parsed display id ({@code >= 0}), or {@link #NO_DISPLAY_ID} if absent/invalid
     */
    public int displayId() {
        String raw = args.get(ARG_DISPLAY_ID);
        if (raw == null) {
            return NO_DISPLAY_ID;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return NO_DISPLAY_ID;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return NO_DISPLAY_ID;
        }
        return parsed < 0 ? NO_DISPLAY_ID : parsed;
    }

    /**
     * Encodes this command to its wire-format string.
     *
     * @return {@code type|k1=v1;k2=v2}, or just {@code type} when there are no arguments
     */
    public String encode() {
        StringBuilder sb = new StringBuilder(type.name());
        if (!args.isEmpty()) {
            sb.append(TYPE_SEP);
            boolean first = true;
            for (Map.Entry<String, String> e : args.entrySet()) {
                if (!first) {
                    sb.append(ARG_SEP);
                }
                sb.append(e.getKey()).append(KV_SEP).append(e.getValue());
                first = false;
            }
        }
        return sb.toString();
    }

    /**
     * Decodes a wire-format string produced by {@link #encode()} back into an
     * {@link IpcCommand}.
     *
     * @param wire the encoded command; must not be null
     * @return the decoded command
     * @throws IllegalArgumentException if {@code wire} is null/blank, names an unknown
     *                                  command type, or contains a malformed argument
     */
    public static IpcCommand decode(String wire) {
        if (wire == null || wire.trim().isEmpty()) {
            throw new IllegalArgumentException("wire must not be null/blank");
        }
        String s = wire.trim();
        int sep = s.indexOf(TYPE_SEP);
        String typeToken = sep < 0 ? s : s.substring(0, sep);
        Type type = parseType(typeToken);

        Map<String, String> parsed = new LinkedHashMap<String, String>();
        if (sep >= 0) {
            String argList = s.substring(sep + 1);
            for (String pair : argList.split(String.valueOf(ARG_SEP), -1)) {
                int kv = pair.indexOf(KV_SEP);
                if (kv < 0) {
                    throw new IllegalArgumentException("malformed argument (no '='): '" + pair + "'");
                }
                String key = pair.substring(0, kv);
                String value = pair.substring(kv + 1);
                validateToken("key", key);
                validateToken("value", value);
                parsed.put(key, value);
            }
        }
        return new IpcCommand(type, parsed);
    }

    private static Type parseType(String token) {
        try {
            return Type.valueOf(token);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown command type: '" + token + "'");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IpcCommand)) return false;
        IpcCommand other = (IpcCommand) o;
        return type == other.type && args.equals(other.args);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + args.hashCode();
    }

    @Override
    public String toString() {
        return "IpcCommand{" + encode() + '}';
    }
}
