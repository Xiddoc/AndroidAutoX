package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.rm.rmswitch.RMSwitch;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Robolectric tests for {@link AppsList} (the "Select apps" screen of the
 * "patch custom apps" feature) and the selection-persistence half of its
 * {@link MyAdapter}.
 *
 * <p>This screen has two distinct behaviours worth pinning:
 *
 * <ul>
 *   <li><b>Full launchable enumeration + badging.</b> {@code AppsList.onCreate}
 *       enumerates <em>every</em> launchable app — those exposing an
 *       {@code ACTION_MAIN}/{@code CATEGORY_LAUNCHER} entry via
 *       {@code queryIntentActivities} — de-duped by package, and re-reads each via
 *       {@code getApplicationInfo(GET_META_DATA)}. Nothing is filtered out: each app
 *       is instead <em>badged</em> by {@link AppCompatibilityClassifier} into
 *       {@code NATIVE_AUTO} (declares the non-zero
 *       {@code com.google.android.gms.car.application} marker), {@code MIRROR_SHIM}
 *       (a known screen-mirroring shim), or {@code NEEDS_BRIDGE} (everything else,
 *       including apps with no metadata, a present-but-zero marker, or a missing
 *       launcher ApplicationInfo). We register fake launchable apps across these
 *       cases via {@link ShadowPackageManager} and assert the resulting badges.</li>
 *   <li><b>Pre-population.</b> Any package already stored in the
 *       {@value #PREF_NAME} SharedPreferences (key = packageName, value = label)
 *       loads as <em>checked</em>. A stored package that is no longer installed is
 *       still surfaced (so the user can un-select it), using the persisted label.</li>
 *   <li><b>Selection persistence (most important).</b> Toggling a row's switch
 *       writes {@code packageName -> label} into {@value #PREF_NAME}; toggling it
 *       again removes the key. This is driven through the adapter's real click
 *       listeners (rendered via {@code onCreateViewHolder}/{@code onBindViewHolder}),
 *       exercising the production {@code onClickSaveAppsWhiteList} path end to end.</li>
 * </ul>
 *
 * <p>The car-application marker is registered as an {@code int} metadata value
 * because the production code reads it with {@code Bundle.getInt(...)}; a real
 * Android-Auto app points it at an XML resource id, which is likewise a non-zero
 * int.
 *
 * <p><b>Label-resolution caveat.</b> Real Android-Auto apps resolve their display
 * label from {@code labelRes}; these tests set {@code nonLocalizedLabel} instead,
 * which {@code loadLabel()} falls back to off-device. So labels here exercise the
 * fallback path, not the resource-string path a device would use.
 *
 * <p><b>Isolation.</b> Each test gets a fresh {@link ShadowPackageManager}
 * (Robolectric resets shadows per test), so installed packages do not leak between
 * tests; {@link #setUp()} additionally clears the prefs slate.
 */
@RunWith(RobolectricTestRunner.class)
public class AppsListTest {

    private static final String CAR_META = "com.google.android.gms.car.application";

    /** Name of the SharedPreferences AppsList/MyAdapter read and write. */
    private static final String PREF_NAME = "appsListPref";

    /**
     * Measure spec the {@link #layoutRecycler} pass forces on the RecyclerView.
     * Tall enough that the {@code LinearLayoutManager} lays out every test row in
     * one shot rather than recycling, so {@link #findRowView} can find any package.
     */
    private static final int MEASURE_WIDTH_PX = 1000;
    private static final int MEASURE_HEIGHT_PX = 20000;

    private Context appContext;
    private ShadowPackageManager shadowPm;

    @Before
    public void setUp() {
        appContext = ApplicationProvider.getApplicationContext();
        shadowPm = shadowOf(appContext.getPackageManager());
        // Start each test from a clean prefs slate so persistence assertions are isolated.
        prefs().edit().clear().commit();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** Stores a selected app (packageName -> label) in prefs, like the production toggle. */
    private void selectApp(String pkg, String label) {
        prefs().edit().putString(pkg, label).commit();
    }

    /**
     * Shared core: registers a fake <em>launchable</em> installed app with the given
     * metadata bundle (which may be {@code null} to cover the no-metadata branch).
     *
     * <p>The production code now enumerates apps via {@code queryIntentActivities(
     * MAIN/LAUNCHER)} and re-reads each via {@code getApplicationInfo(pkg,
     * GET_META_DATA)}, so the package needs both a registered ApplicationInfo (with
     * metadata) and a MAIN/LAUNCHER activity that resolves.
     */
    private void installAppWithMeta(String pkg, String label, Bundle meta) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pkg;
        ai.name = label;
        ai.nonLocalizedLabel = label; // loadLabel() falls back to this off-device
        ai.metaData = meta;

        String activityName = pkg + ".MainActivity";
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = pkg;
        activityInfo.name = activityName;
        activityInfo.applicationInfo = ai;
        activityInfo.nonLocalizedLabel = label;

        PackageInfo pi = new PackageInfo();
        pi.packageName = pkg;
        pi.applicationInfo = ai;
        pi.activities = new ActivityInfo[]{activityInfo};
        shadowPm.installPackage(pi);

        // Make the package launchable so queryIntentActivities(MAIN/LAUNCHER) returns
        // a ResolveInfo carrying this activity (and thus its package).
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        shadowPm.addResolveInfoForIntentNoDefaults(launcherIntent(), resolveInfo);
    }

    /**
     * Registers a fake installed app tagged with a specific car-marker int value.
     * Use this only when the exact marker value is load-bearing (e.g. the present-
     * but-zero boundary); otherwise prefer {@link #installCarApp}/{@link #installPlainApp}.
     */
    private void installApp(String pkg, String label, int carMarkerValue) {
        Bundle meta = new Bundle();
        meta.putInt(CAR_META, carMarkerValue);
        installAppWithMeta(pkg, label, meta);
    }

    /** Registers a car-tagged app (non-zero marker) the filter must keep. */
    private void installCarApp(String pkg, String label) {
        Bundle meta = new Bundle();
        meta.putInt(CAR_META, 1);
        installAppWithMeta(pkg, label, meta);
    }

    /** Registers a plain app whose metadata bundle omits the marker entirely. */
    private void installPlainApp(String pkg, String label) {
        installAppWithMeta(pkg, label, new Bundle());
    }

    /** Registers an app whose {@code metaData} is null (covers the null-bundle branch). */
    private void installAppNoMetaData(String pkg, String label) {
        installAppWithMeta(pkg, label, null);
    }

    /** Builds the activity through its real lifecycle and returns it. */
    private AppsList buildActivity() {
        ActivityController<AppsList> controller =
                Robolectric.buildActivity(AppsList.class).create().start().resume();
        return controller.get();
    }

    private RecyclerView recyclerOf(AppsList activity) {
        RecyclerView rv = activity.findViewById(R.id.apps_info);
        assertNotNull("RecyclerView must be inflated", rv);
        assertNotNull("adapter must be set in onCreate", rv.getAdapter());
        return rv;
    }

    /**
     * Forces a real measure/layout pass on the RecyclerView so it creates, binds,
     * and attaches its row Views itself. Without this pass there are no child Views
     * to click or read — Robolectric does not lay out a RecyclerView automatically.
     *
     * <p>We lay out a deliberately tall viewport ({@value #MEASURE_HEIGHT_PX}px) so
     * the {@code LinearLayoutManager} renders <em>every</em> test row rather than
     * recycling some off-screen.
     *
     * <p>Note on the row-click index: the row {@code OnClickListener} (set in
     * {@code MyAdapter.onCreateViewHolder}) captures the creation-time {@code i} and
     * passes it to {@code onClickSaveAppsWhiteList}. That index is correct only
     * because {@code getItemViewType} returns a unique value per position, forcing
     * the RecyclerView to create a distinct holder per position — so each row is
     * created with {@code i == position}. (The {@code getAdapterPosition()} reads in
     * {@code onBindViewHolder} and the checkbox listener are a separate path, not
     * the one a bare {@code row.performClick()} drives.)
     */
    private void layoutRecycler(RecyclerView rv) {
        int width = View.MeasureSpec.makeMeasureSpec(MEASURE_WIDTH_PX, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(MEASURE_HEIGHT_PX, View.MeasureSpec.EXACTLY);
        rv.measure(width, height);
        rv.layout(0, 0, rv.getMeasuredWidth(), rv.getMeasuredHeight());
        // Guard: if the viewport were too short only some rows would render and
        // findRowView could silently miss one. Pin that the viewport fits them all.
        assertEquals("layout pass must render every row",
                rv.getAdapter().getItemCount(), rv.getChildCount());
    }

    /** The on-screen row View whose package-name TextView matches, or null. */
    private View findRowView(RecyclerView rv, String pkg) {
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            TextView pkgView = child.findViewById(R.id.app_package_name);
            if (pkgView != null && pkg.equals(pkgView.getText().toString())) {
                return child;
            }
        }
        return null;
    }

    /**
     * One seam for the Robolectric layout mechanic: builds the activity's recycler,
     * forces the layout pass, and returns the attached row View for {@code pkg}
     * (asserting it exists).
     */
    private View rowFor(AppsList activity, String pkg) {
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);
        View row = findRowView(rv, pkg);
        assertNotNull("row for " + pkg + " must be laid out", row);
        return row;
    }

    /** Pulls back the package + label + checked state of every laid-out row. */
    private Map<String, RowState> renderRows(AppsList activity) {
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);
        Map<String, RowState> byPackage = new HashMap<>();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View row = rv.getChildAt(i);
            TextView name = row.findViewById(R.id.app_name);
            TextView pkg = row.findViewById(R.id.app_package_name);
            TextView badge = row.findViewById(R.id.app_badge);
            RMSwitch sw = row.findViewById(R.id.checkbox_app);
            byPackage.put(pkg.getText().toString(),
                    new RowState(name.getText().toString(), sw.isChecked(),
                            badge.getText().toString()));
        }
        return byPackage;
    }

    /** Reads the laid-out rows top-to-bottom, preserving on-screen order. */
    private List<String> renderRowOrder(AppsList activity) {
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);
        List<String> order = new ArrayList<>();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View row = rv.getChildAt(i);
            TextView pkg = row.findViewById(R.id.app_package_name);
            order.add(pkg.getText().toString());
        }
        return order;
    }

    private static final class RowState {
        final String label;
        final boolean checked;
        final String badge;

        RowState(String label, boolean checked, String badge) {
            this.label = label;
            this.checked = checked;
            this.badge = badge;
        }

        @Override
        public String toString() {
            return "RowState{label='" + label + "', checked=" + checked
                    + ", badge='" + badge + "'}";
        }
    }

    // ------------------------------------------------------------------
    // enumeration: ALL launchable apps appear, badged by category
    // ------------------------------------------------------------------

    private String badge(int res) {
        return appContext.getString(res);
    }

    /**
     * Every launchable app now appears (the old car-only filter is gone), each
     * tagged by its compatibility category: a car app -> NATIVE, a known mirror
     * shim -> MIRROR, an arbitrary app -> NEEDS BRIDGE.
     */
    @Test
    public void enumeration_includesAllLaunchableApps_withCorrectBadges() {
        installCarApp("com.example.carapp", "Car App");
        installPlainApp("ru.inceptive.screentwoauto", "Screen2Auto");
        installPlainApp("com.anthropic.claude", "Claude");

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        assertTrue("car app must appear", rows.containsKey("com.example.carapp"));
        assertTrue("mirror shim must appear", rows.containsKey("ru.inceptive.screentwoauto"));
        assertTrue("arbitrary app must appear", rows.containsKey("com.anthropic.claude"));

        assertEquals(badge(R.string.badge_native_auto),
                rows.get("com.example.carapp").badge);
        assertEquals(badge(R.string.badge_mirror_shim),
                rows.get("ru.inceptive.screentwoauto").badge);
        assertEquals(badge(R.string.badge_needs_bridge),
                rows.get("com.anthropic.claude").badge);
    }

    /** A car app with a null metadata bundle still resolves to NEEDS_BRIDGE, not a crash. */
    @Test
    public void enumeration_nullMetaData_classifiesAsNeedsBridge() {
        installAppNoMetaData("com.example.nometa", "No Meta App");

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState state = rows.get("com.example.nometa");
        assertNotNull("app with null metaData must still appear", state);
        assertEquals(badge(R.string.badge_needs_bridge), state.badge);
    }

    /** Present-but-zero marker is NOT a declared car app -> NEEDS_BRIDGE (carApp != 0 boundary). */
    @Test
    public void enumeration_zeroMarker_classifiesAsNeedsBridge() {
        installApp("com.example.zeromarker", "Zero Marker App", 0);

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState state = rows.get("com.example.zeromarker");
        assertNotNull(state);
        assertEquals("present-but-zero marker is not native (carApp != 0 boundary)",
                badge(R.string.badge_needs_bridge), state.badge);
    }

    /** A plain app (metadata bundle present but no marker key) -> NEEDS_BRIDGE. */
    @Test
    public void enumeration_plainApp_classifiesAsNeedsBridge() {
        installPlainApp("com.example.plainapp", "Plain App");

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState state = rows.get("com.example.plainapp");
        assertNotNull(state);
        assertEquals(badge(R.string.badge_needs_bridge), state.badge);
    }

    /** The launcher intent AppsList builds, used to register edge-case resolve infos. */
    private static Intent launcherIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    }

    /**
     * A launcher resolve whose {@code activityInfo} is null is skipped (the
     * {@code ri.activityInfo == null} continue branch) without crashing — the real
     * car app still shows.
     */
    @Test
    public void enumeration_resolveWithNullActivityInfo_isSkipped() {
        installCarApp("com.example.carapp", "Car App");

        ResolveInfo bogus = new ResolveInfo();
        bogus.activityInfo = null;
        shadowPm.addResolveInfoForIntentNoDefaults(launcherIntent(), bogus);

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        assertTrue("real car app still appears", rows.containsKey("com.example.carapp"));
    }

    /**
     * A package that is launchable but has no installed ApplicationInfo makes
     * {@code getApplicationInfo} throw NameNotFoundException; that package is skipped
     * (the catch/continue branch) while the real car app still shows.
     */
    @Test
    public void enumeration_uninstalledLaunchablePackage_isSkipped() {
        installCarApp("com.example.carapp", "Car App");

        ActivityInfo ghostActivity = new ActivityInfo();
        ghostActivity.packageName = "com.example.ghostlauncher";
        ghostActivity.name = "com.example.ghostlauncher.Main";
        ResolveInfo ghost = new ResolveInfo();
        ghost.activityInfo = ghostActivity;
        shadowPm.addResolveInfoForIntentNoDefaults(launcherIntent(), ghost);

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        assertTrue("real car app still appears", rows.containsKey("com.example.carapp"));
        assertFalse("package with no ApplicationInfo is skipped",
                rows.containsKey("com.example.ghostlauncher"));
    }

    /**
     * A single package can expose more than one launcher activity (e.g. multiple
     * {@code MAIN/LAUNCHER} aliases). The picker enumerates per-package, so the
     * package must appear exactly once — pinning the {@code LinkedHashSet} multi-
     * activity de-dup in {@code AppsList} (distinct from the installed+prefs dedup).
     */
    @Test
    public void enumeration_packageWithTwoLauncherActivities_appearsExactlyOnce() {
        // installCarApp registers one launcher activity (pkg + ".MainActivity").
        installCarApp("com.example.multi", "Multi Launcher App");

        // Register a SECOND launcher activity for the SAME package.
        ActivityInfo secondActivity = new ActivityInfo();
        secondActivity.packageName = "com.example.multi";
        secondActivity.name = "com.example.multi.SecondMain";
        ResolveInfo secondResolve = new ResolveInfo();
        secondResolve.activityInfo = secondActivity;
        shadowPm.addResolveInfoForIntentNoDefaults(launcherIntent(), secondResolve);

        AppsList activity = buildActivity();
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);

        int rowsForPackage = 0;
        for (int i = 0; i < rv.getChildCount(); i++) {
            TextView pkg = rv.getChildAt(i).findViewById(R.id.app_package_name);
            if ("com.example.multi".equals(pkg.getText().toString())) {
                rowsForPackage++;
            }
        }
        assertEquals("a package with two launcher activities must appear once",
                1, rowsForPackage);
    }

    @Test
    public void filter_carTaggedApp_loadsUncheckedWithLabelAndPackage() {
        installApp("com.example.carapp", "Friendly Car App", 7);

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState state = rows.get("com.example.carapp");
        assertNotNull(state);
        assertEquals("Friendly Car App", state.label);
        assertFalse("an app not in prefs loads unchecked", state.checked);
    }

    // ------------------------------------------------------------------
    // pre-population from appsListPref
    // ------------------------------------------------------------------

    @Test
    public void prePopulation_installedCarAppInPrefs_loadsChecked() {
        installCarApp("com.example.carapp", "Car App");
        selectApp("com.example.carapp", "Car App");

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState state = rows.get("com.example.carapp");
        assertNotNull(state);
        assertTrue("an app already in appsListPref loads checked", state.checked);
    }

    /**
     * A car app that is BOTH installed and already in prefs must appear exactly
     * once, not twice — pins the {@code allEntries.remove(...)} de-dup in AppsList
     * (which prevents the leftover-prefs loop from re-adding it).
     */
    @Test
    public void prePopulation_installedCarAppAlsoInPrefs_appearsExactlyOnce() {
        installCarApp("com.example.carapp", "Car App");
        selectApp("com.example.carapp", "Car App");

        AppsList activity = buildActivity();
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);

        int rowsForPackage = 0;
        for (int i = 0; i < rv.getChildCount(); i++) {
            TextView pkg = rv.getChildAt(i).findViewById(R.id.app_package_name);
            if ("com.example.carapp".equals(pkg.getText().toString())) {
                rowsForPackage++;
            }
        }
        assertEquals("installed-and-stored app must not be duplicated", 1, rowsForPackage);
    }

    @Test
    public void prePopulation_storedAppNotInstalled_stillShownChecked() {
        // No matching installed app; only a leftover prefs entry. AppsList surfaces it
        // (with its persisted label) so the user can un-select it.
        installCarApp("com.example.carapp", "Car App");
        selectApp("com.example.ghost", "Ghost App");

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState ghost = rows.get("com.example.ghost");
        assertNotNull("stale prefs entry must still be listed", ghost);
        assertEquals("uses the persisted label", "Ghost App", ghost.label);
        assertTrue("stale prefs entry loads checked", ghost.checked);
    }

    @Test
    public void prePopulation_installedAppNotInPrefs_loadsUnchecked() {
        installCarApp("com.example.carapp", "Car App");
        selectApp("com.example.other", "Other");

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState state = rows.get("com.example.carapp");
        assertNotNull(state);
        assertFalse("installed car app absent from prefs loads unchecked", state.checked);
    }

    // ------------------------------------------------------------------
    // ordering (mirrors AppInfo.compareTo)
    // ------------------------------------------------------------------

    /**
     * Rows sort checked-before-unchecked, ties broken by name. Mirrors
     * {@link AppInfo#compareTo}. (None of these packages is in the hard-coded
     * known-AA allowlist, so only the checked-state and name tie-breakers apply.)
     */
    @Test
    public void ordering_checkedBeforeUnchecked_thenByName() {
        installCarApp("com.example.zulu", "Zulu");   // unchecked, name last
        installCarApp("com.example.alpha", "Alpha"); // unchecked, name first
        installCarApp("com.example.mike", "Mike");   // will be checked
        selectApp("com.example.mike", "Mike");

        AppsList activity = buildActivity();
        // Keep only the test packages: the app-under-test (and any Robolectric default
        // launchers) now also surface, since the picker lists ALL launchable apps.
        List<String> order = new ArrayList<>();
        for (String pkg : renderRowOrder(activity)) {
            if (pkg.startsWith("com.example.")) {
                order.add(pkg);
            }
        }

        assertEquals("checked app sorts first, then unchecked apps by name",
                Arrays.asList(
                        "com.example.mike",   // checked -> first
                        "com.example.alpha",  // unchecked, "Alpha" < "Zulu"
                        "com.example.zulu"),
                order);
    }

    /**
     * Characterizes current behaviour when a car app's {@code nonLocalizedLabel} is
     * null. {@code loadLabel()} then falls back to the package name, so the row
     * renders with the package name as its label and {@code AppInfo.compareTo} does
     * NOT NPE. (Pinned as-is; no production change.)
     */
    @Test
    public void edge_nullLabel_isToleratedAndShowsPackageName() {
        String pkg = "com.example.nolabel";
        Bundle meta = new Bundle();
        meta.putInt(CAR_META, 1);
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pkg;
        ai.nonLocalizedLabel = null; // force loadLabel() past its nonLocalizedLabel fallback
        ai.metaData = meta;

        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = pkg;
        activityInfo.name = pkg + ".MainActivity";
        activityInfo.applicationInfo = ai;
        activityInfo.nonLocalizedLabel = null;

        PackageInfo pi = new PackageInfo();
        pi.packageName = pkg;
        pi.applicationInfo = ai;
        pi.activities = new ActivityInfo[]{activityInfo};
        shadowPm.installPackage(pi);

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        shadowPm.addResolveInfoForIntentNoDefaults(launcherIntent(), resolveInfo);

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState state = rows.get("com.example.nolabel");
        assertNotNull("null-label car app is still listed (no crash)", state);
        assertEquals("loadLabel falls back to the package name",
                "com.example.nolabel", state.label);
    }

    // ------------------------------------------------------------------
    // selection persistence (drives the real adapter click path)
    // ------------------------------------------------------------------

    @Test
    public void toggle_on_writesPackageAndLabelToPrefs() {
        installCarApp("com.example.carapp", "Car App");

        AppsList activity = buildActivity();
        View row = rowFor(activity, "com.example.carapp");

        // Tapping the row toggles the entry (the row-level click listener calls the
        // production onClickSaveAppsWhiteList path with the captured creation-time i).
        row.performClick();

        assertEquals("toggling ON persists packageName -> label",
                "Car App", prefs().getString("com.example.carapp", null));
    }

    @Test
    public void toggle_off_removesPackageFromPrefs() {
        installCarApp("com.example.carapp", "Car App");
        selectApp("com.example.carapp", "Car App");

        AppsList activity = buildActivity();
        View row = rowFor(activity, "com.example.carapp");

        // It loaded checked (in prefs); a tap must remove it.
        row.performClick();

        // The remove path uses async apply(); flush the main looper before asserting.
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse("toggling OFF removes the key",
                prefs().contains("com.example.carapp"));
    }

    @Test
    public void toggle_onThenOff_isIdempotentRoundTrip() {
        installCarApp("com.example.carapp", "Car App");

        AppsList activity = buildActivity();
        View row = rowFor(activity, "com.example.carapp");

        row.performClick(); // ON
        assertEquals("Car App", prefs().getString("com.example.carapp", null));

        row.performClick(); // OFF (same AppInfo instance flipped back)
        // Remove path is async apply(); flush before asserting absence.
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(prefs().contains("com.example.carapp"));
    }

    @Test
    public void toggle_doesNotTouchOtherPrefEntries() {
        installCarApp("com.example.carapp", "Car App");
        installCarApp("com.example.carapp2", "Car App 2");
        selectApp("com.example.carapp2", "Car App 2");

        AppsList activity = buildActivity();
        View row = rowFor(activity, "com.example.carapp");

        row.performClick(); // toggle carapp ON

        assertEquals("toggling one app leaves other entries intact",
                "Car App 2", prefs().getString("com.example.carapp2", null));
        assertEquals("Car App", prefs().getString("com.example.carapp", null));
    }

    /**
     * Clicks the SWITCH ({@code R.id.checkbox_app}) — not the row — on a row that is
     * NOT position 0. The checkbox listener (set in {@code onBindViewHolder}) calls
     * {@code onClickSaveAppsWhiteList(v, getAdapterPosition())}, so this genuinely
     * exercises the {@code getAdapterPosition()} path that the row-click tests do
     * not. Characterization test: pins that the correct package (the one whose row
     * was clicked) is toggled, not a neighbour.
     */
    @Test
    public void toggleViaCheckbox_onNonFirstRow_togglesCorrectPackage() {
        // Three car apps. Names chosen so the sorted order is deterministic and all
        // unchecked rows sort by name: "App One" < "App Three" < "App Two".
        installCarApp("com.example.app1", "App One");
        installCarApp("com.example.app2", "App Two");
        installCarApp("com.example.app3", "App Three");

        AppsList activity = buildActivity();
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);

        // Target a row that is not the first one ("App Three" sorts in the middle).
        View row = findRowView(rv, "com.example.app3");
        assertNotNull("third car app row must be laid out", row);
        RMSwitch sw = row.findViewById(R.id.checkbox_app);
        assertNotNull(sw);

        sw.performClick();

        // Flush in case the toggle scheduled async work on the main looper.
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("checkbox click on a non-first row must toggle ITS package on",
                "App Three", prefs().getString("com.example.app3", null));
        assertFalse("neighbouring rows must be untouched",
                prefs().contains("com.example.app1"));
        assertFalse("neighbouring rows must be untouched",
                prefs().contains("com.example.app2"));
    }
}
