package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.xiddoc.androidautox.autox.AutoXDisplaySpec;
import com.xiddoc.androidautox.autox.GestureSpec;
import com.xiddoc.androidautox.autox.provider.ProviderSelectionPolicy.Decision;
import com.xiddoc.androidautox.autox.provider.ProviderSelectionPolicy.Provider;

import org.junit.Test;

/**
 * 100% line + branch tests for the pure {@link AutoXProviders} holder: null-guards on
 * every argument, all accessors, the {@code provider}/{@code reason}/{@code isBlocked}/
 * {@code usesLsposed}/{@code isProvisional} predicates for both decisions,
 * {@code toString}, and the pure {@link AutoXProviders#reevaluate(boolean, boolean)}
 * recompute across the static-probe input combinations (LSPosed-first binary model).
 */
public class AutoXProvidersTest {

    // --- minimal pure stubs for the four provider seams ---

    private static final class StubSettings implements SystemSettingsProvider {
        @Override public SettingsResult putGlobalInt(String k, int v) { return SettingsResult.ok(); }
        @Override public SettingsResult getGlobalInt(String k) { return SettingsResult.notFound(); }
        @Override public SettingsResult putSecureInt(String k, int v) { return SettingsResult.ok(); }
        @Override public SettingsResult getSecureInt(String k) { return SettingsResult.notFound(); }
    }

    private static final class StubInput implements InputProvider {
        @Override public boolean inject(GestureSpec spec) { return false; }
        @Override public boolean isInjectionHonored() { return false; }
    }

    private static final class StubDisplay implements DisplayProvider {
        @Override public int create(AutoXDisplaySpec spec) { return NO_DISPLAY; }
        @Override public boolean resize(int w, int h, int d) { return false; }
        @Override public void release() { }
        @Override public int getDisplayId() { return NO_DISPLAY; }
        @Override public boolean isTrustedDisplayHonored() { return false; }
    }

    private static final class StubAudio implements AudioRouter {
        @Override public boolean setUidAffinity(int uid, String addr) { return false; }
        @Override public boolean clearUidAffinity(int uid) { return false; }
    }

    /** Fully-honored LSPosed decision (the only LSPOSED outcome). */
    private static Decision lsposedDecision() {
        return ProviderSelectionPolicy.select(ProviderCapabilities.builder()
                .lsposedModuleActive(true)
                .trustedDisplayHonored(true)
                .inputInjectionHonored(true)
                .build());
    }

    /** A BLOCKED decision (no LSPosed). */
    private static Decision blockedDecision() {
        return ProviderSelectionPolicy.select(ProviderCapabilities.none());
    }

    @Test
    public void accessors_returnTheExactInstancesGiven() {
        StubSettings settings = new StubSettings();
        StubInput input = new StubInput();
        StubDisplay display = new StubDisplay();
        StubAudio audio = new StubAudio();
        Decision d = lsposedDecision();

        AutoXProviders p = new AutoXProviders(settings, input, display, audio, d,
                true, false, true, true, false);

        assertSame(settings, p.settings());
        assertSame(input, p.input());
        assertSame(display, p.display());
        assertSame(audio, p.audio());
        assertSame(d, p.decision());
        assertEquals(d.provider, p.provider());
        assertEquals(d.reason, p.reason());
    }

    @Test
    public void lsposed_usesLsposed_notBlocked() {
        AutoXProviders p = build(lsposedDecision());
        assertEquals(Provider.LSPOSED, p.provider());
        assertTrue(p.usesLsposed());
        assertFalse(p.isBlocked());
    }

    @Test
    public void blocked_isBlocked_notLsposed() {
        AutoXProviders p = build(blockedDecision());
        assertEquals(Provider.BLOCKED, p.provider());
        assertTrue(p.isBlocked());
        assertFalse(p.usesLsposed());
    }

    @Test
    public void provisionalFlag_isExposed_bothValues() {
        AutoXProviders provisional = new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), new StubAudio(),
                blockedDecision(), false, false, true, true, true);
        assertTrue(provisional.isProvisional());

        AutoXProviders settled = new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), new StubAudio(),
                lsposedDecision(), true, false, true, true, false);
        assertFalse(settled.isProvisional());
    }

    @Test
    public void toString_includesDecisionAndProvisional() {
        AutoXProviders p = build(lsposedDecision());
        assertTrue(p.toString().contains("AutoXProviders"));
        assertTrue(p.toString().contains("decision="));
        assertTrue(p.toString().contains("provisional="));
    }

    // --- reevaluate(...) : pure recompute, carries instances, clears provisional ---

    @Test
    public void reevaluate_lsposedDevice_promotesToLsposedWhenHooksHonored_andClearsProvisional() {
        StubSettings settings = new StubSettings();
        StubInput input = new StubInput();
        StubDisplay display = new StubDisplay();
        StubAudio audio = new StubAudio();

        // Provisional bundle on an LSPosed device: at start trusted/injection are false but
        // LSPosed is active => provisionally LSPOSED already. (Provisional honored-flags are not
        // yet meaningful.)
        AutoXProviders provisional = new AutoXProviders(settings, input, display, audio,
                blockedDecision(), true, false, true, true, true);
        assertTrue(provisional.isProvisional());

        // Surface arrives: both hooks honored => LSPOSED.
        AutoXProviders settled = provisional.reevaluate(true, true);
        assertEquals(Provider.LSPOSED, settled.provider());
        assertFalse(settled.isBlocked());
        assertFalse(settled.isProvisional());

        // Provider instances carry over unchanged.
        assertSame(settings, settled.settings());
        assertSame(input, settled.input());
        assertSame(display, settled.display());
        assertSame(audio, settled.audio());
    }

    @Test
    public void reevaluate_lsposed_trustedHookIneffective_blocks() {
        AutoXProviders provisional = new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), new StubAudio(),
                blockedDecision(), true, false, true, true, true);

        AutoXProviders settled = provisional.reevaluate(false, true);
        assertEquals(Provider.BLOCKED, settled.provider());
        assertTrue(settled.isBlocked());
        assertFalse(settled.isProvisional());
    }

    @Test
    public void reevaluate_lsposed_injectionHookIneffective_blocks() {
        AutoXProviders provisional = new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), new StubAudio(),
                blockedDecision(), true, false, true, true, true);

        AutoXProviders settled = provisional.reevaluate(true, false);
        assertEquals(Provider.BLOCKED, settled.provider());
        assertFalse(settled.isProvisional());
    }

    @Test
    public void reevaluate_noLsposed_staysBlocked_evenIfHooksHonored() {
        AutoXProviders provisional = new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), new StubAudio(),
                blockedDecision(), false, false, true, true, true);

        AutoXProviders settled = provisional.reevaluate(true, true);
        assertEquals(Provider.BLOCKED, settled.provider());
        assertFalse(settled.isProvisional());
    }

    @Test
    public void nullSettings_throws() {
        assertThrows(() -> new AutoXProviders(
                null, new StubInput(), new StubDisplay(), new StubAudio(), lsposedDecision(),
                true, false, true, true, false),
                "settings");
    }

    @Test
    public void nullInput_throws() {
        assertThrows(() -> new AutoXProviders(
                new StubSettings(), null, new StubDisplay(), new StubAudio(), lsposedDecision(),
                true, false, true, true, false),
                "input");
    }

    @Test
    public void nullDisplay_throws() {
        assertThrows(() -> new AutoXProviders(
                new StubSettings(), new StubInput(), null, new StubAudio(), lsposedDecision(),
                true, false, true, true, false),
                "display");
    }

    @Test
    public void nullAudio_throws() {
        assertThrows(() -> new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), null, lsposedDecision(),
                true, false, true, true, false),
                "audio");
    }

    @Test
    public void nullDecision_throws() {
        assertThrows(() -> new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), new StubAudio(), null,
                true, false, true, true, false),
                "decision");
    }

    // --- helpers ---

    private static AutoXProviders build(Decision d) {
        return new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), new StubAudio(), d,
                true, false, true, true, false);
    }

    private interface ThrowingRunnable {
        void run();
    }

    private static void assertThrows(ThrowingRunnable r, String messageFragment) {
        try {
            r.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue("message should mention '" + messageFragment + "': " + expected.getMessage(),
                    expected.getMessage().contains(messageFragment));
        }
    }
}
