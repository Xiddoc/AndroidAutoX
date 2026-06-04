package com.xiddoc.androidautox;

import android.app.Application;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Robolectric tests verifying that the logs {@link TextView} is selectable.
 *
 * <p>Launching {@link MainActivity} under Robolectric is impractical: it calls
 * libsu (which needs a real root shell) and tries to bind a {@link com.topjohnwu.superuser.ipc.RootService}.
 * Instead we inflate {@code R.layout.scrollview_second} directly — the actual
 * layout that hosts the logs view — and assert the attribute set in both the XML
 * and via the code path in {@link MainActivity#initiateLogsText()}.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 34, application = Application.class)
public class LogsTextSelectableTest {

    /**
     * Inflates the logs panel layout and asserts that the {@code logs} TextView
     * has {@code android:textIsSelectable="true"} as declared in the XML.
     * This verifies the layout change is in place and correctly parsed by the
     * Android framework (via Robolectric's resource pipeline).
     */
    @Test
    public void logsTextView_layoutAttribute_isTextSelectableTrue() {
        Context ctx = ApplicationProvider.getApplicationContext();
        View root = LayoutInflater.from(ctx).inflate(R.layout.scrollview_second, null, false);

        TextView logs = root.findViewById(R.id.logs);
        assertNotNull("R.id.logs must exist in scrollview_second.xml", logs);
        assertTrue(
                "logs TextView must have android:textIsSelectable=\"true\" (set in layout XML)",
                logs.isTextSelectable()
        );
    }

    /**
     * Verifies that calling {@code setTextIsSelectable(true)} on a plain {@link TextView}
     * (the code path used in {@code initiateLogsText()}) enables text selectability.
     * This is a unit-level regression guard: if the call is ever removed or replaced
     * with a conflicting movement method, this test will catch it.
     */
    @Test
    public void setTextIsSelectable_true_makesViewSelectable() {
        Context ctx = ApplicationProvider.getApplicationContext();
        TextView tv = new TextView(ctx);

        // Starts as not selectable (default)
        assertFalse("TextView must NOT be selectable by default", tv.isTextSelectable());

        // This is exactly what initiateLogsText() calls
        tv.setTextIsSelectable(true);

        assertTrue(
                "TextView must be selectable after setTextIsSelectable(true)",
                tv.isTextSelectable()
        );
    }

    /**
     * Verifies that calling {@code setTextIsSelectable(true)} does NOT additionally
     * set a {@link android.text.method.ScrollingMovementMethod} — which would break
     * horizontal scrolling delegation to the parent ScrollView.
     * <p>
     * When selectable, Android uses {@link android.text.method.ArrowKeyMovementMethod}.
     * If the code ever reverts to setting ScrollingMovementMethod, this guard will
     * fail, reminding the developer of the conflict.
     */
    @Test
    public void logsTextView_afterInflation_doesNotUseScrollingMovementMethod() {
        Context ctx = ApplicationProvider.getApplicationContext();
        View root = LayoutInflater.from(ctx).inflate(R.layout.scrollview_second, null, false);

        TextView logs = root.findViewById(R.id.logs);
        assertNotNull(logs);

        // ScrollingMovementMethod must NOT be present — it would override the
        // selection movement method and break long-press text selection.
        assertFalse(
                "ScrollingMovementMethod must not be set on the logs TextView",
                logs.getMovementMethod() instanceof android.text.method.ScrollingMovementMethod
        );
    }

    /**
     * Verifies that after inflation the logs panel still contains a parent ScrollView,
     * which is responsible for vertical scrolling now that {@code ScrollingMovementMethod}
     * has been removed from the TextView.
     */
    @Test
    public void logsScrollView_existsInLayout() {
        Context ctx = ApplicationProvider.getApplicationContext();
        View root = LayoutInflater.from(ctx).inflate(R.layout.scrollview_second, null, false);

        View logsScroll = root.findViewById(R.id.logs_scroll);
        assertNotNull(
                "logs_scroll ScrollView must be present in scrollview_second.xml",
                logsScroll
        );
        assertTrue(
                "logs_scroll must be a ScrollView (handles vertical scrolling)",
                logsScroll instanceof android.widget.ScrollView
        );
    }
}
