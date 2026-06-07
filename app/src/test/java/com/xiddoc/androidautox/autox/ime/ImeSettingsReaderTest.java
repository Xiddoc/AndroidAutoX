package com.xiddoc.androidautox.autox.ime;

import static org.junit.Assert.assertEquals;

import com.xiddoc.androidautox.autox.provider.SettingsEntry;
import com.xiddoc.androidautox.autox.provider.SettingsResult;
import com.xiddoc.androidautox.autox.provider.SystemSettingsProvider;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests for the pure {@link ImeSettingsReader} prior-reading logic. */
public class ImeSettingsReaderTest {

    private static final class FakeProvider implements SystemSettingsProvider {
        final Map<String, Integer> secure = new HashMap<>();
        final Set<String> deniedGet = new HashSet<>();

        @Override public SettingsResult putGlobalInt(String key, int value) { return SettingsResult.ok(); }
        @Override public SettingsResult getGlobalInt(String key) { return SettingsResult.notFound(); }
        @Override public SettingsResult putSecureInt(String key, int value) {
            secure.put(key, value);
            return SettingsResult.ok();
        }
        @Override public SettingsResult getSecureInt(String key) {
            if (deniedGet.contains(key)) return SettingsResult.denied();
            Integer v = secure.get(key);
            return v != null ? SettingsResult.ok(v) : SettingsResult.notFound();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullProvider_throws() {
        new ImeSettingsReader(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void readPriors_nullSpec_throws() {
        new ImeSettingsReader(new FakeProvider()).readPriors(null);
    }

    @Test
    public void readPriors_absentKeys_mapToUnset_revertDisabled() {
        FakeProvider p = new FakeProvider();
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(5);
        ImeDisplaySettingsSpec out = new ImeSettingsReader(p).readPriors(spec);
        // Both unset → applyEntries revert to disabled (WRITE_DEFAULT).
        List<SettingsEntry> apply = out.applyEntries();
        assertEquals(SettingsEntry.RevertStrategy.WRITE_DEFAULT, apply.get(0).revertStrategy);
        assertEquals(SettingsEntry.RevertStrategy.WRITE_DEFAULT, apply.get(1).revertStrategy);
    }

    @Test
    public void readPriors_existingValues_captured() {
        FakeProvider p = new FakeProvider();
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(5);
        p.secure.put(spec.decorKey(), 0); // disabled
        p.secure.put(spec.imeKey(), 1);   // enabled
        ImeDisplaySettingsSpec out = new ImeSettingsReader(p).readPriors(spec);
        List<SettingsEntry> apply = out.applyEntries();
        assertEquals(SettingsEntry.RevertStrategy.RESTORE_PRIOR, apply.get(0).revertStrategy);
        assertEquals(0, apply.get(0).priorValue);
        assertEquals(SettingsEntry.RevertStrategy.RESTORE_PRIOR, apply.get(1).revertStrategy);
        assertEquals(1, apply.get(1).priorValue);
    }

    @Test
    public void readPriors_deniedRead_treatedAsDisabledPrior() {
        FakeProvider p = new FakeProvider();
        ImeDisplaySettingsSpec spec = ImeDisplaySettingsSpec.forDisplay(5);
        p.deniedGet.add(spec.decorKey());
        p.deniedGet.add(spec.imeKey());
        ImeDisplaySettingsSpec out = new ImeSettingsReader(p).readPriors(spec);
        List<SettingsEntry> apply = out.applyEntries();
        // DENIED → VALUE_DISABLED (0) → RESTORE_PRIOR with prior 0.
        assertEquals(SettingsEntry.RevertStrategy.RESTORE_PRIOR, apply.get(0).revertStrategy);
        assertEquals(0, apply.get(0).priorValue);
        assertEquals(0, apply.get(1).priorValue);
    }
}
