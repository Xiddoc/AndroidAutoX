package com.xiddoc.androidautox;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Shared test scaffolding for the Robolectric RecyclerView adapter/listener
 * tests ({@code MyAdapterTest}, {@code CarAdapterTest}, {@code
 * AccountAdapterTest}, {@code RecyclerItemClickListenerTest}).
 *
 * <p>These tests all need to (a) stand up a real {@link RecyclerView} wired to
 * a {@link LinearLayoutManager} and an adapter, and (b) measure + lay it out so
 * its rows are actually created/bound and {@code findViewHolderForAdapterPosition}
 * resolves. That boilerplate was copy-pasted across every test; it lives here
 * once instead.
 */
public final class RecyclerTestSupport {

    private RecyclerTestSupport() { }

    /**
     * Measure + lay out the RecyclerView at a {@code size x size} EXACTLY spec so
     * it creates and binds its rows (and {@code findChildViewUnder} resolves).
     */
    public static void measureAndLayout(RecyclerView rv, int size) {
        int spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
        rv.measure(spec, spec);
        rv.layout(0, 0, size, size);
    }

    /**
     * Build a {@link RecyclerView} backed by a {@link LinearLayoutManager} and the
     * given adapter, then measure + lay it out (default 1080px) so its rows exist.
     */
    public static RecyclerView laidOutRecycler(Context context, RecyclerView.Adapter<?> adapter) {
        RecyclerView rv = new RecyclerView(context);
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(adapter);
        measureAndLayout(rv, 1080);
        return rv;
    }
}
