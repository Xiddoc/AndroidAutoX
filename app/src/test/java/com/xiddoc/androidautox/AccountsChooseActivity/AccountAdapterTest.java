package com.xiddoc.androidautox.AccountsChooseActivity;

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
 * Robolectric tests for {@link AccountAdapter} (the account chooser list).
 *
 * <p>Drives the adapter through a real {@link RecyclerView} so {@code
 * onCreateViewHolder} inflates the row (and clears the {@code accountsList}
 * prefs), and {@code onBindViewHolder} sets the title. The row-click and
 * {@code onClickSaveAppsWhiteList} paths toggle the position-keyed boolean in
 * {@link SharedPreferences}, which the tests assert.
 */
@RunWith(RobolectricTestRunner.class)
public class AccountAdapterTest {

    private Context context;
    private RecyclerView recyclerView;
    private ArrayList<AccountInfo> items;
    private AccountAdapter adapter;

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("accountsList", 0).edit().clear().commit();

        items = new ArrayList<>();
        items.add(new AccountInfo("alice@example.com", false));
        items.add(new AccountInfo("bob@example.com", true));

        adapter = new AccountAdapter(items);
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
    public void adapterBasics_idsCountsAndChecked() {
        assertEquals(2, adapter.getItemCount());
        assertEquals(0L, adapter.getItemId(0));
        assertEquals(0, adapter.getItemViewType(0));
        assertEquals(1, adapter.getItemViewType(1));

        assertFalse(adapter.getChecked(0));
        assertTrue(adapter.getChecked(1));
    }

    // -----------------------------------------------------------------------
    // onBindViewHolder / onCreateViewHolder
    // -----------------------------------------------------------------------

    @Test
    public void onBindViewHolder_setsTitle() {
        RecyclerView.ViewHolder vh0 = holderAt(0);
        TextView title = vh0.itemView.findViewById(R.id.app_name);
        assertEquals("alice@example.com", title.getText().toString());
    }

    @Test
    public void onCreateViewHolder_clearsAccountsListPrefs() {
        // Surprising-but-real behavior: every onCreateViewHolder wipes the whole
        // "accountsList" prefs. Seed a stale entry, force a fresh holder to be
        // created, and assert it was cleared — locks in the side effect.
        context.getSharedPreferences("accountsList", 0)
                .edit().putBoolean("stale", true).commit();

        // A brand-new laid-out RecyclerView creates fresh holders -> clear runs.
        RecyclerTestSupport.laidOutRecycler(context, new AccountAdapter(items));

        SharedPreferences prefs = context.getSharedPreferences("accountsList", 0);
        assertFalse("onCreateViewHolder must clear the accountsList prefs",
                prefs.contains("stale"));
    }

    // -----------------------------------------------------------------------
    // Click paths (add / remove branches of onClickSaveAppsWhiteList)
    // -----------------------------------------------------------------------

    @Test
    public void rowClick_onUncheckedItem_putsBooleanAndChecks() {
        // onCreateViewHolder cleared the prefs; item 0 is unchecked -> click adds it.
        RecyclerView.ViewHolder vh0 = holderAt(0);
        vh0.itemView.performClick();

        SharedPreferences prefs = context.getSharedPreferences("accountsList", 0);
        assertTrue(prefs.getBoolean("0", false));
        assertTrue(items.get(0).getIsChecked());
    }

    @Test
    public void rowClick_atPositionOne_writesKeyOne_notZero() {
        // Position-correctness guard: the row listener captures `i` from
        // onCreateViewHolder. Clicking position 1 must write key "1" (and NOT "0"),
        // so a bug that always wrote "0" would be caught here. Item 1 starts
        // checked, so unchecked it first to take the ADD branch (putBoolean).
        items.get(1).setChecked(false);

        RecyclerView.ViewHolder vh1 = holderAt(1);
        vh1.itemView.performClick();

        SharedPreferences prefs = context.getSharedPreferences("accountsList", 0);
        assertTrue("click at position 1 must write key \"1\"", prefs.getBoolean("1", false));
        assertFalse("click at position 1 must NOT write key \"0\"", prefs.contains("0"));
        assertTrue(items.get(1).getIsChecked());
    }

    @Test
    public void rowClick_onCheckedItem_removesAndUnchecks_viaClickPath() {
        // Drive the REMOVE branch through the real click path (symmetric with the
        // ADD branch above) rather than calling onClickSaveAppsWhiteList directly.
        // Seed the checked item's key, then performClick on its row.
        context.getSharedPreferences("accountsList", 0)
                .edit().putBoolean("1", true).commit();

        RecyclerView.ViewHolder vh1 = holderAt(1); // item 1 is checked
        vh1.itemView.performClick();

        SharedPreferences prefs = context.getSharedPreferences("accountsList", 0);
        assertFalse(prefs.contains("1"));
        assertFalse(items.get(1).getIsChecked());
    }

    @Test
    public void onClickSaveAppsWhiteList_onCheckedItem_removesAndUnchecks() {
        // Direct-call variant of the remove branch (kept alongside the click-path
        // test above for an isolated, listener-free exercise of the method).
        // Seed the prefs for the checked item so the remove branch is meaningful.
        context.getSharedPreferences("accountsList", 0)
                .edit().putBoolean("1", true).commit();

        RecyclerView.ViewHolder vh1 = holderAt(1);
        // Item 1 is checked -> remove branch.
        adapter.onClickSaveAppsWhiteList(vh1.itemView, 1);

        SharedPreferences prefs = context.getSharedPreferences("accountsList", 0);
        assertFalse(prefs.contains("1"));
        assertFalse(items.get(1).getIsChecked());
    }
}
