package com.xiddoc.androidautox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Direct Robolectric tests for {@link PhixitEngine#isAppliedStrict}, covering the paths that
 * distinguish it from the lenient {@link PhixitEngine#isApplied}:
 * <ul>
 *   <li>all specs match in every partition → {@code true};</li>
 *   <li>one spec mismatches → {@code false};</li>
 *   <li>structurally unreadable DB → THROWS (empty partition list, and a partition list with no
 *       decodable blob), so callers can map it to UNKNOWN instead of FALSE.</li>
 * </ul>
 *
 * <p>The root boundary is faked by reflectively installing a fake {@link IPhixitRoot} into
 * {@link RootDb}'s static service field, so {@code RootDb.readPartitions} returns crafted
 * partitions built with the production {@link PhixitSnapshot} encoder.
 *
 * <p>Because {@code isApplied} now delegates to {@code isAppliedStrict}, the same fakes also
 * confirm the lenient wrapper maps every thrown/structural case to {@code false}.
 */
@RunWith(RobolectricTestRunner.class)
public class PhixitEngineIsAppliedStrictTest {

    private static final String PKG = FlagSpec.PKG_GEARHEAD;

    private Context context;
    private Field svcField;
    private Object previousSvc;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        svcField = RootDb.class.getDeclaredField("svc");
        svcField.setAccessible(true);
        previousSvc = svcField.get(null);
    }

    @After
    public void tearDown() throws Exception {
        // Restore whatever was there so we don't leak the fake into other tests.
        svcField.set(null, previousSvc);
    }

    /** Installs a fake IPhixitRoot whose readPartitions returns {@code parts}. */
    private void installFake(final List<Partition> parts) throws Exception {
        IPhixitRoot fake = new IPhixitRoot.Stub() {
            @Override
            public List<Partition> readPartitions(String pkg) {
                return parts;
            }

            @Override
            public void writePartitions(List<Partition> p, int servingVersion) {
            }

            @Override
            public String query(String dbPath, String sql) {
                return "";
            }

            @Override
            public void execStatements(String dbPath, List<String> statements) {
            }

            @Override
            public int[] statOwner(String path) {
                return new int[]{0, 0};
            }

            @Override
            public void chownPath(String path, int uid, int gid) {
            }

            @Override
            public boolean deleteRecursive(String path) {
                return true;
            }

            @Override
            public void backupFile(String srcPath, String destPath, int uid, int gid) {
            }
        };
        svcField.set(null, fake);
    }

    /** Builds a single param_partition blob holding the given flags. */
    private static Partition partitionOf(long id, PhixitSnapshot.Flag... flags) {
        List<PhixitSnapshot.Flag> list = new ArrayList<PhixitSnapshot.Flag>(Arrays.asList(flags));
        byte[] blob = PhixitSnapshot.deflateRaw(PhixitSnapshot.encode(list));
        return new Partition(id, blob);
    }

    private static PhixitSnapshot.Flag boolFlag(String name, boolean value) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.numericName = false;
        f.type = value ? PhixitSnapshot.TYPE_BOOL_TRUE : PhixitSnapshot.TYPE_BOOL_FALSE;
        return f;
    }

    private PhixitEngine engine() {
        return new PhixitEngine(context, null);
    }

    private List<FlagSpec> oneBoolSpec(boolean value) {
        return Arrays.asList(FlagSpec.bool(PKG, "MyFeature__some_flag", value));
    }

    // -------------------------------------------------------------------------
    // true / false discrimination
    // -------------------------------------------------------------------------

    @Test
    public void allSpecsMatch_returnsTrue() throws Exception {
        installFake(Arrays.asList(
                partitionOf(1, boolFlag("MyFeature__some_flag", true))));

        assertTrue(engine().isAppliedStrict(oneBoolSpec(true)));
        // Lenient wrapper agrees.
        assertTrue(engine().isApplied(oneBoolSpec(true)));
    }

    @Test
    public void oneSpecMismatch_returnsFalse() throws Exception {
        // DB has the flag set to FALSE, but we assert TRUE -> mismatch.
        installFake(Arrays.asList(
                partitionOf(1, boolFlag("MyFeature__some_flag", false))));

        assertFalse(engine().isAppliedStrict(oneBoolSpec(true)));
        assertFalse(engine().isApplied(oneBoolSpec(true)));
    }

    @Test
    public void missingFlag_returnsFalse() throws Exception {
        // Partition decodes fine but does not contain the asserted flag.
        installFake(Arrays.asList(
                partitionOf(1, boolFlag("OtherFeature__unrelated", true))));

        assertFalse(engine().isAppliedStrict(oneBoolSpec(true)));
        assertFalse(engine().isApplied(oneBoolSpec(true)));
    }

    // -------------------------------------------------------------------------
    // structurally unreadable -> throws (UNKNOWN), lenient wrapper -> false
    // -------------------------------------------------------------------------

    @Test
    public void emptyPartitionList_throws() throws Exception {
        installFake(new ArrayList<Partition>()); // no partitions at all

        try {
            engine().isAppliedStrict(oneBoolSpec(true));
            fail("expected an exception for an empty partition list (structurally unreadable)");
        } catch (Exception expected) {
            // ok: caller maps this to UNKNOWN
        }
        // Lenient variant must swallow it to false.
        assertFalse(engine().isApplied(oneBoolSpec(true)));
    }

    @Test
    public void noDecodablePartition_throws() throws Exception {
        // A non-empty list whose only partition has an empty blob -> nothing decodable.
        installFake(Arrays.asList(new Partition(1, new byte[0])));

        try {
            engine().isAppliedStrict(oneBoolSpec(true));
            fail("expected an exception when no partition is decodable (structurally unreadable)");
        } catch (Exception expected) {
            // ok: caller maps this to UNKNOWN
        }
        assertFalse(engine().isApplied(oneBoolSpec(true)));
    }
}
