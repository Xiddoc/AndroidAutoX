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
 * every argument, all accessors, the {@code provider}/{@code reason}/{@code isDegraded}/
 * {@code usesLsposed} predicates for all three decisions, and {@code toString}.
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

    private static Decision lsposedDecision() {
        return ProviderSelectionPolicy.select(
                ProviderCapabilities.builder().lsposedModuleActive(true).build());
    }

    private static Decision rootDecision() {
        return ProviderSelectionPolicy.select(ProviderCapabilities.builder()
                .rootAvailable(true)
                .trustedDisplayHonored(true)
                .inputInjectionHonored(true)
                .build());
    }

    private static Decision degradedDecision() {
        return ProviderSelectionPolicy.select(ProviderCapabilities.none());
    }

    @Test
    public void accessors_returnTheExactInstancesGiven() {
        StubSettings settings = new StubSettings();
        StubInput input = new StubInput();
        StubDisplay display = new StubDisplay();
        StubAudio audio = new StubAudio();
        Decision d = rootDecision();

        AutoXProviders p = new AutoXProviders(settings, input, display, audio, d);

        assertSame(settings, p.settings());
        assertSame(input, p.input());
        assertSame(display, p.display());
        assertSame(audio, p.audio());
        assertSame(d, p.decision());
        assertEquals(d.provider, p.provider());
        assertEquals(d.reason, p.reason());
    }

    @Test
    public void rootReflection_notDegraded_notLsposed() {
        AutoXProviders p = build(rootDecision());
        assertEquals(Provider.ROOT_REFLECTION, p.provider());
        assertFalse(p.isDegraded());
        assertFalse(p.usesLsposed());
    }

    @Test
    public void lsposed_usesLsposed_notDegraded() {
        AutoXProviders p = build(lsposedDecision());
        assertEquals(Provider.LSPOSED, p.provider());
        assertTrue(p.usesLsposed());
        assertFalse(p.isDegraded());
    }

    @Test
    public void degraded_isDegraded_notLsposed() {
        AutoXProviders p = build(degradedDecision());
        assertEquals(Provider.DEGRADED, p.provider());
        assertTrue(p.isDegraded());
        assertFalse(p.usesLsposed());
    }

    @Test
    public void toString_includesTheDecision() {
        AutoXProviders p = build(lsposedDecision());
        assertTrue(p.toString().contains("AutoXProviders"));
        assertTrue(p.toString().contains("decision="));
    }

    @Test
    public void nullSettings_throws() {
        assertThrows(() -> new AutoXProviders(
                null, new StubInput(), new StubDisplay(), new StubAudio(), rootDecision()),
                "settings");
    }

    @Test
    public void nullInput_throws() {
        assertThrows(() -> new AutoXProviders(
                new StubSettings(), null, new StubDisplay(), new StubAudio(), rootDecision()),
                "input");
    }

    @Test
    public void nullDisplay_throws() {
        assertThrows(() -> new AutoXProviders(
                new StubSettings(), new StubInput(), null, new StubAudio(), rootDecision()),
                "display");
    }

    @Test
    public void nullAudio_throws() {
        assertThrows(() -> new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), null, rootDecision()),
                "audio");
    }

    @Test
    public void nullDecision_throws() {
        assertThrows(() -> new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), new StubAudio(), null),
                "decision");
    }

    // --- helpers ---

    private static AutoXProviders build(Decision d) {
        return new AutoXProviders(
                new StubSettings(), new StubInput(), new StubDisplay(), new StubAudio(), d);
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
