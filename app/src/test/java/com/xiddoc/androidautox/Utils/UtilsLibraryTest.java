package com.xiddoc.androidautox.Utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Exercises {@link UtilsLibrary}: the dp->px conversion (needs a real
 * {@link Context} for display metrics) and the GradientDrawable / RippleDrawable
 * button-background builders.
 */
@RunWith(RobolectricTestRunner.class)
public class UtilsLibraryTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void dpToPixels_scalesByDensity() {
        Context ctx = context();
        float density = ctx.getResources().getDisplayMetrics().density;

        int px = UtilsLibrary.dpToPixels(ctx, 10);

        assertEquals((int) (10 * density + 0.5f), px);
    }

    @Test
    public void dpToPixels_zeroIsZero() {
        assertEquals(0, UtilsLibrary.dpToPixels(context(), 0));
    }

    @Test
    public void constructor_isInstantiable() {
        // UtilsLibrary is a static-helper holder; cover its implicit constructor.
        assertNotNull(new UtilsLibrary());
    }

    @Test
    public void createButtonBackgroundDrawable_returnsRippleWrappingGradient() {
        Drawable d = UtilsLibrary.createButtonBackgroundDrawable(context(), 0xFF112233);

        assertNotNull(d);
        assertTrue("expected a RippleDrawable", d instanceof RippleDrawable);

        RippleDrawable ripple = (RippleDrawable) d;
        Drawable content = ripple.getDrawable(0);
        assertTrue("ripple should wrap a GradientDrawable",
                content instanceof GradientDrawable);
    }
}
