package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Robolectric tests for {@link AppsList} (the "Select apps" screen of the
 * "patch custom apps" feature) and the selection-persistence half of its
 * {@link MyAdapter}.
 *
 * <p>This screen has two distinct behaviours worth pinning:
 *
 * <ul>
 *   <li><b>Car-app filter.</b> {@code AppsList.onCreate} enumerates every
 *       installed app via {@code PackageManager.getInstalledApplications(GET_META_DATA)}
 *       and keeps <em>only</em> those whose manifest metadata declares a non-zero
 *       {@code com.google.android.gms.car.application} entry (the Android-Auto
 *       compatibility marker). Apps without that metadata — or with no metadata at
 *       all — are excluded. We register fake apps both with and without the marker
 *       via {@link ShadowPackageManager} and assert the resulting list.</li>
 *   <li><b>Pre-population.</b> Any package already stored in the {@code appsListPref}
 *       SharedPreferences (key = packageName, value = label) loads as
 *       <em>checked</em>. A stored package that is no longer installed is still
 *       surfaced (so the user can un-select it), using the persisted label.</li>
 *   <li><b>Selection persistence (most important).</b> Toggling a row's switch
 *       writes {@code packageName -> label} into {@code appsListPref}; toggling it
 *       again removes the key. This is driven through the adapter's real row click
 *       listener (rendered via {@code onCreateViewHolder}/{@code onBindViewHolder}),
 *       exercising the production {@code onClickSaveAppsWhiteList} path end to end.</li>
 * </ul>
 *
 * <p>The car-application marker is registered as an {@code int} metadata value
 * because the production code reads it with {@code Bundle.getInt(...)}; a real
 * Android-Auto app points it at an XML resource id, which is likewise a non-zero
 * int.
 */
@RunWith(RobolectricTestRunner.class)
public class AppsListTest {

    private static final String CAR_META = "com.google.android.gms.car.application";

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
        return appContext.getSharedPreferences("appsListPref", 0);
    }

    /**
     * Registers a fake installed app. When {@code carMarkerValue != 0} the app is
     * tagged with the {@code com.google.android.gms.car.application} metadata so the
     * filter treats it as Android-Auto compatible. A {@code carMarkerValue} of 0
     * registers a plain (non-car) app — its metadata bundle deliberately omits the
     * marker so the code path that reads a 0 (and skips it) is exercised. Passing a
     * {@code null} bundle (see {@link #installAppNoMetaData}) covers the
     * NullPointerException branch.
     */
    private void installApp(String pkg, String label, int carMarkerValue) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pkg;
        ai.name = label;
        ai.nonLocalizedLabel = label; // loadLabel() falls back to this off-device
        Bundle meta = new Bundle();
        if (carMarkerValue != 0) {
            meta.putInt(CAR_META, carMarkerValue);
        }
        ai.metaData = meta;

        PackageInfo pi = new PackageInfo();
        pi.packageName = pkg;
        pi.applicationInfo = ai;
        shadowPm.installPackage(pi);
    }

    /** Registers an app whose {@code metaData} is null (covers the NPE-catch branch). */
    private void installAppNoMetaData(String pkg, String label) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pkg;
        ai.name = label;
        ai.nonLocalizedLabel = label;
        ai.metaData = null;

        PackageInfo pi = new PackageInfo();
        pi.packageName = pkg;
        pi.applicationInfo = ai;
        shadowPm.installPackage(pi);
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
     * Forces a real measure/layout pass on the RecyclerView so it binds its rows
     * itself. This is essential because {@link MyAdapter#onBindViewHolder} reads
     * {@code getAdapterPosition()} (ignoring the position argument), which only
     * returns a valid index for holders the RecyclerView itself attached — calling
     * {@code bindViewHolder} on a detached holder yields {@code NO_POSITION} (-1).
     * Laying out a tall viewport renders every row.
     */
    private void layoutRecycler(RecyclerView rv) {
        int width = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY);
        // Tall enough that the LinearLayoutManager lays out every test row.
        int height = View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.EXACTLY);
        rv.measure(width, height);
        rv.layout(0, 0, rv.getMeasuredWidth(), rv.getMeasuredHeight());
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

    /** Pulls back the package + label + checked state of every laid-out row. */
    private Map<String, RowState> renderRows(AppsList activity) {
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);
        Map<String, RowState> byPackage = new HashMap<>();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View row = rv.getChildAt(i);
            TextView name = row.findViewById(R.id.app_name);
            TextView pkg = row.findViewById(R.id.app_package_name);
            RMSwitch sw = row.findViewById(R.id.checkbox_app);
            byPackage.put(pkg.getText().toString(),
                    new RowState(name.getText().toString(), sw.isChecked()));
        }
        return byPackage;
    }

    private static final class RowState {
        final String label;
        final boolean checked;

        RowState(String label, boolean checked) {
            this.label = label;
            this.checked = checked;
        }
    }

    // ------------------------------------------------------------------
    // car-app filter
    // ------------------------------------------------------------------

    @Test
    public void filter_keepsOnlyCarTaggedApps() {
        installApp("com.example.carapp", "Car App", 1);
        installApp("com.example.plainapp", "Plain App", 0);

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        assertTrue("car-tagged app must appear", rows.containsKey("com.example.carapp"));
        assertFalse("non-car app must be filtered out", rows.containsKey("com.example.plainapp"));
    }

    @Test
    public void filter_excludesAppWithoutMetaData() {
        installApp("com.example.carapp", "Car App", 1);
        installAppNoMetaData("com.example.nometa", "No Meta App");

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        assertTrue(rows.containsKey("com.example.carapp"));
        assertFalse("app with null metaData must be skipped (NPE branch)",
                rows.containsKey("com.example.nometa"));
    }

    @Test
    public void filter_noCarApps_yieldsEmptyList() {
        installApp("com.example.plainapp", "Plain App", 0);
        installAppNoMetaData("com.example.nometa", "No Meta App");

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        assertTrue("no car apps installed -> nothing listed", rows.isEmpty());
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
        installApp("com.example.carapp", "Car App", 1);
        prefs().edit().putString("com.example.carapp", "Car App").commit();

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState state = rows.get("com.example.carapp");
        assertNotNull(state);
        assertTrue("an app already in appsListPref loads checked", state.checked);
    }

    @Test
    public void prePopulation_storedAppNotInstalled_stillShownChecked() {
        // No matching installed app; only a leftover prefs entry. AppsList surfaces it
        // (with its persisted label) so the user can un-select it.
        installApp("com.example.carapp", "Car App", 1);
        prefs().edit().putString("com.example.ghost", "Ghost App").commit();

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState ghost = rows.get("com.example.ghost");
        assertNotNull("stale prefs entry must still be listed", ghost);
        assertEquals("uses the persisted label", "Ghost App", ghost.label);
        assertTrue("stale prefs entry loads checked", ghost.checked);
    }

    @Test
    public void prePopulation_installedAppNotInPrefs_loadsUnchecked() {
        installApp("com.example.carapp", "Car App", 1);
        prefs().edit().putString("com.example.other", "Other").commit();

        AppsList activity = buildActivity();
        Map<String, RowState> rows = renderRows(activity);

        RowState state = rows.get("com.example.carapp");
        assertNotNull(state);
        assertFalse("installed car app absent from prefs loads unchecked", state.checked);
    }

    // ------------------------------------------------------------------
    // selection persistence (drives the real adapter click path)
    // ------------------------------------------------------------------

    @Test
    public void toggle_on_writesPackageAndLabelToPrefs() {
        installApp("com.example.carapp", "Car App", 1);

        AppsList activity = buildActivity();
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);
        View row = findRowView(rv, "com.example.carapp");
        assertNotNull("car app row must be laid out", row);

        // Tapping the row toggles the entry (the row-level click listener calls the
        // production onClickSaveAppsWhiteList path).
        row.performClick();

        assertEquals("toggling ON persists packageName -> label",
                "Car App", prefs().getString("com.example.carapp", null));
    }

    @Test
    public void toggle_off_removesPackageFromPrefs() {
        installApp("com.example.carapp", "Car App", 1);
        prefs().edit().putString("com.example.carapp", "Car App").commit();

        AppsList activity = buildActivity();
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);
        View row = findRowView(rv, "com.example.carapp");
        assertNotNull(row);

        // It loaded checked (in prefs); a tap must remove it.
        row.performClick();

        assertFalse("toggling OFF removes the key",
                prefs().contains("com.example.carapp"));
    }

    @Test
    public void toggle_onThenOff_isIdempotentRoundTrip() {
        installApp("com.example.carapp", "Car App", 1);

        AppsList activity = buildActivity();
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);
        View row = findRowView(rv, "com.example.carapp");
        assertNotNull(row);

        row.performClick(); // ON
        assertEquals("Car App", prefs().getString("com.example.carapp", null));

        row.performClick(); // OFF (same AppInfo instance flipped back)
        assertFalse(prefs().contains("com.example.carapp"));
    }

    @Test
    public void toggle_doesNotTouchOtherPrefEntries() {
        installApp("com.example.carapp", "Car App", 1);
        installApp("com.example.carapp2", "Car App 2", 1);
        prefs().edit().putString("com.example.carapp2", "Car App 2").commit();

        AppsList activity = buildActivity();
        RecyclerView rv = recyclerOf(activity);
        layoutRecycler(rv);
        View row = findRowView(rv, "com.example.carapp");
        assertNotNull(row);

        row.performClick(); // toggle carapp ON

        assertEquals("toggling one app leaves other entries intact",
                "Car App 2", prefs().getString("com.example.carapp2", null));
        assertEquals("Car App", prefs().getString("com.example.carapp", null));
    }
}
