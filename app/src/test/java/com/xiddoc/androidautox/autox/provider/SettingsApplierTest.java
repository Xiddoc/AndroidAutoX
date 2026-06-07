package com.xiddoc.androidautox.autox.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests for the shared instance {@link SettingsApplier}. */
public class SettingsApplierTest {

    /** In-memory fake that records writes and can deny specific keys per namespace. */
    private static final class FakeProvider implements SystemSettingsProvider {
        final Map<String, Integer> global = new HashMap<>();
        final Map<String, Integer> secure = new HashMap<>();
        final Set<String> denyGlobal = new HashSet<>();
        final Set<String> denySecure = new HashSet<>();
        final List<String> writeOrder = new ArrayList<>();

        @Override
        public SettingsResult putGlobalInt(String key, int value) {
            if (denyGlobal.contains(key)) return SettingsResult.denied();
            writeOrder.add(key);
            global.put(key, value);
            return SettingsResult.ok();
        }

        @Override
        public SettingsResult getGlobalInt(String key) {
            Integer v = global.get(key);
            return v != null ? SettingsResult.ok(v) : SettingsResult.notFound();
        }

        @Override
        public SettingsResult putSecureInt(String key, int value) {
            if (denySecure.contains(key)) return SettingsResult.denied();
            writeOrder.add(key);
            secure.put(key, value);
            return SettingsResult.ok();
        }

        @Override
        public SettingsResult getSecureInt(String key) {
            Integer v = secure.get(key);
            return v != null ? SettingsResult.ok(v) : SettingsResult.notFound();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullProvider_throws() {
        new SettingsApplier(null, SettingsApplier.Namespace.GLOBAL);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullNamespace_throws() {
        new SettingsApplier(new FakeProvider(), null);
    }

    @Test
    public void namespaceAccessor() {
        assertEquals(SettingsApplier.Namespace.SECURE,
                new SettingsApplier(new FakeProvider(), SettingsApplier.Namespace.SECURE)
                        .namespace());
    }

    @Test(expected = IllegalArgumentException.class)
    public void apply_nullEntries_throws() {
        new SettingsApplier(new FakeProvider(), SettingsApplier.Namespace.GLOBAL).apply(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void revert_nullEntries_throws() {
        new SettingsApplier(new FakeProvider(), SettingsApplier.Namespace.GLOBAL).revert(null);
    }

    @Test
    public void apply_global_writesApplyValues_allSucceed() {
        FakeProvider p = new FakeProvider();
        SettingsApplier applier = new SettingsApplier(p, SettingsApplier.Namespace.GLOBAL);
        List<SettingsEntry> entries = Arrays.asList(
                SettingsEntry.forAbsentKey("a", 1),
                SettingsEntry.forExistingKey("b", 1, 0));

        ApplyResult r = applier.apply(entries);

        assertTrue(r.allSucceeded());
        assertEquals(2, r.succeeded);
        assertEquals(Integer.valueOf(1), p.global.get("a"));
        assertEquals(Integer.valueOf(1), p.global.get("b"));
        assertEquals(Arrays.asList("a", "b"), p.writeOrder);
    }

    @Test
    public void apply_secure_usesSecureNamespace() {
        FakeProvider p = new FakeProvider();
        SettingsApplier applier = new SettingsApplier(p, SettingsApplier.Namespace.SECURE);
        applier.apply(Collections.singletonList(SettingsEntry.forAbsentKey("s", 1)));
        assertEquals(Integer.valueOf(1), p.secure.get("s"));
        assertTrue(p.global.isEmpty());
    }

    @Test
    public void apply_continueAndReport_recordsFailedKeysButKeepsGoing() {
        FakeProvider p = new FakeProvider();
        p.denyGlobal.add("b");
        SettingsApplier applier = new SettingsApplier(p, SettingsApplier.Namespace.GLOBAL);
        List<SettingsEntry> entries = Arrays.asList(
                SettingsEntry.forAbsentKey("a", 1),
                SettingsEntry.forAbsentKey("b", 1),
                SettingsEntry.forAbsentKey("c", 1));

        ApplyResult r = applier.apply(entries);

        assertFalse(r.allSucceeded());
        assertEquals(2, r.succeeded);
        assertEquals(3, r.total);
        assertEquals(Collections.singletonList("b"), r.failedKeys());
        // a and c still written despite b failing (continue-and-report).
        assertTrue(p.global.containsKey("a"));
        assertTrue(p.global.containsKey("c"));
        assertFalse(p.global.containsKey("b"));
    }

    @Test
    public void revert_writesRevertValues() {
        FakeProvider p = new FakeProvider();
        SettingsApplier applier = new SettingsApplier(p, SettingsApplier.Namespace.GLOBAL);
        List<SettingsEntry> entries = Arrays.asList(
                SettingsEntry.forAbsentKey("a", 1),       // revertValue = DEFAULT (0)
                SettingsEntry.forExistingKey("b", 1, 7)); // revertValue = 7

        ApplyResult r = applier.revert(entries);

        assertTrue(r.allSucceeded());
        assertEquals(Integer.valueOf(0), p.global.get("a"));
        assertEquals(Integer.valueOf(7), p.global.get("b"));
    }

    @Test
    public void revert_continueAndReport() {
        FakeProvider p = new FakeProvider();
        p.denySecure.add("a");
        SettingsApplier applier = new SettingsApplier(p, SettingsApplier.Namespace.SECURE);
        List<SettingsEntry> entries = Arrays.asList(
                SettingsEntry.forExistingKey("a", 1, 1),
                SettingsEntry.forExistingKey("b", 1, 1));

        ApplyResult r = applier.revert(entries);

        assertFalse(r.allSucceeded());
        assertEquals(1, r.succeeded);
        assertEquals(Collections.singletonList("a"), r.failedKeys());
    }
}
