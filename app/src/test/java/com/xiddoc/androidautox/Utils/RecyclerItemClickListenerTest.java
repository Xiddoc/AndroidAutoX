package com.xiddoc.androidautox.Utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xiddoc.androidautox.RecyclerTestSupport;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowGestureDetector;

import java.util.ArrayList;
import java.util.List;

/**
 * Robolectric tests for {@link RecyclerItemClickListener}.
 *
 * <p>The listener wraps a {@link GestureDetector}. We stand up a real, laid-out
 * {@link RecyclerView} (so {@code findChildViewUnder} resolves a child) and a
 * recording {@link RecyclerItemClickListener.OnItemClickListener}, then:
 * <ul>
 *   <li>feed a DOWN+UP {@link MotionEvent} pair over a child so the detector
 *       reports a single tap and {@code onInterceptTouchEvent} fires
 *       {@code onItemClick} and returns true;</li>
 *   <li>feed a tap with no child underneath so it returns false and no callback
 *       fires;</li>
 *   <li>invoke {@code onLongPress} on the detector's listener to drive the
 *       long-press callback;</li>
 *   <li>call the no-op {@code onTouchEvent} / {@code onRequestDisallowInterceptTouchEvent}.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
public class RecyclerItemClickListenerTest {

    /** Records the last item / long-item click it received. */
    private static final class RecordingListener
            implements RecyclerItemClickListener.OnItemClickListener {
        View clickedView;
        int clickedPos = -999;
        View longView;
        int longPos = -999;

        @Override
        public void onItemClick(View view, int position) {
            clickedView = view;
            clickedPos = position;
        }

        @Override
        public void onLongItemClick(View view, int position) {
            longView = view;
            longPos = position;
        }
    }

    /** Minimal one-row adapter so the RecyclerView has a child to resolve. */
    private static final class StubAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<String> data;

        StubAdapter(List<String> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = new View(parent.getContext());
            v.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, 100));
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) { }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private Context context;
    private RecyclerView recyclerView;
    private RecordingListener listener;
    private RecyclerItemClickListener subject;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();

        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        List<String> data = new ArrayList<>();
        data.add("row-0");
        recyclerView.setAdapter(new StubAdapter(data));

        // Shared measure+layout boilerplate (500px EXACTLY) so findChildViewUnder
        // resolves the single 100px row.
        RecyclerTestSupport.measureAndLayout(recyclerView, 500);

        listener = new RecordingListener();
        subject = new RecyclerItemClickListener(context, recyclerView, listener);
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0, 0, action, x, y, 0);
    }

    @Test
    public void singleTapOverChild_firesOnItemClick_andInterceptsTrue() {
        // Prime the detector with a DOWN, then an UP over the child -> single tap.
        subject.onInterceptTouchEvent(recyclerView, event(MotionEvent.ACTION_DOWN, 10, 10));
        boolean intercepted =
                subject.onInterceptTouchEvent(recyclerView, event(MotionEvent.ACTION_UP, 10, 10));

        assertTrue("a single tap over a child must intercept", intercepted);
        assertEquals(0, listener.clickedPos);
        assertEquals(recyclerView.findChildViewUnder(10, 10), listener.clickedView);
    }

    @Test
    public void downOverChildWithoutTap_returnsFalse_noCallback() {
        // A lone DOWN over a child resolves the child (first condition true) but the
        // detector reports no tap yet, so onTouchEvent(e) is false -> not intercepted.
        boolean intercepted =
                subject.onInterceptTouchEvent(recyclerView, event(MotionEvent.ACTION_DOWN, 10, 10));

        assertFalse("DOWN alone is not a tap -> not intercepted", intercepted);
        assertEquals(-999, listener.clickedPos);
    }

    @Test
    public void longPress_withNoChildUnder_doesNotFire() {
        // y below the single 100px row -> findChildViewUnder returns null, so the
        // child==null branch is taken and onLongItemClick must NOT fire.
        ShadowGestureDetector shadow = Shadows.shadowOf(subject.mGestureDetector);
        shadow.getListener().onLongPress(event(MotionEvent.ACTION_DOWN, 10, 480));

        assertEquals(-999, listener.longPos);
        assertNull("no child under the long-press -> no view captured", listener.longView);
    }

    @Test
    public void tapWithNoChildUnder_returnsFalse_noCallback() {
        // y far below the single 100px row -> no child resolved.
        subject.onInterceptTouchEvent(recyclerView, event(MotionEvent.ACTION_DOWN, 10, 480));
        boolean intercepted =
                subject.onInterceptTouchEvent(recyclerView, event(MotionEvent.ACTION_UP, 10, 480));

        assertFalse("no child under the touch -> not intercepted", intercepted);
        assertEquals(-999, listener.clickedPos);
    }

    @Test
    public void longPress_overChild_firesOnLongItemClick() {
        // Drive the detector's gesture listener directly: long-press is timer-based
        // and awkward to schedule, so trigger the SimpleOnGestureListener callback.
        ShadowGestureDetector shadow = Shadows.shadowOf(subject.mGestureDetector);
        GestureDetector.OnGestureListener gl = shadow.getListener();

        gl.onLongPress(event(MotionEvent.ACTION_DOWN, 10, 10));

        assertEquals(0, listener.longPos);
        assertEquals(recyclerView.findChildViewUnder(10, 10), listener.longView);
    }

    @Test
    public void nullListener_tapOverChild_returnsFalse_andLongPressNoThrow() {
        // With a null listener, the `mListener != null` guard is false in both
        // onInterceptTouchEvent and onLongPress even though a child is resolved.
        RecyclerItemClickListener nullSubject =
                new RecyclerItemClickListener(context, recyclerView, null);

        nullSubject.onInterceptTouchEvent(recyclerView, event(MotionEvent.ACTION_DOWN, 10, 10));
        boolean intercepted =
                nullSubject.onInterceptTouchEvent(recyclerView, event(MotionEvent.ACTION_UP, 10, 10));
        assertFalse("null listener -> never intercepts", intercepted);

        // child resolved but listener null -> no NPE, nothing fires.
        ShadowGestureDetector shadow = Shadows.shadowOf(nullSubject.mGestureDetector);
        shadow.getListener().onLongPress(event(MotionEvent.ACTION_DOWN, 10, 10));
    }

    @Test
    public void onSingleTapUp_returnsTrue() {
        ShadowGestureDetector shadow = Shadows.shadowOf(subject.mGestureDetector);
        assertTrue(shadow.getListener().onSingleTapUp(event(MotionEvent.ACTION_UP, 10, 10)));
    }

    @Test
    public void noOpMethods_doNotThrow() {
        // onTouchEvent and onRequestDisallowInterceptTouchEvent are intentionally empty.
        subject.onTouchEvent(recyclerView, event(MotionEvent.ACTION_MOVE, 10, 10));
        subject.onRequestDisallowInterceptTouchEvent(true);
        subject.onRequestDisallowInterceptTouchEvent(false);
    }
}
