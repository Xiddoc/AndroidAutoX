package com.xiddoc.androidautox.autox.provider;

/**
 * Immutable snapshot of the privileged-provider capabilities detected on the live
 * system. Produced by capability detection (the pure parts here; the live reflection in
 * the existing excluded provider glue — {@code RootDisplayProvider},
 * {@code ReflectiveGestureInjector}, the root settings providers — collected via
 * {@link CapabilityDecider}, not via any single dedicated probe class) and consumed by the
 * pure {@link ProviderSelectionPolicy} to choose which provider set to use.
 *
 * <p>Framework-free value object (no Android imports) — fully unit testable. Built via
 * the nested {@link Builder} so call sites read clearly and unset flags default to the
 * conservative {@code false}.
 */
public final class ProviderCapabilities {

    /** True if an LSPosed module hook for AndroidAutoX is active in {@code system_server}. */
    public final boolean lsposedModuleActive;
    /** True if the app is signed with the platform signature (signature-permission path). */
    public final boolean platformSignature;
    /** True if a root shell / root reflection path is available. */
    public final boolean rootAvailable;
    /** True if {@code VIRTUAL_DISPLAY_FLAG_TRUSTED} is honored (not silently stripped). */
    public final boolean trustedDisplayHonored;
    /** True if cross-display {@code injectInputEvent} is honored. */
    public final boolean inputInjectionHonored;
    /** True if protected {@code Settings.Global}/{@code Secure} writes succeed. */
    public final boolean secureSettingsWritable;

    private ProviderCapabilities(Builder b) {
        this.lsposedModuleActive = b.lsposedModuleActive;
        this.platformSignature = b.platformSignature;
        this.rootAvailable = b.rootAvailable;
        this.trustedDisplayHonored = b.trustedDisplayHonored;
        this.inputInjectionHonored = b.inputInjectionHonored;
        this.secureSettingsWritable = b.secureSettingsWritable;
    }

    /** @return a fresh builder with all capabilities defaulting to {@code false}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience: a capability snapshot with nothing available (the fully degraded
     * baseline). Equivalent to {@code builder().build()}.
     */
    public static ProviderCapabilities none() {
        return builder().build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProviderCapabilities)) return false;
        ProviderCapabilities c = (ProviderCapabilities) o;
        return lsposedModuleActive == c.lsposedModuleActive
                && platformSignature == c.platformSignature
                && rootAvailable == c.rootAvailable
                && trustedDisplayHonored == c.trustedDisplayHonored
                && inputInjectionHonored == c.inputInjectionHonored
                && secureSettingsWritable == c.secureSettingsWritable;
    }

    @Override
    public int hashCode() {
        int h = 17;
        h = 31 * h + (lsposedModuleActive ? 1 : 0);
        h = 31 * h + (platformSignature ? 1 : 0);
        h = 31 * h + (rootAvailable ? 1 : 0);
        h = 31 * h + (trustedDisplayHonored ? 1 : 0);
        h = 31 * h + (inputInjectionHonored ? 1 : 0);
        h = 31 * h + (secureSettingsWritable ? 1 : 0);
        return h;
    }

    @Override
    public String toString() {
        return "ProviderCapabilities{"
                + "lsposedModuleActive=" + lsposedModuleActive
                + ", platformSignature=" + platformSignature
                + ", rootAvailable=" + rootAvailable
                + ", trustedDisplayHonored=" + trustedDisplayHonored
                + ", inputInjectionHonored=" + inputInjectionHonored
                + ", secureSettingsWritable=" + secureSettingsWritable
                + '}';
    }

    /** Fluent builder for {@link ProviderCapabilities}. */
    public static final class Builder {
        private boolean lsposedModuleActive;
        private boolean platformSignature;
        private boolean rootAvailable;
        private boolean trustedDisplayHonored;
        private boolean inputInjectionHonored;
        private boolean secureSettingsWritable;

        private Builder() {
        }

        public Builder lsposedModuleActive(boolean v) {
            this.lsposedModuleActive = v;
            return this;
        }

        public Builder platformSignature(boolean v) {
            this.platformSignature = v;
            return this;
        }

        public Builder rootAvailable(boolean v) {
            this.rootAvailable = v;
            return this;
        }

        public Builder trustedDisplayHonored(boolean v) {
            this.trustedDisplayHonored = v;
            return this;
        }

        public Builder inputInjectionHonored(boolean v) {
            this.inputInjectionHonored = v;
            return this;
        }

        public Builder secureSettingsWritable(boolean v) {
            this.secureSettingsWritable = v;
            return this;
        }

        public ProviderCapabilities build() {
            return new ProviderCapabilities(this);
        }
    }
}
