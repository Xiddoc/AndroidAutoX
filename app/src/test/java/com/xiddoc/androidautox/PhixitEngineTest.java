package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plain-JUnit + Mockito tests for {@link PhixitEngine}.
 *
 * <p>Deliberately NOT a Robolectric test: Robolectric loads production classes in a
 * sandbox classloader that JaCoCo's instrumentation cannot see, so a Robolectric run
 * reports 0% for the class under test. Running on the plain JUnit classloader lets
 * JaCoCo measure the engine. The engine's three device seams —
 * {@link MainActivity#runSuWithCmd(String)} (shell), {@link RootDb#readPartitions(String)}
 * / {@link RootDb#writePartitions(java.util.List, int)} (root SQLite) — plus
 * {@link android.util.Base64} (stubbed in android.jar) are mocked statically. The
 * engine's {@link SharedPreferences} comes from {@link FakeSharedPreferences}.
 */
public class PhixitEngineTest {

    private Context ctx;
    private FakeSharedPreferences prefs;
    private StringBuilder log;
    private MockedStatic<MainActivity> mainStatic;
    private MockedStatic<RootDb> rootStatic;
    private MockedStatic<android.util.Base64> base64Static;

    private List<Partition> written;
    private boolean writeThrows;

    /** Captured args of the last RootDb.exec(dbPath, statements) call (Heterodyne clear). */
    private String execDbPath;
    private List<String> execStatements;
    private boolean execThrows;

    /** Owner returned by the mocked {@link RootDb#statOwner(String)} (null = unknown). */
    private int[] statOwner;
    private static final int statOwnerUid = 10001;
    private static final int statOwnerGid = 10001;

    /** Toggles for the master-added best-effort error branches in applySpecs. */
    private boolean statOwnerThrows;
    private boolean chownThrows;
    private boolean deleteRecursiveResult = true;
    private boolean deleteRecursiveThrows;

    private final Map<String, List<Partition>> store =
            new HashMap<String, List<Partition>>();
    private final Set<String> readThrows = new HashSet<String>();
    private final Map<String, String> suOut = new LinkedHashMap<String, String>();

    @Before
    public void setUp() {
        prefs = new FakeSharedPreferences();
        ctx = mock(Context.class);
        lenient().when(ctx.getApplicationContext()).thenReturn(ctx);
        lenient().when(ctx.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(prefs);

        log = new StringBuilder();
        written = null;
        writeThrows = false;
        execDbPath = null;
        execStatements = null;
        execThrows = false;
        statOwnerThrows = false;
        chownThrows = false;
        deleteRecursiveResult = true;
        deleteRecursiveThrows = false;
        store.clear();
        readThrows.clear();
        suOut.clear();

        mainStatic = Mockito.mockStatic(MainActivity.class);
        mainStatic.when(() -> MainActivity.runSuWithCmd(Mockito.anyString()))
                .thenAnswer(inv -> {
                    String cmd = inv.getArgument(0);
                    StreamLogs s = new StreamLogs();
                    s.setOutputStreamLog(cmd);
                    String out = "";
                    for (Map.Entry<String, String> e : suOut.entrySet()) {
                        if (cmd.startsWith(e.getKey())) { out = e.getValue(); break; }
                    }
                    s.setInputStreamLog(out);
                    return s;
                });

        rootStatic = Mockito.mockStatic(RootDb.class);
        rootStatic.when(() -> RootDb.readPartitions(Mockito.anyString()))
                .thenAnswer(inv -> {
                    String pkg = inv.getArgument(0);
                    if (readThrows.contains(pkg)) throw new RuntimeException("read boom");
                    List<Partition> l = store.get(pkg);
                    return l != null ? l : new ArrayList<Partition>();
                });
        rootStatic.when(() -> RootDb.writePartitions(Mockito.anyList(), Mockito.anyInt()))
                .thenAnswer(inv -> {
                    if (writeThrows) throw new RuntimeException("write boom");
                    written = inv.getArgument(0);
                    return null;
                });
        // RootDb.exec(dbPath, List<String>) is the choke point the engine uses to clear the
        // Heterodyne committed-config bookkeeping after a count-changing edit. Capture it.
        rootStatic.when(() -> RootDb.exec(Mockito.anyString(), Mockito.anyList()))
                .thenAnswer(inv -> {
                    if (execThrows) throw new RuntimeException("exec boom");
                    execDbPath = inv.getArgument(0);
                    execStatements = inv.getArgument(1);
                    return null;
                });

        // The engine reads the DB's numeric owner via RootDb.statOwner (Os.stat in the
        // root process) and restores it via RootDb.chownPath -- not the old `stat -c`/
        // `chown <user:group>` shell pair. Default to a concrete owner so the
        // ownership-restore path runs; the "no owner" test overrides this to null.
        statOwner = new int[]{statOwnerUid, statOwnerGid};
        rootStatic.when(() -> RootDb.statOwner(Mockito.anyString()))
                .thenAnswer(inv -> {
                    if (statOwnerThrows) throw new RuntimeException("statOwner boom");
                    return statOwner;
                });
        rootStatic.when(() -> RootDb.chownPath(
                        Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
                .thenAnswer(inv -> {
                    if (chownThrows) throw new RuntimeException("chown boom");
                    return null;
                });
        rootStatic.when(() -> RootDb.deleteRecursive(Mockito.anyString()))
                .thenAnswer(inv -> {
                    if (deleteRecursiveThrows) throw new RuntimeException("rm boom");
                    return deleteRecursiveResult;
                });

        // android.util.Base64 is a no-op stub in android.jar; delegate to java.util.Base64
        // so the String baseline serialize/deserialize round-trip works off-device.
        // Production uses Base64.NO_WRAP flags only (see PhixitEngine.serialize/deserialize
        // Baseline) — single-line, standard alphabet, '=' padding — which maps 1:1 to the
        // JDK encoder/decoder used here.
        base64Static = Mockito.mockStatic(android.util.Base64.class);
        base64Static.when(() -> android.util.Base64.encodeToString(
                        Mockito.any(byte[].class), Mockito.anyInt()))
                .thenAnswer(inv -> Base64.getEncoder()
                        .encodeToString((byte[]) inv.getArgument(0)));
        base64Static.when(() -> android.util.Base64.decode(
                        Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(inv -> Base64.getDecoder()
                        .decode((String) inv.getArgument(0)));
    }

    @After
    public void tearDown() {
        mainStatic.close();
        rootStatic.close();
        base64Static.close();
    }

    // --- helpers -----------------------------------------------------------

    private PhixitEngine engine() {
        return new PhixitEngine(ctx, log);
    }

    private Partition partition(long id, PhixitSnapshot.Flag... flags) {
        byte[] blob = PhixitSnapshot.deflateRaw(PhixitSnapshot.encode(Arrays.asList(flags)));
        return new Partition(id, blob);
    }

    private PhixitSnapshot.Flag boolFlag(String name, boolean v) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.numericName = false;
        f.type = v ? PhixitSnapshot.TYPE_BOOL_TRUE : PhixitSnapshot.TYPE_BOOL_FALSE;
        return f;
    }

    private PhixitSnapshot.Flag longFlag(String name, long v) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.numericName = false;
        f.type = PhixitSnapshot.TYPE_LONG;
        f.longValue = v;
        return f;
    }

    private PhixitSnapshot.Flag numericFlag(long id) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = Long.toString(id);
        f.numericName = true;
        f.type = PhixitSnapshot.TYPE_BOOL_TRUE;
        return f;
    }

    private List<PhixitSnapshot.Flag> decodeWritten(int idx) {
        return PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(written.get(idx).blob));
    }

    // =======================================================================
    // applySpecs
    // =======================================================================

    @Test
    public void applySpecs_enforcing_capturesBaselineAndWrites() {
        suOut.put("getenforce", "Enforcing");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Foo__bar", false)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Foo__bar", true)), true);

        assertTrue(ok);
        assertEquals(1, written.size());
        assertEquals("B0", prefs.getString(
                PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "Foo__bar"), null));
        mainStatic.verify(() -> MainActivity.runSuWithCmd("setenforce 0"));
        mainStatic.verify(() -> MainActivity.runSuWithCmd("setenforce 1"));
        // Ownership is restored via the root-process chown (numeric uid/gid from statOwner),
        // not a `chown user:group` shell command.
        rootStatic.verify(() -> RootDb.chownPath(
                PhixitEngine.PHENO_DB, statOwnerUid, statOwnerGid));
        assertEquals(PhixitSnapshot.TYPE_BOOL_TRUE, decodeWritten(0).get(0).type);
    }

    @Test
    public void applySpecs_permissive_unknownOwner_skipsChownAndSetenforce() {
        suOut.put("getenforce", "Permissive");
        // statOwner failed to resolve the owner -> the ownership restore must be skipped.
        statOwner = null;
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, longFlag("Sys__t", 5L)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Sys__t", 99L)), false);

        assertTrue(ok);
        mainStatic.verify(() -> MainActivity.runSuWithCmd("setenforce 0"), Mockito.never());
        mainStatic.verify(() -> MainActivity.runSuWithCmd("setenforce 1"), Mockito.never());
        rootStatic.verify(() -> RootDb.chownPath(
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()), Mockito.never());
    }

    @Test
    public void applySpecs_statOwnerThrows_treatedAsUnknownOwner_andStillWrites() {
        // When the root-process stat blows up, the engine swallows it (best-effort) and
        // treats the owner as unknown, so it must NOT attempt a chown but still writes.
        suOut.put("getenforce", "Permissive");
        statOwnerThrows = true;
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Foo__bar", false)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Foo__bar", true)), false);

        assertTrue(ok);
        assertEquals(1, written.size());
        rootStatic.verify(() -> RootDb.chownPath(
                Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()), Mockito.never());
        assertTrue("statOwner failure should be logged",
                log.toString().contains("statOwner ERR"));
    }

    @Test
    public void applySpecs_chownPathThrows_isSwallowed_andApplySucceeds() {
        // A failure restoring ownership is non-fatal: the edit already landed.
        suOut.put("getenforce", "Permissive");
        chownThrows = true;
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Foo__bar", false)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Foo__bar", true)), false);

        assertTrue(ok);
        assertEquals(1, written.size());
        assertTrue("chownPath failure should be logged",
                log.toString().contains("chownPath ERR"));
    }

    @Test
    public void applySpecs_cacheNotFullyCleared_logsWarningButSucceeds() {
        // deleteRecursive returning false means the phenotype cache was not fully cleared;
        // it's non-fatal but must be surfaced in the log.
        suOut.put("getenforce", "Permissive");
        deleteRecursiveResult = false;
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Foo__bar", false)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Foo__bar", true)), false);

        assertTrue(ok);
        assertTrue("incomplete cache clear should be logged",
                log.toString().contains("phenotype cache not fully cleared"));
    }

    @Test
    public void applySpecs_cacheClearThrows_isSwallowed_andApplySucceeds() {
        suOut.put("getenforce", "Permissive");
        deleteRecursiveThrows = true;
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Foo__bar", false)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Foo__bar", true)), false);

        assertTrue(ok);
        assertTrue("cache clear exception should be logged",
                log.toString().contains("cache clear ERR"));
    }

    @Test
    public void applySpecs_gmsStillRunningPastTimeout_logsWarningButProceeds() {
        // pidof keeps reporting a live GMS pid, so waitForGmsStopped() times out (bounded)
        // and the engine proceeds with a logged warning rather than hanging forever.
        suOut.put("getenforce", "Permissive");
        suOut.put("pidof com.google.android.gms", "12345");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Foo__bar", false)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Foo__bar", true)), false);

        assertTrue(ok);
        assertTrue("force-stop timeout should be logged",
                log.toString().contains("GMS still running after force-stop timeout"));
    }

    @Test
    public void applySpecs_interruptedWhileWaitingForGms_stopsWaitingPromptly() {
        // pidof keeps reporting GMS alive so the engine would sleep between polls; pre-setting
        // the thread's interrupt flag makes the first Thread.sleep throw immediately, exercising
        // the InterruptedException handler (which re-asserts the interrupt and stops waiting).
        suOut.put("getenforce", "Permissive");
        suOut.put("pidof com.google.android.gms", "12345");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Foo__bar", false)))));

        Thread.currentThread().interrupt();
        boolean ok;
        try {
            ok = engine().applySpecs(
                    Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Foo__bar", true)), false);
        } finally {
            // Consume the interrupt flag the handler re-asserted so it doesn't leak to other tests.
            assertTrue("handler must re-assert the interrupt", Thread.interrupted());
        }
        // The edit still completes (the wait is best-effort), and the timeout warning is logged.
        assertTrue(ok);
        assertTrue(log.toString().contains("GMS still running after force-stop timeout"));
    }

    @Test
    public void applySpecs_appendsFlagWhenAbsent_andBaselineRecordsAbsent() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Other__flag", true)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "New__flag", true)), true);

        assertTrue(ok);
        assertEquals("A", prefs.getString(
                PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "New__flag"), null));
        assertEquals(2, decodeWritten(0).size());
    }

    // --- Heterodyne committed-config invalidation (issue #25) ------------------

    @Test
    public void applySpecs_addingFlag_clearsHeterodyneSyncState() {
        // Adding a brand-new flag changes the partition's flag count, which is exactly
        // what trips GMS's Heterodyne "conflicting flags / expected count" check. The
        // engine must therefore clear the committed-config bookkeeping for the package.
        suOut.put("getenforce", "Permissive");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Other__flag", true)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "New__flag", true)), false);

        assertTrue(ok);
        // exec was called against the phenotype DB with the clear statements for gearhead.
        assertEquals(PhixitEngine.PHENO_DB, execDbPath);
        assertEquals(HeterodyneSyncState.clearSyncSql(
                        Arrays.asList(FlagSpec.PKG_GEARHEAD)),
                execStatements);
        assertTrue(log.toString().contains("Heterodyne sync state cleared"));
    }

    @Test
    public void applySpecs_removingFlag_clearsHeterodyneSyncState() {
        // Dropping a flag also changes the count -> sync state must be cleared.
        suOut.put("getenforce", "Permissive");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Drop__me", true), boolFlag("Keep__me", true)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.remove(FlagSpec.PKG_GEARHEAD, "Drop__me")), false);

        assertTrue(ok);
        assertEquals(PhixitEngine.PHENO_DB, execDbPath);
        assertTrue(log.toString().contains("Heterodyne sync state cleared"));
    }

    @Test
    public void applySpecs_valueOnlyEdit_doesNotClearHeterodyneSyncState() {
        // Changing an existing flag's value keeps the count stable, so there is no
        // Heterodyne mismatch risk and no sync clear should be issued.
        suOut.put("getenforce", "Permissive");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, longFlag("Sys__t", 5L)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Sys__t", 99L)), false);

        assertTrue(ok);
        rootStatic.verify(() -> RootDb.exec(Mockito.anyString(), Mockito.anyList()),
                Mockito.never());
        assertFalse(log.toString().contains("Heterodyne sync state cleared"));
    }

    @Test
    public void applySpecs_heterodyneClearThrows_isSwallowed_andApplySucceeds() {
        // A failure clearing the sync state is non-fatal: the flag edit already landed.
        suOut.put("getenforce", "Permissive");
        execThrows = true;
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Other__flag", true)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "New__flag", true)), false);

        assertTrue(ok);
        assertTrue(log.toString().contains("Heterodyne sync clear ERR"));
    }

    @Test
    public void applySpecs_addingFlagButWriteFails_skipsHeterodyneClear() {
        // When the partition write itself fails, the engine must NOT attempt the sync-state
        // clear (it would key off a write that never happened).
        suOut.put("getenforce", "Permissive");
        writeThrows = true;
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Other__flag", true)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "New__flag", true)), false);

        assertFalse(ok);
        rootStatic.verify(() -> RootDb.exec(Mockito.anyString(), Mockito.anyList()),
                Mockito.never());
    }

    @Test
    public void applySpecs_onePackageWriteSucceeds_clearsEvenWhenAnotherPackageFailed() {
        // Issue #25, risk #3 (gating bug): the clear must be gated on the WRITE succeeding,
        // not on the global `ok`. Here gearhead's count-changing edit is written, but a
        // second package fails to decode (ok=false). The previous code gated the clear on
        // `ok` and would skip it -- leaving gearhead in exactly the crash condition. The
        // fix gates on the write outcome, so gearhead is still healed.
        suOut.put("getenforce", "Permissive");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Other__flag", true)))));
        // A corrupt (undecodable) blob for the second package -> decode failure -> ok=false.
        store.put(FlagSpec.PKG_CAR, new ArrayList<Partition>(Arrays.asList(
                new Partition(2, new byte[]{1, 2, 3}))));

        boolean ok = engine().applySpecs(Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "New__flag", true),
                FlagSpec.bool(FlagSpec.PKG_CAR, "Whatever__flag", true)), false);

        assertFalse("a package failed to decode -> overall not ok", ok);
        // ...but gearhead's successful count-changing write is still healed.
        assertEquals(PhixitEngine.PHENO_DB, execDbPath);
        assertEquals(HeterodyneSyncState.clearSyncSql(
                        Arrays.asList(FlagSpec.PKG_GEARHEAD)),
                execStatements);
        assertTrue(log.toString().contains("Heterodyne sync state cleared"));
    }

    @Test
    public void applySpecs_readError_marksNotOk_andWritesNothing() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        readThrows.add(FlagSpec.PKG_GEARHEAD);

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "F__a", true)), false);

        assertFalse(ok);
        rootStatic.verify(() -> RootDb.writePartitions(Mockito.anyList(), Mockito.anyInt()),
                Mockito.never());
        assertTrue(log.toString().contains("read ERR"));
    }

    @Test
    public void applySpecs_emptyPartitionList_marksNotOk() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>());

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "F__a", true)), false);

        assertFalse(ok);
        assertTrue(log.toString().contains("no partitions matched"));
    }

    @Test
    public void applySpecs_skipsNullAndEmptyBlobs_andWritesGoodPartition() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        List<Partition> parts = new ArrayList<Partition>();
        parts.add(new Partition(1, null));
        parts.add(new Partition(2, new byte[0]));
        parts.add(partition(4, boolFlag("F__a", false)));
        store.put(FlagSpec.PKG_GEARHEAD, parts);

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "F__a", true)), false);

        assertTrue(ok);
        assertEquals(1, written.size());
        assertEquals(4L, written.get(0).id);
    }

    @Test
    public void applySpecs_decodeException_marksNotOk_butWritesGoodPartition() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                new Partition(3, new byte[]{1, 2, 3}),
                partition(4, boolFlag("F__a", false)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "F__a", true)), false);

        assertFalse(ok);
        assertTrue(log.toString().contains("decode EXCEPTION"));
        assertEquals(1, written.size());
        assertEquals(4L, written.get(0).id);
    }

    @Test
    public void applySpecs_removeSpec_dropsFlag() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Drop__me", true), boolFlag("Keep__me", true)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.remove(FlagSpec.PKG_GEARHEAD, "Drop__me")), false);

        assertTrue(ok);
        List<PhixitSnapshot.Flag> out = decodeWritten(0);
        assertEquals(1, out.size());
        assertEquals("Keep__me", out.get(0).name);
    }

    @Test
    public void applySpecs_removeSpec_absentFlag_isNoOp() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Keep__me", true)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.remove(FlagSpec.PKG_GEARHEAD, "Missing__flag")), false);

        assertTrue(ok);
        assertEquals(1, decodeWritten(0).size());
    }

    @Test
    public void applySpecs_writeError_marksNotOk() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        writeThrows = true;
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("F__a", false)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "F__a", true)), false);

        assertFalse(ok);
        assertTrue(log.toString().contains("apply ERR"));
    }

    @Test
    public void applySpecs_captureBaseline_doesNotOverwriteExisting() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        prefs.edit().putString(
                PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "F__a"), "L42").apply();
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, longFlag("F__a", 7L)))));

        engine().applySpecs(
                Arrays.asList(FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "F__a", 9L)), true);

        assertEquals("L42", prefs.getString(
                PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "F__a"), null));
    }

    @Test
    public void applySpecs_baselineForLongDoubleStringBytes_serializeAllTags() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        PhixitSnapshot.Flag dbl = new PhixitSnapshot.Flag();
        dbl.name = "D__f"; dbl.type = PhixitSnapshot.TYPE_DOUBLE;
        dbl.doubleBits = Double.doubleToRawLongBits(1.5);
        PhixitSnapshot.Flag str = new PhixitSnapshot.Flag();
        str.name = "S__f"; str.type = PhixitSnapshot.TYPE_STRING; str.stringValue = "hello";
        PhixitSnapshot.Flag byt = new PhixitSnapshot.Flag();
        byt.name = "X__f"; byt.type = PhixitSnapshot.TYPE_BYTES; byt.bytesValue = new byte[]{0x0A, (byte) 0xFF};
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, longFlag("L__f", 3L), dbl, str, byt))));

        engine().applySpecs(Arrays.asList(
                FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "L__f", 100L),
                FlagSpec.dbl(FlagSpec.PKG_GEARHEAD, "D__f", 2.5),
                FlagSpec.str(FlagSpec.PKG_GEARHEAD, "S__f", "new"),
                FlagSpec.bytes(FlagSpec.PKG_GEARHEAD, "X__f", new byte[]{9})), true);

        assertEquals("L3", prefs.getString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "L__f"), null));
        assertEquals("D" + Double.doubleToRawLongBits(1.5),
                prefs.getString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "D__f"), null));
        assertTrue(prefs.getString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "S__f"), "")
                .startsWith("S"));
        assertEquals("X0AFF",
                prefs.getString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "X__f"), null));
    }

    @Test
    public void applySpecs_skipsNumericNamedFlags_inApplyAndBaselineScan() {
        // A numeric-named flag precedes the target in the partition so both the
        // apply-time find (applySpecToList) and the baseline scan iterate past it
        // (exercising the numericName arm of those loops).
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, numericFlag(7), boolFlag("Target__f", false)))));

        boolean ok = engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Target__f", true)), true);

        assertTrue(ok);
        assertEquals("B0", prefs.getString(
                PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "Target__f"), null));
        Map<String, PhixitSnapshot.Flag> m = new HashMap<>();
        for (PhixitSnapshot.Flag f : decodeWritten(0)) m.put(f.name, f);
        assertEquals(PhixitSnapshot.TYPE_BOOL_TRUE, m.get("Target__f").type);
    }

    @Test
    public void applySpecs_baselineForBoolTrue_serializesB1() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("B__t", true)))));
        engine().applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "B__t", false)), true);
        assertEquals("B1", prefs.getString(
                PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "B__t"), null));
    }

    // =======================================================================
    // revertSpecs
    // =======================================================================

    @Test
    public void revertSpecs_restoresBaselineValues_andClearsKeys() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        prefs.edit()
                .putString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "WasAbsent__f"), "A")
                .putString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "HadLong__f"), "L7")
                .apply();
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("WasAbsent__f", true), longFlag("HadLong__f", 99L),
                        boolFlag("NoBaseline__f", true)))));

        List<FlagSpec> applied = Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "WasAbsent__f", true),
                FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "HadLong__f", 99L),
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "NoBaseline__f", true));
        boolean ok = engine().revertSpecs(applied);

        assertTrue(ok);
        List<PhixitSnapshot.Flag> out = decodeWritten(0);
        assertEquals(1, out.size());
        assertEquals("HadLong__f", out.get(0).name);
        assertEquals(7L, out.get(0).longValue);
        assertFalse(prefs.contains(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "WasAbsent__f")));
        assertFalse(prefs.contains(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "HadLong__f")));
    }

    @Test
    public void revertSpecs_deserializesEveryBaselineTag() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        String bb64 = "S" + Base64.getEncoder().encodeToString(
                "rev".getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "Bool__f"), "B1")
                .putString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "Long__f"), "L5")
                .putString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "Dbl__f"),
                        "D" + Double.doubleToRawLongBits(2.25))
                .putString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "Str__f"), bb64)
                .putString(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "Byt__f"), "X0AFF")
                .apply();
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Bool__f", false), longFlag("Long__f", 0L),
                        boolFlag("Dbl__f", false), boolFlag("Str__f", false),
                        boolFlag("Byt__f", false)))));

        List<FlagSpec> applied = Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Bool__f", false),
                FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Long__f", 1L),
                FlagSpec.dbl(FlagSpec.PKG_GEARHEAD, "Dbl__f", 0.0),
                FlagSpec.str(FlagSpec.PKG_GEARHEAD, "Str__f", "x"),
                FlagSpec.bytes(FlagSpec.PKG_GEARHEAD, "Byt__f", new byte[]{0}));
        boolean ok = engine().revertSpecs(applied);
        assertTrue(ok);

        Map<String, PhixitSnapshot.Flag> m = new HashMap<>();
        for (PhixitSnapshot.Flag f : decodeWritten(0)) m.put(f.name, f);
        assertEquals(PhixitSnapshot.TYPE_BOOL_TRUE, m.get("Bool__f").type);
        assertEquals(5L, m.get("Long__f").longValue);
        assertEquals(2.25, Double.longBitsToDouble(m.get("Dbl__f").doubleBits), 0.0001);
        assertEquals("rev", m.get("Str__f").stringValue);
        assertEquals(2, m.get("Byt__f").bytesValue.length);
    }

    // =======================================================================
    // end-to-end apply -> isApplied -> revert -> isApplied(baseline)
    // =======================================================================

    @Test
    public void endToEnd_apply_isApplied_revert_isAppliedBaseline() {
        // Real PhixitSnapshot-encoded data round-tripped through the mocked partition
        // store: a starting partition is encoded, applySpecs() captures the baseline and
        // writes the override, we feed the written blob back as the new DB state, then
        // revertSpecs() restores it. isApplied() is asserted true at both ends.
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Coolwalk__on", false), longFlag("Hun__ms", 3000L)))));

        List<FlagSpec> specs = Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__on", true),
                FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Hun__ms", 8000L));

        // apply (capture baseline) and publish the result back as the live DB state
        assertTrue(engine().applySpecs(specs, true));
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(written));
        assertTrue(engine().isApplied(specs));

        // revert restores the captured baseline; the reverted blob becomes the new state
        assertTrue(engine().revertSpecs(specs));
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(written));

        List<FlagSpec> baseline = Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Coolwalk__on", false),
                FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Hun__ms", 3000L));
        assertTrue(engine().isApplied(baseline));
        // baseline keys are cleared after a successful revert
        assertFalse(prefs.contains(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "Coolwalk__on")));
        assertFalse(prefs.contains(PhixitEngine.baselineKey(FlagSpec.PKG_GEARHEAD, "Hun__ms")));
    }

    // =======================================================================
    // hasBaseline
    // =======================================================================

    @Test
    public void hasBaseline_trueWhenAnyFlagCaptured_falseOtherwise_nullForUnknownTweak() {
        PhixitEngine e = engine();
        assertFalse(e.hasBaseline("aa_material_you"));
        assertFalse(e.hasBaseline("not_a_tweak"));
        prefs.edit().putString(PhixitEngine.baselineKey(
                FlagSpec.PKG_GEARHEAD, "SystemUi__material_you_settings_enabled"), "B0").apply();
        assertTrue(e.hasBaseline("aa_material_you"));
    }

    // =======================================================================
    // isApplied
    // =======================================================================

    @Test
    public void isApplied_true_whenAllFlagsMatch() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Foo__on", true), longFlag("Foo__n", 5L)))));
        assertTrue(engine().isApplied(Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Foo__on", true),
                FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Foo__n", 5L))));
    }

    @Test
    public void isApplied_false_whenValueDiffers() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, longFlag("Foo__n", 4L)))));
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "Foo__n", 5L))));
    }

    @Test
    public void isApplied_false_whenExpectedFlagMissing() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Other__f", true)))));
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Foo__on", true))));
    }

    @Test
    public void isApplied_removeSpec_trueWhenAbsent_falseWhenPresent() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Keep__f", true)))));
        assertTrue(engine().isApplied(Arrays.asList(
                FlagSpec.remove(FlagSpec.PKG_GEARHEAD, "Gone__f"))));
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("Gone__f", true)))));
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.remove(FlagSpec.PKG_GEARHEAD, "Gone__f"))));
    }

    @Test
    public void isApplied_false_onReadError() {
        readThrows.add(FlagSpec.PKG_GEARHEAD);
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "F__a", true))));
    }

    @Test
    public void isApplied_false_onEmptyPartitions() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>());
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "F__a", true))));
    }

    @Test
    public void isApplied_false_onDecodeError() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                new Partition(1, new byte[]{1, 2, 3}))));
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "F__a", true))));
    }

    @Test
    public void isApplied_false_whenOnlyNullOrEmptyBlobs() {
        List<Partition> parts = new ArrayList<Partition>();
        parts.add(new Partition(1, null));
        parts.add(new Partition(2, new byte[0]));
        store.put(FlagSpec.PKG_GEARHEAD, parts);
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "F__a", true))));
    }

    @Test
    public void isApplied_ignoresNumericNamedFlags_whenFinding() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, numericFlag(42), boolFlag("Real__f", true)))));
        assertTrue(engine().isApplied(Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "Real__f", true))));
    }

    @Test
    public void isApplied_coversAllValueEqualsTypeBranches() {
        PhixitSnapshot.Flag dbl = new PhixitSnapshot.Flag();
        dbl.name = "D__f"; dbl.type = PhixitSnapshot.TYPE_DOUBLE;
        dbl.doubleBits = Double.doubleToRawLongBits(3.5);
        PhixitSnapshot.Flag str = new PhixitSnapshot.Flag();
        str.name = "S__f"; str.type = PhixitSnapshot.TYPE_STRING; str.stringValue = "v";
        PhixitSnapshot.Flag byt = new PhixitSnapshot.Flag();
        byt.name = "X__f"; byt.type = PhixitSnapshot.TYPE_BYTES; byt.bytesValue = new byte[]{7, 8};
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("B__f", true), longFlag("L__f", 9L), dbl, str, byt))));
        assertTrue(engine().isApplied(Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "B__f", true),
                FlagSpec.lng(FlagSpec.PKG_GEARHEAD, "L__f", 9L),
                FlagSpec.dbl(FlagSpec.PKG_GEARHEAD, "D__f", 3.5),
                FlagSpec.str(FlagSpec.PKG_GEARHEAD, "S__f", "v"),
                FlagSpec.bytes(FlagSpec.PKG_GEARHEAD, "X__f", new byte[]{7, 8}))));
    }

    @Test
    public void isApplied_false_whenTypeMismatch() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, longFlag("M__f", 1L)))));
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "M__f", true))));
    }

    @Test
    public void isApplied_false_whenStringDiffers_and_whenBytesDiffer() {
        PhixitSnapshot.Flag str = new PhixitSnapshot.Flag();
        str.name = "S__f"; str.type = PhixitSnapshot.TYPE_STRING; str.stringValue = "a";
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, str))));
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.str(FlagSpec.PKG_GEARHEAD, "S__f", "b"))));

        PhixitSnapshot.Flag byt = new PhixitSnapshot.Flag();
        byt.name = "X__f"; byt.type = PhixitSnapshot.TYPE_BYTES; byt.bytesValue = new byte[]{1};
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, byt))));
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.bytes(FlagSpec.PKG_GEARHEAD, "X__f", new byte[]{2}))));
    }

    @Test
    public void isApplied_false_whenDoubleDiffers() {
        PhixitSnapshot.Flag dbl = new PhixitSnapshot.Flag();
        dbl.name = "D__f"; dbl.type = PhixitSnapshot.TYPE_DOUBLE;
        dbl.doubleBits = Double.doubleToRawLongBits(1.0);
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, dbl))));
        assertFalse(engine().isApplied(Arrays.asList(
                FlagSpec.dbl(FlagSpec.PKG_GEARHEAD, "D__f", 2.0))));
    }

    @Test
    public void valueEquals_stringNullArm_isFalse_andEqualArmTrue() {
        // Direct call: the codec normalizes a null stringValue to "" on decode, so the
        // `a.stringValue == null` arm of valueEquals is only reachable here.
        PhixitSnapshot.Flag a = new PhixitSnapshot.Flag();
        a.type = PhixitSnapshot.TYPE_STRING; a.stringValue = null;
        PhixitSnapshot.Flag b = new PhixitSnapshot.Flag();
        b.type = PhixitSnapshot.TYPE_STRING; b.stringValue = "x";
        assertFalse(PhixitEngine.valueEquals(a, b));

        a.stringValue = "x";
        assertTrue(PhixitEngine.valueEquals(a, b));
    }

    // =======================================================================
    // readLong
    // =======================================================================

    @Test
    public void readLong_returnsValueWhenPresent() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, longFlag("N__f", 1234L)))));
        assertEquals(1234L, engine().readLong(FlagSpec.PKG_GEARHEAD, "N__f", -1L));
    }

    @Test
    public void readLong_returnsDefaultWhenAbsent() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, longFlag("Other__f", 5L)))));
        assertEquals(-9L, engine().readLong(FlagSpec.PKG_GEARHEAD, "Missing__f", -9L));
    }

    @Test
    public void readLong_returnsDefaultOnReadError() {
        readThrows.add(FlagSpec.PKG_GEARHEAD);
        assertEquals(42L, engine().readLong(FlagSpec.PKG_GEARHEAD, "N__f", 42L));
    }

    @Test
    public void readLong_skipsNullEmptyBlobs_andDecodeErrors_andWrongType() {
        List<Partition> parts = new ArrayList<Partition>();
        parts.add(new Partition(1, null));
        parts.add(new Partition(2, new byte[0]));
        parts.add(new Partition(3, new byte[]{9, 9, 9}));
        parts.add(partition(4, boolFlag("N__f", true)));
        parts.add(partition(5, longFlag("N__f", 7L)));
        store.put(FlagSpec.PKG_GEARHEAD, parts);
        assertEquals(7L, engine().readLong(FlagSpec.PKG_GEARHEAD, "N__f", -1L));
    }

    @Test
    public void readLong_ignoresNumericNamedFlag() {
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, numericFlag(8)))));
        assertEquals(-1L, engine().readLong(FlagSpec.PKG_GEARHEAD, "8", -1L));
    }

    // =======================================================================
    // serializeBaseline / deserializeBaseline edge branches (package-private)
    // =======================================================================

    @Test
    public void serializeBaseline_nullFlag_isAbsentSentinel() {
        assertEquals("A", PhixitEngine.serializeBaseline(null));
    }

    @Test
    public void serializeBaseline_unknownType_fallsToAbsentSentinel() {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = "U__f";
        f.type = 99; // not a known TYPE_*
        assertEquals("A", PhixitEngine.serializeBaseline(f));
    }

    @Test
    public void serializeBaseline_stringWithNullValue_hitsCatch() {
        // f.stringValue.getBytes(..) NPEs -> caught -> bare "S"
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = "S__f";
        f.type = PhixitSnapshot.TYPE_STRING;
        f.stringValue = null;
        assertEquals("S", PhixitEngine.serializeBaseline(f));
    }

    @Test
    public void deserializeBaseline_unknownTag_becomesRemove() {
        FlagSpec s = PhixitEngine.deserializeBaseline(FlagSpec.PKG_GEARHEAD, "Z__f", "Zxyz");
        assertTrue(s.remove);
    }

    @Test
    public void deserializeBaseline_invalidBase64String_recoversToEmpty() {
        // body is not valid base64 -> decode throws -> caught -> empty string
        FlagSpec s = PhixitEngine.deserializeBaseline(FlagSpec.PKG_GEARHEAD, "S__f", "S@@@not-base64@@@");
        assertEquals(PhixitSnapshot.TYPE_STRING, s.flag.type);
        assertEquals("", s.flag.stringValue);
    }

    // =======================================================================
    // isAndroidAutoProjecting
    // =======================================================================

    @Test
    public void projecting_trueWhenOutputIsNull() {
        // getInputStreamLog() returns null -> the `out == null` arm of line ... is taken.
        StreamLogs nullOut = mock(StreamLogs.class);
        lenient().when(nullOut.getInputStreamLog()).thenReturn(null);
        mainStatic.when(() -> MainActivity.runSuWithCmd(
                Mockito.startsWith("dumpsys activity services"))).thenReturn(nullOut);
        assertTrue(engine().isAndroidAutoProjecting());
    }

    @Test
    public void projecting_trueWhenOutputEmpty() {
        assertTrue(engine().isAndroidAutoProjecting());
    }

    @Test
    public void projecting_trueWhenForegroundMarkerPresent() {
        suOut.put("dumpsys activity services", "ServiceRecord ... isForeground=true ...");
        assertTrue(engine().isAndroidAutoProjecting());
    }

    @Test
    public void projecting_trueWhenForegroundServiceTypeMarkerPresent() {
        suOut.put("dumpsys activity services", "foregroundServiceType=mediaProjection");
        assertTrue(engine().isAndroidAutoProjecting());
    }

    @Test
    public void projecting_falseWhenNoServiceRecords() {
        suOut.put("dumpsys activity services", "(nothing)");
        assertFalse(engine().isAndroidAutoProjecting());
    }

    @Test
    public void projecting_trueWhenServiceRecordsButNoForegroundMarker() {
        suOut.put("dumpsys activity services", "ServiceRecord{abc gearhead}");
        assertTrue(engine().isAndroidAutoProjecting());
    }

    // =======================================================================
    // constructor null-log fallback
    // =======================================================================

    @Test
    public void constructor_nullLog_usesInternalBuffer() {
        suOut.put("getenforce", "Permissive");
        suOut.put("stat -c", "x:y");
        store.put(FlagSpec.PKG_GEARHEAD, new ArrayList<Partition>(Arrays.asList(
                partition(1, boolFlag("F__a", false)))));
        PhixitEngine e = new PhixitEngine(ctx, null);
        assertTrue(e.applySpecs(
                Arrays.asList(FlagSpec.bool(FlagSpec.PKG_GEARHEAD, "F__a", true)), false));
    }
}
