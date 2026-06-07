package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.rm.rmswitch.RMSwitch;
import com.xiddoc.androidautox.AppCompatibilityClassifier.Category;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;

/**
 * Robolectric tests for {@link MyAdapter} (the patched-apps whitelist list).
 *
 * <p>Drives the adapter through a real {@link RecyclerView} so {@code
 * onCreateViewHolder} inflates the row, {@code onBindViewHolder} populates it,
 * and the row/switch click listeners run against a live {@code getAdapterPosition}.
 * The click path persists the selection into the {@code appsListPref}
 * {@link SharedPreferences}, which the tests assert directly.
 */
@RunWith(RobolectricTestRunner.class)
public class MyAdapterTest {

    private Context context;
    private RecyclerView recyclerView;
    private ArrayList<AppInfo> items;
    private MyAdapter adapter;

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        // Start from a clean prefs slate.
        context.getSharedPreferences("appsListPref", 0).edit().clear().commit();

        items = new ArrayList<>();
        // Alpha is NEEDS_BRIDGE (default) and unchecked; Beta is NATIVE_AUTO and checked.
        items.add(new AppInfo("Alpha", "com.example.alpha", false, Category.NEEDS_BRIDGE));
        items.add(new AppInfo("Beta", "com.example.beta", true, Category.NATIVE_AUTO));

        recyclerView = new RecyclerView(context);
        adapter = new MyAdapter(items, recyclerView);
        // laidOutRecycler wires the LinearLayoutManager + adapter and lays it out
        // so the rows are created/bound (shared RecyclerView scaffolding).
        recyclerView = RecyclerTestSupport.laidOutRecycler(context, adapter);
    }

    private RecyclerView.ViewHolder holderAt(int pos) {
        return recyclerView.findViewHolderForAdapterPosition(pos);
    }

    // -----------------------------------------------------------------------
    // Adapter metadata
    // -----------------------------------------------------------------------

    @Test
    public void adapterBasics_idsAndCounts() {
        assertEquals(2, adapter.getItemCount());
        // getItemId / getItemViewType both echo position.
        assertEquals(0L, adapter.getItemId(0));
        assertEquals(1L, adapter.getItemId(1));
        assertEquals(0, adapter.getItemViewType(0));
        assertEquals(1, adapter.getItemViewType(1));
    }

    // -----------------------------------------------------------------------
    // onBindViewHolder
    // -----------------------------------------------------------------------

    @Test
    public void onBindViewHolder_populatesNamePackageAndCheckState() {
        RecyclerView.ViewHolder vh0 = holderAt(0);
        RecyclerView.ViewHolder vh1 = holderAt(1);

        TextView name0 = vh0.itemView.findViewById(R.id.app_name);
        TextView pkg0 = vh0.itemView.findViewById(R.id.app_package_name);
        RMSwitch sw0 = vh0.itemView.findViewById(R.id.checkbox_app);

        assertEquals("Alpha", name0.getText().toString());
        assertEquals("com.example.alpha", pkg0.getText().toString());
        assertFalse(sw0.isChecked());

        RMSwitch sw1 = vh1.itemView.findViewById(R.id.checkbox_app);
        assertTrue(sw1.isChecked());
    }

    // -----------------------------------------------------------------------
    // Click paths (add / remove branches of onClickSaveAppsWhiteList)
    // -----------------------------------------------------------------------

    @Test
    public void rowClick_onUncheckedItem_addsToPrefsAndChecksIt() {
        // Item 0 starts unchecked -> clicking the row should ADD (putString+commit).
        RecyclerView.ViewHolder vh0 = holderAt(0);
        vh0.itemView.performClick();

        SharedPreferences prefs = context.getSharedPreferences("appsListPref", 0);
        assertEquals("Alpha", prefs.getString("com.example.alpha", null));
        assertTrue(items.get(0).getIsChecked());
    }

    @Test
    public void switchClick_onCheckedItem_removesFromPrefsAndUnchecksIt() {
        // Pre-seed the prefs for the checked item so the remove branch is meaningful.
        context.getSharedPreferences("appsListPref", 0)
                .edit().putString("com.example.beta", "Beta").commit();

        RecyclerView.ViewHolder vh1 = holderAt(1);
        RMSwitch sw1 = vh1.itemView.findViewById(R.id.checkbox_app);

        // Item 1 starts checked -> clicking removes it (editor.remove + apply).
        sw1.performClick();

        SharedPreferences prefs = context.getSharedPreferences("appsListPref", 0);
        assertFalse(prefs.contains("com.example.beta"));
        assertFalse(items.get(1).getIsChecked());
    }

    // -----------------------------------------------------------------------
    // Badge binding + helper mappings
    // -----------------------------------------------------------------------

    private static int colorOf(android.view.View badge) {
        return ((ColorDrawable) badge.getBackground()).getColor();
    }

    @Test
    public void onBindViewHolder_bindsBadgeTextAndColorPerCategory() {
        // Row 0 (Alpha) is NEEDS_BRIDGE; row 1 (Beta) is NATIVE_AUTO.
        TextView badge0 = holderAt(0).itemView.findViewById(R.id.app_badge);
        TextView badge1 = holderAt(1).itemView.findViewById(R.id.app_badge);

        assertEquals(context.getString(R.string.badge_needs_bridge), badge0.getText().toString());
        assertEquals(ContextCompat.getColor(context, R.color.status_red), colorOf(badge0));

        assertEquals(context.getString(R.string.badge_native_auto), badge1.getText().toString());
        assertEquals(ContextCompat.getColor(context, R.color.status_green), colorOf(badge1));
    }

    @Test
    public void onBindViewHolder_mirrorShim_bindsYellowMirrorBadge() {
        ArrayList<AppInfo> mirrorItems = new ArrayList<>();
        mirrorItems.add(new AppInfo("Mir", "ru.inceptive.screentwoauto", false, Category.MIRROR_SHIM));
        MyAdapter mirrorAdapter = new MyAdapter(mirrorItems, new RecyclerView(context));
        RecyclerView rv = RecyclerTestSupport.laidOutRecycler(context, mirrorAdapter);

        TextView badge = rv.findViewHolderForAdapterPosition(0)
                .itemView.findViewById(R.id.app_badge);
        assertEquals(context.getString(R.string.badge_mirror_shim), badge.getText().toString());
        assertEquals(ContextCompat.getColor(context, R.color.status_yellow), colorOf(badge));
    }

    @Test
    public void badgeStringRes_mapsEveryCategory() {
        assertEquals(R.string.badge_native_auto, MyAdapter.badgeStringRes(Category.NATIVE_AUTO));
        assertEquals(R.string.badge_mirror_shim, MyAdapter.badgeStringRes(Category.MIRROR_SHIM));
        assertEquals(R.string.badge_needs_bridge, MyAdapter.badgeStringRes(Category.NEEDS_BRIDGE));
    }

    @Test
    public void badgeColorRes_mapsEveryCategory() {
        assertEquals(R.color.status_green, MyAdapter.badgeColorRes(Category.NATIVE_AUTO));
        assertEquals(R.color.status_yellow, MyAdapter.badgeColorRes(Category.MIRROR_SHIM));
        assertEquals(R.color.status_red, MyAdapter.badgeColorRes(Category.NEEDS_BRIDGE));
    }

    // -----------------------------------------------------------------------
    // NEEDS_BRIDGE hint toast branch
    // -----------------------------------------------------------------------

    @Test
    public void rowClick_onNeedsBridgeItem_showsHintToast() {
        // Alpha (row 0) is NEEDS_BRIDGE and unchecked -> toggling ON fires the hint.
        holderAt(0).itemView.performClick();

        assertEquals(context.getString(R.string.badge_needs_bridge_hint),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void switchClick_onNativeAutoItem_doesNotShowBridgeHint() {
        // Beta (row 1) is NATIVE_AUTO and checked -> toggling OFF must not show the
        // bridge hint (covers the false side of the NEEDS_BRIDGE branch on add, and
        // the remove path never hints). Toggle it OFF then back ON to add a NATIVE app.
        context.getSharedPreferences("appsListPref", 0)
                .edit().putString("com.example.beta", "Beta").commit();
        RMSwitch sw1 = holderAt(1).itemView.findViewById(R.id.checkbox_app);

        sw1.performClick(); // OFF (remove)
        sw1.performClick(); // ON again (add, NATIVE_AUTO -> no hint)

        assertEquals("adding a NATIVE_AUTO app shows the added toast, not the hint",
                context.getString(R.string.added_app_action) + "com.example.beta",
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }
}
