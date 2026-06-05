package com.xiddoc.androidautox;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Minimal in-memory {@link SharedPreferences} for plain-JUnit tests (no Robolectric).
 *
 * <p>Robolectric runs tests in its own sandbox classloader, which JaCoCo's on-the-fly
 * instrumentation cannot see — so Robolectric-loaded production classes report 0%
 * coverage. To get real coverage for {@link PhixitEngine}/{@link TweakRegistry} we run
 * those tests on the plain JUnit classloader and hand them this fake instead of a real
 * Android {@code SharedPreferences}. Only the methods the production code touches are
 * implemented; {@code apply()}/{@code commit()} mutate the backing map immediately.
 *
 * <p>This is a generic, reusable plain-JUnit pref double (shared-helper intent): it is
 * not tied to any one test, so a future plain-JUnit group can reuse it as-is.
 */
public final class FakeSharedPreferences implements SharedPreferences {

    private final Map<String, Object> map = new HashMap<String, Object>();

    @Override
    public Map<String, ?> getAll() {
        return new HashMap<String, Object>(map);
    }

    @Override
    public String getString(String key, String defValue) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object v = map.get(key);
        return v instanceof Set ? (Set<String>) v : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object v = map.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object v = map.get(key);
        return v instanceof Long ? (Long) v : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object v = map.get(key);
        return v instanceof Float ? (Float) v : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object v = map.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    @Override
    public boolean contains(String key) {
        return map.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
    }

    private final class FakeEditor implements Editor {
        private final Map<String, Object> pending = new HashMap<String, Object>();
        private final Set<String> removals = new HashSet<String>();
        private boolean clear;

        @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
        @Override public Editor putStringSet(String key, Set<String> values) { pending.put(key, values); return this; }
        @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
        @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
        @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
        @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
        @Override public Editor remove(String key) { removals.add(key); return this; }
        @Override public Editor clear() { clear = true; return this; }

        @Override
        public boolean commit() {
            if (clear) map.clear();
            for (String k : removals) map.remove(k);
            map.putAll(pending);
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
