package com.xiddoc.androidautox.Utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/**
 * Robolectric tests for {@link BottomDialog} and its fluent {@link BottomDialog.Builder}.
 *
 * <p>Covers every fluent setter on the builder plus both code paths through
 * {@code initBottomDialog} (each optional section present vs. absent) and the
 * positive/negative click listeners (with/without callback, with/without
 * auto-dismiss).
 */
@RunWith(RobolectricTestRunner.class)
public class BottomDialogTest {

    private Activity activity;

    @Before
    public void setUp() {
        // A themed Activity supplies colorPrimary / colorControlHighlight and the
        // app theme so dialog_text_color & text appearances resolve.
        activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(com.xiddoc.androidautox.R.style.AppTheme);
    }

    private Context ctx() {
        return activity;
    }

    // ---- Builder fluent setters --------------------------------------------

    @Test
    public void builder_fluentSettersReturnSelfAndStoreValues() {
        BottomDialog.Builder b = new BottomDialog.Builder(ctx());

        assertSame(b, b.setTitle("title"));
        assertSame(b, b.setContent("content"));
        assertSame(b, b.setShadowHeight(7));
        assertSame(b, b.setPositiveBackgroundColor(Color.RED));
        assertSame(b, b.setPositiveTextColor(Color.GREEN));
        assertSame(b, b.setPositiveText("ok"));
        assertSame(b, b.setNegativeTextColor(Color.BLUE));
        assertSame(b, b.setNegativeText("cancel"));
        assertSame(b, b.setCancelable(false));
        assertSame(b, b.autoDismiss(false));
        assertSame(b, b.onPositive(d -> {}));
        assertSame(b, b.onNegative(d -> {}));

        assertEquals("title", b.title);
        assertEquals("content", b.content);
        assertEquals(7, b.shadowHeight);
        assertEquals(Color.RED, b.btn_colorPositiveBackground);
        assertEquals(Color.GREEN, b.btn_colorPositive);
        assertEquals("ok", b.btn_positive);
        assertEquals(Color.BLUE, b.btn_colorNegative);
        assertEquals("cancel", b.btn_negative);
        assertFalse(b.isCancelable);
        assertFalse(b.isAutoDismiss);
        assertNotNull(b.btn_positive_callback);
        assertNotNull(b.btn_negative_callback);
    }

    @Test
    public void builder_stringResAndColorResSetters() {
        BottomDialog.Builder b = new BottomDialog.Builder(ctx());

        b.setTitle(com.xiddoc.androidautox.R.string.app_name);
        b.setContent(com.xiddoc.androidautox.R.string.app_name);
        b.setPositiveText(com.xiddoc.androidautox.R.string.app_name);
        b.setNegativeText(com.xiddoc.androidautox.R.string.app_name);

        String appName = ctx().getString(com.xiddoc.androidautox.R.string.app_name);
        assertEquals(appName, b.title);
        assertEquals(appName, b.content);
        assertEquals(appName, b.btn_positive);
        assertEquals(appName, b.btn_negative);

        int expectedColor = androidx.core.content.res.ResourcesCompat.getColor(
                ctx().getResources(), com.xiddoc.androidautox.R.color.dialog_text_color, null);

        b.setBackgroundColor(com.xiddoc.androidautox.R.color.dialog_text_color);
        b.setPositiveBackgroundColorResource(com.xiddoc.androidautox.R.color.dialog_text_color);
        b.setPositiveTextColorResource(com.xiddoc.androidautox.R.color.dialog_text_color);
        b.setNegativeTextColorResource(com.xiddoc.androidautox.R.color.dialog_text_color);

        // Each *Resource setter must resolve to the actual resource color.
        assertEquals(expectedColor, b.backgroundColor);
        assertEquals(expectedColor, b.btn_colorPositiveBackground);
        assertEquals(expectedColor, b.btn_colorPositive);
        assertEquals(expectedColor, b.btn_colorNegative);
    }

    @Test
    public void builder_setIconDrawableAndRes() {
        BottomDialog.Builder b = new BottomDialog.Builder(ctx());
        Drawable d = new ColorDrawable(Color.MAGENTA);
        assertSame(b, b.setIcon(d));
        assertSame(d, b.icon);

        b.setIcon(android.R.drawable.ic_dialog_info);
        assertNotNull(b.icon);
    }

    @Test
    public void builder_setCustomViewWithAndWithoutPadding() {
        BottomDialog.Builder b = new BottomDialog.Builder(ctx());
        View v1 = new View(ctx());
        b.setCustomView(v1);
        assertSame(v1, b.customView);
        assertEquals(0, b.customViewPaddingLeft);

        View v2 = new View(ctx());
        b.setCustomView(v2, 4, 5, 6, 7);
        assertSame(v2, b.customView);
        assertTrue(b.customViewPaddingLeft > 0);
        assertTrue(b.customViewPaddingTop > 0);
        assertTrue(b.customViewPaddingRight > 0);
        assertTrue(b.customViewPaddingBottom > 0);
    }

    // ---- initBottomDialog: "everything present" branch ----------------------

    @Test
    public void build_withAllOptions_populatesViewsAndFiresCallbacks() {
        final boolean[] positive = {false};
        final boolean[] negative = {false};

        View custom = new View(ctx());
        BottomDialog dialog = new BottomDialog.Builder(ctx())
                .setIcon(new ColorDrawable(Color.RED))
                .setTitle("Title")
                .setContent("Content")
                .setShadowHeight(9)                 // != DEFAULT_SHADOW_HEIGHT
                .setCustomView(custom, 1, 1, 1, 1)
                .setPositiveText("OK")
                .setPositiveTextColor(Color.GREEN)  // btn_colorPositive != 0
                .setPositiveBackgroundColor(Color.CYAN) // != 0 path
                .onPositive(d -> positive[0] = true)
                .setNegativeText("Cancel")
                .setNegativeTextColor(Color.YELLOW) // btn_colorNegative != 0
                .onNegative(d -> negative[0] = true)
                .autoDismiss(true)
                .setCancelable(true)
                .build();

        assertNotNull(dialog.getBuilder());
        assertEquals(View.VISIBLE, dialog.getIconImageView().getVisibility());
        assertEquals("Title", dialog.getTitleTextView().getText().toString());
        assertEquals("Content", dialog.getContentTextView().getText().toString());
        assertEquals(View.VISIBLE, dialog.getPositiveButton().getVisibility());
        assertEquals(View.VISIBLE, dialog.getNegativeButton().getVisibility());
        assertNotNull(dialog.getCustomView());

        dialog.show();

        // Both click listeners: callback present + auto-dismiss true.
        dialog.getPositiveButton().performClick();
        dialog.getNegativeButton().performClick();
        assertTrue(positive[0]);
        assertTrue(negative[0]);

        dialog.dismiss();
    }

    // ---- initBottomDialog: "everything absent" branch -----------------------

    @Test
    public void build_withMinimalOptions_hidesTitleContentAndDefaults() {
        BottomDialog dialog = new BottomDialog.Builder(ctx())
                // no icon, no title, no content, no custom view,
                // positive/negative present but no callback, auto-dismiss false,
                // positive text color 0 (default), positive bg color 0 (resolve path)
                .setPositiveText("OK")
                .setNegativeText("Cancel")
                .autoDismiss(false)
                .build();

        assertEquals(View.GONE, dialog.getTitleTextView().getVisibility());
        assertEquals(View.GONE, dialog.getContentTextView().getVisibility());

        dialog.show();
        assertTrue(dialog.getBuilder().bottomDialog.isShowing());

        // Click listeners with NO callback and auto-dismiss FALSE: exercises the
        // other side of each branch. Because isAutoDismiss is false, clicking the
        // buttons must NOT dismiss the dialog -- assert it is still showing.
        dialog.getPositiveButton().performClick();
        assertTrue("autoDismiss=false: positive click must not dismiss",
                dialog.getBuilder().bottomDialog.isShowing());
        dialog.getNegativeButton().performClick();
        assertTrue("autoDismiss=false: negative click must not dismiss",
                dialog.getBuilder().bottomDialog.isShowing());

        dialog.dismiss();
    }

    @Test
    public void build_customViewWithExistingParent_isReparented() {
        FrameLayout parent = new FrameLayout(ctx());
        View custom = new View(ctx());
        parent.addView(custom);
        assertNotNull(custom.getParent());

        BottomDialog dialog = new BottomDialog.Builder(ctx())
                .setCustomView(custom)
                .build();

        assertSame(dialog.getCustomView(), custom.getParent());
    }

    // ---- setters / show / dismiss / dismiss-listener seams ------------------

    @Test
    public void setTitleAndContentTextView_setters() {
        BottomDialog dialog = new BottomDialog.Builder(ctx()).build();
        TextView t = new TextView(ctx());
        TextView c = new TextView(ctx());
        dialog.setTitleTextView(t);
        dialog.setContentTextView(c);
        assertSame(t, dialog.getTitleTextView());
        assertSame(c, dialog.getContentTextView());
    }

    @Test
    public void onDismissListener_seamRunsAndDialogDismisses() {
        BottomDialog dialog = new BottomDialog.Builder(ctx()).build();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                // no-op; we only need the registration seam to execute
            }
        });
        dialog.show();
        assertTrue(dialog.getBuilder().bottomDialog.isShowing());
        dialog.dismiss();
        assertFalse(dialog.getBuilder().bottomDialog.isShowing());
    }

    @Test
    public void builderShow_buildsAndShows() {
        BottomDialog dialog = new BottomDialog.Builder(ctx())
                .setTitle("X")
                .show();
        assertNotNull(dialog);
        dialog.dismiss();
    }

    /**
     * Covers the "colorPrimary present" path of {@link
     * BottomDialog#resolvePositiveBackgroundColor}: the real (Robolectric) theme
     * always resolves the attribute, so this drives the {@code ContextCompat.getColor}
     * side. Building a dialog with a zero positive-background color routes through it.
     */
    @Test
    public void build_zeroPositiveBackground_resolvesThemeColor() {
        BottomDialog dialog = new BottomDialog.Builder(ctx())
                .setPositiveText("OK")
                // leave btn_colorPositiveBackground == 0 so the resolve branch runs
                .build();

        assertNotNull(dialog.getPositiveButton().getBackground());
    }

    /**
     * Covers the {@code colorPrimary present} branch of {@link
     * BottomDialog#pickPositiveBackgroundColor} (uses the library color resource).
     */
    @Test
    public void pickPositiveBackgroundColor_attributePresent_usesLibraryColor() {
        int expected = androidx.core.content.ContextCompat.getColor(
                ctx(), com.github.javiersantos.bottomdialogs.R.color.colorPrimary);
        int color = BottomDialog.pickPositiveBackgroundColor(ctx(), true, 0x11223344);
        assertEquals(expected, color);
    }

    /**
     * Covers the {@code !hasColorPrimary} (attribute absent) branch: the raw
     * resolved {@code TypedValue} data is used. This path is unreachable through a
     * real theme because the test runtime's {@code returnDefaultValues=true} always
     * resolves attributes, so it is exercised on the extracted pure helper.
     */
    @Test
    public void pickPositiveBackgroundColor_attributeAbsent_usesTypedValueData() {
        int color = BottomDialog.pickPositiveBackgroundColor(ctx(), false, 0xFF010203);
        assertEquals(0xFF010203, color);
    }
}
