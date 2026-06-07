package com.xiddoc.androidautox.autox;

import java.util.Objects;

/**
 * Immutable value object describing a launchable target app that can be streamed
 * onto an AutoX virtual display (the isolated head-unit projection surface).
 *
 * <p>An instance carries the Android {@link #packageName} used to build the launch
 * {@link android.content.Intent} and a human-readable {@link #label} shown in the
 * selection UI. Both are set at construction time and never change.
 *
 * <p><b>Package-name validation</b> — the constructor enforces a minimal set of
 * well-formedness rules so callers receive a fast-fail for obvious mistakes rather
 * than a silent bad intent at runtime:
 * <ul>
 *   <li>Must be non-null and non-empty.</li>
 *   <li>Must contain at least one {@code '.'} (so a bare identifier like {@code "foo"}
 *       is rejected).</li>
 *   <li>Every dot-separated segment must be non-empty and must start with a character
 *       that is a valid Java identifier start ({@link Character#isJavaIdentifierStart})
 *       — this rejects leading/trailing dots, doubled dots, numeric-leading segments,
 *       and whitespace inside the name.</li>
 * </ul>
 * The rule is intentionally simple: it is a plausibility check, not a full RFC
 * validator. A valid Android package name is a superset of what passes here.
 *
 * <p><b>Label defaulting</b> — if {@code label} is {@code null} or blank the
 * constructor substitutes {@code packageName} so the object is always display-ready.
 */
public final class AutoXTargetApp {

    /** Android package name used to construct the launch intent. */
    public final String packageName;

    /**
     * Human-readable display label for the app. Never null; falls back to
     * {@link #packageName} when no label was supplied at construction time.
     */
    public final String label;

    /**
     * Constructs a new target-app descriptor.
     *
     * @param packageName the Android package name (e.g. {@code "com.instagram.android"});
     *                    must be non-null, non-empty, contain at least one {@code '.'},
     *                    and have every dot-separated segment start with a valid Java
     *                    identifier character.
     * @param label       a human-readable name; if {@code null} or blank the package name
     *                    is used as the label.
     * @throws IllegalArgumentException if {@code packageName} fails the validation rules.
     */
    public AutoXTargetApp(String packageName, String label) {
        validatePackageName(packageName);
        this.packageName = packageName;
        this.label = (label == null || label.trim().isEmpty()) ? packageName : label;
    }

    /**
     * Validates {@code packageName} against the minimal well-formedness rules.
     *
     * @param packageName the candidate package name.
     * @throws IllegalArgumentException if validation fails.
     */
    private static void validatePackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalArgumentException(
                    "packageName must be non-null and non-empty");
        }
        if (!packageName.contains(".")) {
            throw new IllegalArgumentException(
                    "packageName must contain at least one '.' (got: \"" + packageName + "\")");
        }
        String[] segments = packageName.split("\\.", -1);
        for (String segment : segments) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException(
                        "packageName must not have empty segments (leading, trailing, or doubled "
                                + "dots) (got: \"" + packageName + "\")");
            }
            if (!Character.isJavaIdentifierStart(segment.charAt(0))) {
                throw new IllegalArgumentException(
                        "each segment of packageName must start with a valid Java identifier "
                                + "character; bad segment: \"" + segment
                                + "\" in \"" + packageName + "\"");
            }
        }
    }

    // ------------------------------------------------------------------
    // Standard object methods
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} when both objects are {@link AutoXTargetApp} instances with
     * equal {@link #packageName} and {@link #label}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AutoXTargetApp)) return false;
        AutoXTargetApp other = (AutoXTargetApp) o;
        return packageName.equals(other.packageName) && label.equals(other.label);
    }

    /** Hash code consistent with {@link #equals}. */
    @Override
    public int hashCode() {
        return Objects.hash(packageName, label);
    }

    /**
     * Returns a developer-friendly representation:
     * {@code AutoXTargetApp{packageName='...', label='...'}}.
     */
    @Override
    public String toString() {
        return "AutoXTargetApp{packageName='" + packageName + "', label='" + label + "'}";
    }
}
