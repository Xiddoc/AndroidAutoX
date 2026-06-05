package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import android.widget.FrameLayout;

import androidx.viewpager.widget.PagerAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Unit tests for {@link CommonPageAdapter}'s insertion-order bookkeeping.
 *
 * <p>{@code insertViewId} returns the index it inserted at; callers derive page
 * positions (e.g. the Logs page index) from that instead of hardcoding magic
 * numbers. These tests pin that the returned indices are 0 then 1 for two
 * inserts and that {@link CommonPageAdapter#getCount()} agrees, so the Logs page
 * index can't silently drift.
 */
@RunWith(RobolectricTestRunner.class)
public class CommonPageAdapterTest {

    @Test
    public void insertViewId_returnsSequentialIndices() {
        CommonPageAdapter adapter = new CommonPageAdapter();

        int first = adapter.insertViewId(1001, "Tweaks");
        int second = adapter.insertViewId(1002, "Logs");

        assertEquals(0, first);
        assertEquals(1, second);
    }

    @Test
    public void getCount_tracksInserts() {
        CommonPageAdapter adapter = new CommonPageAdapter();
        assertEquals(0, adapter.getCount());

        adapter.insertViewId(1001, "Tweaks");
        assertEquals(1, adapter.getCount());

        adapter.insertViewId(1002, "Logs");
        assertEquals(2, adapter.getCount());
    }

    @Test
    public void getPageTitle_matchesInsertionOrder() {
        CommonPageAdapter adapter = new CommonPageAdapter();
        adapter.insertViewId(1001, "Tweaks");
        adapter.insertViewId(1002, "Logs");

        assertEquals("Tweaks", adapter.getPageTitle(0));
        assertEquals("Logs", adapter.getPageTitle(1));
    }

    /**
     * {@code instantiateItem} resolves the page view by the stored id from the
     * container, and {@code destroyItem} removes that exact view again. Drive
     * both against a real (Robolectric) container with a child whose id matches
     * the inserted page id.
     */
    @Test
    public void instantiateItem_findsChildById_andDestroyItemRemovesIt() {
        Application app = RuntimeEnvironment.getApplication();
        FrameLayout container = new FrameLayout(app);

        View page = new View(app);
        page.setId(4242);
        container.addView(page);

        CommonPageAdapter adapter = new CommonPageAdapter();
        int pos = adapter.insertViewId(4242, "Page");

        Object item = adapter.instantiateItem(container, pos);
        assertSame("instantiateItem must return the child resolved by id", page, item);

        // isViewFromObject pairs a view with its instantiated object.
        assertTrue(adapter.isViewFromObject(page, item));
        assertFalse(adapter.isViewFromObject(new View(app), item));

        assertEquals(1, container.getChildCount());
        adapter.destroyItem(container, pos, item);
        assertEquals("destroyItem must remove the page view", 0, container.getChildCount());
        assertNull(container.findViewById(4242));
    }

    /**
     * {@code instantiateItem} indexes into {@code pageIds.get(position)}, so a
     * second page must resolve via its OWN id at position 1 (not position 0's).
     * Guards the non-zero indexing path.
     */
    @Test
    public void instantiateItem_atPositionOne_resolvesSecondPageById() {
        Application app = RuntimeEnvironment.getApplication();
        FrameLayout container = new FrameLayout(app);

        View page0 = new View(app);
        page0.setId(1111);
        container.addView(page0);

        View page1 = new View(app);
        page1.setId(2222);
        container.addView(page1);

        CommonPageAdapter adapter = new CommonPageAdapter();
        int pos0 = adapter.insertViewId(1111, "Page0");
        int pos1 = adapter.insertViewId(2222, "Page1");

        // Position 1 must resolve the second page's view, not the first's.
        assertSame(page1, adapter.instantiateItem(container, pos1));
        assertSame(page0, adapter.instantiateItem(container, pos0));
    }

    /**
     * The adapter always reports {@link PagerAdapter#POSITION_NONE} so the pager
     * fully rebuilds on {@code notifyDataSetChanged()} (pages are static ids, not
     * reordered), guarding against stale page caching.
     */
    @Test
    public void getItemPosition_isAlwaysPositionNone() {
        CommonPageAdapter adapter = new CommonPageAdapter();
        assertEquals(PagerAdapter.POSITION_NONE, adapter.getItemPosition(new Object()));
    }
}
