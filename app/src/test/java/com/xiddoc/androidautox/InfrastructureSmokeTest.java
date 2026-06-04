package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Robolectric smoke test that guards the unit-test infrastructure.
 *
 * <p>Real off-device logic tests live elsewhere (e.g. {@code RootGateTest}). This
 * test exists only to prove that Robolectric + Android resources stay wired up:
 * it inflates a real Android {@link Context}. If the test harness breaks, this
 * is the canary that fails first.
 */
@RunWith(RobolectricTestRunner.class)
public class InfrastructureSmokeTest {

    /** Inflate an Android Context to prove Robolectric is configured correctly. */
    @Test
    public void context_isAvailable() {
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull("Robolectric should provide an Android Context", context);
        assertEquals("com.xiddoc.androidautox", context.getPackageName());
    }
}
