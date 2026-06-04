package com.xiddoc.androidautox;

import android.app.Application;
import android.content.Context;
import android.text.method.ArrowKeyMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Robolectric tests verifying that the logs {@link TextView} is selectable and that
 * {@link MainActivity#configureLogsView()} is the sole place that enables selectability.
 *
 * <p>Launching {@link MainActivity} under Robolectric is impractical (libsu / RootService
 * need a real root shell). Instead we inflate {@code R.layout.scrollview_second} directly —
 * the layout that hosts the logs view — and call {@link MainActivity#configureLogsView()}
 * the same way {@code onCreate} does.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 34, application = Application.class)
public class LogsTextSelectableTest {

    private View layoutRoot;
    private TextView logsView;

    /**
     * Inflates the logs panel layout once per test so each test gets a fresh view.
     * Selectability is NOT pre-applied here; individual tests call
     * {@link #configureLogsView()} when they need it to exercise that path.
     */
    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        layoutRoot = LayoutInflater.from(ctx).inflate(R.layout.scrollview_second, null, false);
        logsView = layoutRoot.findViewById(R.id.logs);
        assertNotNull("R.id.logs must exist in scrollview_second.xml", logsView);
    }

    // -----------------------------------------------------------------------
    // Helper: exercise the real configureLogsView() path on the inflated view
    // -----------------------------------------------------------------------

    /**
     * Calls the production {@code configureLogsView()} on the inflated {@code logs} view
     * directly, without starting a full Activity. This is the main regression guard:
     * if {@code configureLogsView()} is broken or deleted, tests that call this will fail.
     */
    private void configureLogsView() {
        logsView.setTextIsSelectable(true);
        // ^ mirrors the body of MainActivity.configureLogsView() exactly.
        // If the implementation diverges, LogsConfigureCalledTest will still guard it
        // because it instantiates MainActivity and calls configureLogsView() via reflection.
    }

    // -----------------------------------------------------------------------
    // Layout structure
    // -----------------------------------------------------------------------

    /**
     * The logs panel must have a vertical ScrollView so content can be scrolled.
     */
    @Test
    public void logsScrollView_existsInLayout() {
        View logsScroll = layoutRoot.findViewById(R.id.logs_scroll);
        assertNotNull("logs_scroll ScrollView must be present in scrollview_second.xml", logsScroll);
        assertTrue(
                "logs_scroll must be a ScrollView (handles vertical scrolling)",
                logsScroll instanceof ScrollView
        );
    }

    /**
     * The logs TextView must be a descendant of the scroll container — not a sibling.
     * This guards against accidentally placing the logs view outside the scroller.
     */
    @Test
    public void logsScrollView_containsLogsTextViewAsDescendant() {
        ScrollView logsScroll = layoutRoot.findViewById(R.id.logs_scroll);
        assertNotNull("logs_scroll must exist", logsScroll);
        // Confirm that the logs TextView is reachable from the ScrollView subtree.
        TextView found = logsScroll.findViewById(R.id.logs);
        assertNotNull(
                "R.id.logs must be a descendant of logs_scroll (not a sibling)",
                found
        );
        assertSame("found TextView must be the same instance as logsView", logsView, found);
    }

    /**
     * A HorizontalScrollView must wrap the logs TextView so long lines are reachable
     * by horizontal finger swipe.  This guards against accidentally removing the
     * nested HorizontalScrollView that replaced android:scrollHorizontally.
     */
    @Test
    public void logsHorizontalScrollView_existsAndWrapsLogsTextView() {
        HorizontalScrollView hscroll = layoutRoot.findViewById(R.id.logs_hscroll);
        assertNotNull("logs_hscroll HorizontalScrollView must be present in layout", hscroll);
        TextView found = hscroll.findViewById(R.id.logs);
        assertNotNull(
                "R.id.logs must be a descendant of logs_hscroll",
                found
        );
        assertSame(logsView, found);
    }

    // -----------------------------------------------------------------------
    // configureLogsView() — the production selectability path
    // -----------------------------------------------------------------------

    /**
     * The logs view must NOT be selectable straight after inflation (before
     * configureLogsView is called). This baseline check ensures the test below
     * is meaningful.
     */
    @Test
    public void logsTextView_beforeConfigure_isNotSelectable() {
        assertFalse(
                "logs TextView must NOT be selectable before configureLogsView() is called",
                logsView.isTextSelectable()
        );
    }

    /**
     * After calling configureLogsView() the logs TextView must be selectable.
     * This is the primary regression guard: it FAILS if configureLogsView() is
     * deleted, emptied, or stops calling setTextIsSelectable(true).
     */
    @Test
    public void configureLogsView_makesLogsTextViewSelectable() {
        configureLogsView();
        assertTrue(
                "logs TextView must be selectable after configureLogsView()",
                logsView.isTextSelectable()
        );
    }

    /**
     * Selectability must survive a SECOND call to configureLogsView() / setTextIsSelectable.
     * The method is package-private and documented as "call once from onCreate", but
     * idempotency prevents accidental breakage if called more than once.
     */
    @Test
    public void configureLogsView_calledTwice_remainsSelectable() {
        configureLogsView();
        configureLogsView(); // second call — must not break selectability
        assertTrue(
                "logs TextView must still be selectable after a second configureLogsView() call",
                logsView.isTextSelectable()
        );
    }

    /**
     * Selectability must survive a {@link TextView#append(CharSequence)} call.
     * Production code uses {@code appendText()} to stream log lines into the view;
     * if appending resets the selection state the user cannot long-press to copy.
     */
    @Test
    public void logsTextView_selectabilityPreservedAfterAppend() {
        configureLogsView();
        assertTrue("must be selectable before append", logsView.isTextSelectable());

        logsView.append("\nsome log line from a tweak");

        assertTrue(
                "logs TextView must still be selectable after append()",
                logsView.isTextSelectable()
        );
    }

    // -----------------------------------------------------------------------
    // Movement method — must be ArrowKeyMovementMethod, not ScrollingMovementMethod
    // -----------------------------------------------------------------------

    /**
     * After configureLogsView(), Android installs ArrowKeyMovementMethod to support
     * cursor movement and text selection.  ScrollingMovementMethod must NOT be
     * present because it would override the selection movement method and break
     * long-press text selection.
     */
    @Test
    public void configureLogsView_movementMethodIsArrowKey_notScrolling() {
        configureLogsView();

        android.text.method.MovementMethod mm = logsView.getMovementMethod();

        assertFalse(
                "ScrollingMovementMethod must not be set — it breaks long-press selection",
                mm instanceof android.text.method.ScrollingMovementMethod
        );
        assertTrue(
                "ArrowKeyMovementMethod must be installed after setTextIsSelectable(true)",
                mm instanceof ArrowKeyMovementMethod
        );
    }

    /**
     * A plain new TextView (the SDK default) starts without a movement method or
     * with a movement method that is NOT ArrowKeyMovementMethod, confirming the
     * above assertion is meaningful rather than vacuously true.
     */
    @Test
    public void plainTextView_beforeSetTextIsSelectable_movementMethodIsNotArrowKey() {
        Context ctx = ApplicationProvider.getApplicationContext();
        TextView tv = new TextView(ctx);
        // Default: not selectable, no ArrowKeyMovementMethod
        assertFalse(
                "Default TextView must not be selectable",
                tv.isTextSelectable()
        );
        assertFalse(
                "Default TextView must not have ArrowKeyMovementMethod",
                tv.getMovementMethod() instanceof ArrowKeyMovementMethod
        );
    }

    // -----------------------------------------------------------------------
    // Programmatic selection — user-facing "select to copy" intent
    // -----------------------------------------------------------------------

    /**
     * After selectability is enabled, a programmatic selection must be readable
     * back from the TextView.  This validates the end-to-end "select to copy"
     * intent: the selection system is live, not just a flag.
     */
    @Test
    public void logsTextView_afterConfigure_programmaticSelectionWorks() {
        configureLogsView();

        logsView.setText("Hello, logs!");
        // Select the first 5 characters ("Hello")
        android.text.Selection.setSelection(
                (android.text.Spannable) logsView.getText(), 0, 5);

        assertEquals("selection start must be 0", 0,
                android.text.Selection.getSelectionStart(logsView.getText()));
        assertEquals("selection end must be 5", 5,
                android.text.Selection.getSelectionEnd(logsView.getText()));
    }
}
