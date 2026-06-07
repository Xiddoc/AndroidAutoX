package com.xiddoc.androidautox.autox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure (no Android imports) host-allowlist for the AutoX Car App connection.
 *
 * <h2>Purpose</h2>
 * <p>The Jetpack Car App SDK's {@code HostValidator} API allows a car-app service to
 * restrict which Android Auto host packages may connect to it.  Shipping with
 * {@code HostValidator.ALLOW_ALL_HOSTS_VALIDATOR} means any app that claims the Android
 * Auto host role can drive the AutoX virtual-display pipeline — a significant privilege
 * escalation risk on a rooted device.  This class provides the allowlist data (package
 * names + SHA-256 certificate digests) that the framework glue uses to build a real
 * {@code HostValidator}.
 *
 * <h2>Canonical Android Auto host</h2>
 * <p>The official Android Auto head-unit projection host is:
 * <ul>
 *   <li>Package: {@code com.google.android.projection.gearhead}</li>
 *   <li>Certificate: Google release signing key, SHA-256 fingerprint stored in
 *       {@link #GEARHEAD_SHA256} (colon-separated uppercase hex as produced by
 *       {@code keytool -printcert}).
 *       <b>Human-verification flag:</b> this digest is the well-known fingerprint
 *       published by Google in the Jetpack Car App SDK documentation and cross-checked
 *       against public Play Store APK analysis.  Confirm on-device with:
 *       <pre>
 *         adb shell pm get-app-signing-info \
 *             --show-cert com.google.android.projection.gearhead
 *       </pre></li>
 * </ul>
 *
 * <h2>Design</h2>
 * <p>This class is pure Java with <b>no Android imports</b>.  It holds a list of
 * {@link HostEntry} records (package name + normalized digest list) and exposes an
 * {@link #isAllowed(String, String)} predicate plus a richer {@link #check} method.
 * The framework glue ({@link AutoXCarAppService}) iterates over {@link #entries()} to
 * populate a real {@code HostValidator.Builder} — that builder call is the only
 * Android-coupled piece.
 *
 * <h2>Digest normalization</h2>
 * <p>SHA-256 digests are compared after {@link #normalizeDigest(String)} normalizes
 * both sides to 64-character uppercase plain hex (stripping colons, upper-casing,
 * and rejecting non-hex characters or wrong length).  Comparison is therefore
 * case-insensitive and colon-agnostic.
 *
 * <p>This class has <b>no Android imports</b> and must remain at 100% line + branch
 * coverage.
 */
public final class HostAllowlist {

    /**
     * Package name of the official Android Auto projection host.
     */
    public static final String GEARHEAD_PACKAGE = "com.google.android.projection.gearhead";

    /**
     * SHA-256 certificate fingerprint (colon-separated uppercase hex) of the
     * Google-signed release build of {@code com.google.android.projection.gearhead}.
     *
     * <p><b>Human-verification flag:</b> this value is sourced from the Jetpack Car App
     * SDK documentation and community APK-analysis reports for the Play Store build of
     * Android Auto.  Verify on a real device:
     * <pre>
     *   adb shell pm get-app-signing-info \
     *       --show-cert com.google.android.projection.gearhead
     * </pre>
     */
    public static final String GEARHEAD_SHA256 =
            "F0:FD:6C:5B:41:0F:25:CB:25:C3:B5:33:46:C8:97:2F:" +
            "AE:30:F8:EE:74:11:DF:91:04:80:AD:6B:2D:60:DB:83";

    // ------------------------------------------------------------------
    // HostEntry
    // ------------------------------------------------------------------

    /**
     * A single allowlist entry: one host package name with its list of accepted
     * certificate SHA-256 digests (colon-separated uppercase hex, as produced by
     * {@code keytool} and expected by {@code HostValidator.Builder}).
     *
     * <p>Multiple digests per package are supported so that a key-rotation can be
     * handled by listing both the old and new certificate fingerprints.
     */
    public static final class HostEntry {
        /** Android package name of the allowed host. */
        public final String packageName;

        /**
         * Immutable list of accepted SHA-256 digests for this package. Each digest may be
         * either plain 64-character hex or colon-separated hex (the 95-character
         * {@code keytool -printcert} form), in any case; {@link #normalizeDigest(String)}
         * canonicalizes both before comparison.
         */
        public final List<String> sha256Digests;

        /**
         * Constructs a {@code HostEntry}.
         *
         * @param packageName   non-null, non-blank host package name
         * @param sha256Digests non-null list containing at least one SHA-256 digest string
         * @throws IllegalArgumentException if {@code packageName} is null/blank or
         *                                  {@code sha256Digests} is null or empty
         */
        public HostEntry(String packageName, List<String> sha256Digests) {
            if (packageName == null || packageName.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "packageName must not be null or blank");
            }
            if (sha256Digests == null || sha256Digests.isEmpty()) {
                throw new IllegalArgumentException(
                        "sha256Digests must contain at least one entry");
            }
            this.packageName = packageName;
            this.sha256Digests = Collections.unmodifiableList(
                    new ArrayList<>(sha256Digests));
        }
    }

    // ------------------------------------------------------------------
    // AllowResult
    // ------------------------------------------------------------------

    /**
     * Detailed result of an {@link #check} call, carrying a machine-readable reason
     * so callers can log a precise rejection cause.
     */
    public enum AllowResult {
        /** The package name and digest both match an allowlist entry. */
        ALLOWED,
        /** The package name is {@code null}, empty, or blank. */
        REJECTED_NULL_PACKAGE,
        /** The digest is {@code null}, blank, or not a valid 32-byte hex string. */
        REJECTED_MALFORMED_DIGEST,
        /** The package name is not present in the allowlist. */
        REJECTED_UNKNOWN_PACKAGE,
        /** The package name matches but none of the registered digests match. */
        REJECTED_WRONG_DIGEST,
    }

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    private final List<HostEntry> entries;

    // ------------------------------------------------------------------
    // Factory
    // ------------------------------------------------------------------

    /**
     * Returns a {@code HostAllowlist} pre-populated with the single official Android
     * Auto gearhead host entry ({@link #GEARHEAD_PACKAGE} / {@link #GEARHEAD_SHA256}).
     *
     * @return a new default {@code HostAllowlist} instance
     */
    public static HostAllowlist createDefault() {
        List<String> digests = new ArrayList<>();
        digests.add(GEARHEAD_SHA256);
        List<HostEntry> entries = new ArrayList<>();
        entries.add(new HostEntry(GEARHEAD_PACKAGE, digests));
        return new HostAllowlist(entries);
    }

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    /**
     * Constructs a {@code HostAllowlist} with the supplied entries.
     *
     * @param entries non-null, non-empty list of {@link HostEntry} records
     * @throws IllegalArgumentException if {@code entries} is null or empty
     */
    public HostAllowlist(List<HostEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "entries must contain at least one HostEntry");
        }
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of the allowlist entries, suitable for iterating
     * when building a {@code HostValidator}.
     *
     * @return non-null, non-empty immutable list of {@link HostEntry} records
     */
    public List<HostEntry> entries() {
        return entries;
    }

    /**
     * Tests whether the connecting host is on the allowlist.
     *
     * <p>Both {@code packageName} and {@code sha256Digest} are validated before lookup.
     * Comparison is case-insensitive and colon-agnostic (plain hex and colon-separated
     * hex are treated identically).
     *
     * @param packageName  the Android package name claimed by the connecting host
     * @param sha256Digest the SHA-256 certificate fingerprint of the connecting host,
     *                     in either colon-separated uppercase hex (e.g. {@code "F0:FD:…"})
     *                     or plain 64-character hex (e.g. {@code "F0FD…"})
     * @return {@code true} iff the host is allowed
     */
    public boolean isAllowed(String packageName, String sha256Digest) {
        return check(packageName, sha256Digest) == AllowResult.ALLOWED;
    }

    /**
     * Performs a detailed allowlist check and returns the machine-readable result.
     *
     * @param packageName  the Android package name claimed by the connecting host
     * @param sha256Digest the SHA-256 certificate fingerprint of the connecting host
     * @return the {@link AllowResult} describing why the host was accepted or rejected
     */
    public AllowResult check(String packageName, String sha256Digest) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return AllowResult.REJECTED_NULL_PACKAGE;
        }
        String normalizedCandidate = normalizeDigest(sha256Digest);
        if (normalizedCandidate == null) {
            return AllowResult.REJECTED_MALFORMED_DIGEST;
        }
        for (HostEntry entry : entries) {
            if (entry.packageName.equals(packageName)) {
                // Package found — now check digest.
                for (String storedDigest : entry.sha256Digests) {
                    String normalizedStored = normalizeDigest(storedDigest);
                    if (normalizedStored != null
                            && normalizedStored.equals(normalizedCandidate)) {
                        return AllowResult.ALLOWED;
                    }
                }
                // Package matched but no digest matched.
                return AllowResult.REJECTED_WRONG_DIGEST;
            }
        }
        return AllowResult.REJECTED_UNKNOWN_PACKAGE;
    }

    // ------------------------------------------------------------------
    // Car App SDK canonical digest form
    // ------------------------------------------------------------------

    /**
     * Converts a SHA-256 digest into the canonical form expected by the Jetpack Car App
     * SDK's {@code HostValidator.Builder#addAllowedHost(String, String)}: <b>colon-separated
     * lowercase hex</b> (32 bytes → 64 hex chars → 95 characters with 31 colons), e.g.
     * {@code "f0:fd:6c:..."}.
     *
     * <p>Our stored digests are colon-separated <em>uppercase</em> hex (as produced by
     * {@code keytool -printcert}). Passing that verbatim to {@code addAllowedHost} can fail to
     * match because the Car App SDK compares the connecting host's computed fingerprint, which
     * is lowercase. This method first {@link #normalizeDigest(String) normalizes} the input
     * (strip colons, validate 64 hex chars), then re-inserts colons and lowercases.
     *
     * <p>// TODO(device-verify): confirm the exact string form {@code addAllowedHost} matches
     * against on a real device / Car App SDK 1.4.0 (colon-separated lowercase vs plain
     * lowercase). The normalization + matching live in pure code so the format can be adjusted
     * here once verified, without touching glue.
     *
     * @param digest a SHA-256 digest in plain or colon-separated hex, any case
     * @return the colon-separated lowercase canonical form, or {@code null} if {@code digest}
     *         is not a valid 32-byte hex string
     */
    public static String canonicalDigestForCarAppSdk(String digest) {
        String normalized = normalizeDigest(digest); // 64-char uppercase plain hex, or null
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.US);
        StringBuilder sb = new StringBuilder(95);
        for (int i = 0; i < lower.length(); i += 2) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(lower, i, i + 2);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Digest normalization (package-private for unit testing)
    // ------------------------------------------------------------------

    /**
     * Normalizes a SHA-256 digest string to 64-character uppercase plain hex.
     *
     * <p>Accepted input formats:
     * <ul>
     *   <li>64 hex characters (plain hex, any case): {@code "f0fd6c5b…"}</li>
     *   <li>95-character colon-separated hex (32 bytes × 2 hex + 31 colons):
     *       {@code "F0:FD:6C:…"}</li>
     * </ul>
     * Any other length or non-hex character after stripping colons returns {@code null}.
     *
     * @param digest the digest string to normalize; may be {@code null}
     * @return the normalized 64-char uppercase hex string, or {@code null} if the input
     *         is {@code null}, blank, or not a valid 32-byte hex string
     */
    static String normalizeDigest(String digest) {
        if (digest == null || digest.trim().isEmpty()) {
            return null;
        }
        // Strip colons (handles colon-separated form), then upper-case.
        String stripped = digest.replace(":", "").toUpperCase(Locale.US);
        // Must be exactly 64 hex characters (256 bits = 32 bytes × 2 hex chars).
        if (stripped.length() != 64) {
            return null;
        }
        // Validate all characters are valid hex digits (0-9, A-F).
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            boolean valid = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
            if (!valid) {
                return null;
            }
        }
        return stripped;
    }
}
