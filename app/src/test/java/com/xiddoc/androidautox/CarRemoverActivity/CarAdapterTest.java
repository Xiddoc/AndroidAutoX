package com.xiddoc.androidautox.CarRemoverActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.xiddoc.androidautox.R;
import com.xiddoc.androidautox.RecyclerTestSupport;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;

/**
 * Robolectric tests for {@link CarAdapter} (the saved-car remover list).
 *
 * <p>Drives the adapter through a real {@link RecyclerView} so the row is
 * inflated, bound (title set), and its click listener — which flips the item's
 * checked state — runs. {@code onClickSaveAppsWhiteList} persists the toggle into
 * the {@code idList} {@link SharedPreferences} keyed by the car id, which the
 * tests assert across both the add and remove branches.
 */
@RunWith(RobolectricTestRunner.class)
public class CarAdapterTest {

    private Context context;
    private RecyclerView recyclerView;
    private ArrayList<CarInfo> items;
    private CarAdapter adapter;

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("idList", 0).edit().clear().commit();

        items = new ArrayList<>();
        items.add(new CarInfo("Car One", false, "id-1"));
        items.add(new CarInfo("Car Two", true, "id-2"));

        adapter = new CarAdapter(items);
        // Shared scaffolding: wires the LinearLayoutManager + adapter and lays it
        // out so the rows are created/bound and holders resolve.
        recyclerView = RecyclerTestSupport.laidOutRecycler(context, adapter);
    }

    private RecyclerView.ViewHolder holderAt(int pos) {
        return recyclerView.findViewHolderForAdapterPosition(pos);
    }

    // -----------------------------------------------------------------------
    // Adapter metadata
    // -----------------------------------------------------------------------

    @Test
    public void adapterBasics_idsCountsCheckedAndId() {
        assertEquals(2, adapter.getItemCount());
        assertEquals(0L, adapter.getItemId(0));
        assertEquals(0, adapter.getItemViewType(0));
        assertEquals(1, adapter.getItemViewType(1));

        assertFalse(adapter.getChecked(0));
        assertTrue(adapter.getChecked(1));

        assertEquals("id-1", adapter.getId(0));
        assertEquals("id-2", adapter.getId(1));
    }

    // -----------------------------------------------------------------------
    // onBindViewHolder
    // -----------------------------------------------------------------------

    @Test
    public void onBindViewHolder_setsTitle() {
        RecyclerView.ViewHolder vh0 = holderAt(0);
        TextView title = vh0.itemView.findViewById(R.id.app_name);
        assertEquals("Car One", title.getText().toString());
    }

    // -----------------------------------------------------------------------
    // Row click (toggles checked state only)
    // -----------------------------------------------------------------------

    @Test
    public void rowClick_togglesCheckedState() {
        // NOTE: CarAdapter's row listener only flips the item's checked state; it
        // does NOT call onClickSaveAppsWhiteList (which is dead from the UI here),
        // so unlike AccountAdapter the remove branch can't be driven via a click —
        // it's exercised by the direct-call tests below.
        RecyclerView.ViewHolder vh0 = holderAt(0);

        // Item 0 starts unchecked; the row listener flips it to checked.
        assertFalse(items.get(0).getIsChecked());
        vh0.itemView.performClick();
        assertTrue(items.get(0).getIsChecked());

        // Clicking again flips it back (exercises the !getIsChecked() branch both ways).
        vh0.itemView.performClick();
        assertFalse(items.get(0).getIsChecked());
    }

    // -----------------------------------------------------------------------
    // onClickSaveAppsWhiteList (add / remove branches — driven directly)
    // -----------------------------------------------------------------------

    @Test
    public void onClickSaveAppsWhiteList_addBranch_putsBooleanAndChecks() {
        RecyclerView.ViewHolder vh0 = holderAt(0);

        // Item 0 unchecked -> add branch: putBoolean(id, true)+commit, setChecked(true).
        adapter.onClickSaveAppsWhiteList(vh0.itemView, 0);

        SharedPreferences prefs = context.getSharedPreferences("idList", 0);
        assertTrue(prefs.getBoolean("id-1", false));
        assertTrue(items.get(0).getIsChecked());
    }

    @Test
    public void onClickSaveAppsWhiteList_removeBranch_removesAndUnchecks() {
        context.getSharedPreferences("idList", 0)
                .edit().putBoolean("id-2", true).commit();

        RecyclerView.ViewHolder vh1 = holderAt(1);

        // Item 1 checked -> remove branch: editor.remove(id)+apply, setChecked(false).
        adapter.onClickSaveAppsWhiteList(vh1.itemView, 1);

        SharedPreferences prefs = context.getSharedPreferences("idList", 0);
        assertFalse(prefs.contains("id-2"));
        assertFalse(items.get(1).getIsChecked());
    }
}
