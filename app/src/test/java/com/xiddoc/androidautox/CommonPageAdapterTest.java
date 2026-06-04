package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

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
}
