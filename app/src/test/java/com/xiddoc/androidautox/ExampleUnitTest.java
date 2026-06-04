package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Smoke tests proving the unit-test infrastructure works.
 *
 * <p>{@link PlainTest} is vanilla JUnit and needs no Android runtime.
 * {@link RobolectricSmokeTest} runs under Robolectric and exercises a real
 * Android {@link Context}, proving Robolectric + Android resources are wired up.
 */
public class ExampleUnitTest {

    /** Vanilla JUnit: no Android, no Robolectric — must always pass. */
    public static class PlainTest {
        @Test
        public void arithmetic_isCorrect() {
            assertEquals(4, 2 + 2);
        }
    }

    /** Robolectric: inflate an Android Context to prove the setup works. */
    @RunWith(RobolectricTestRunner.class)
    public static class RobolectricSmokeTest {
        @Test
        public void context_isAvailable() {
            Context context = ApplicationProvider.getApplicationContext();
            assertNotNull("Robolectric should provide an Android Context", context);
            assertEquals("com.xiddoc.androidautox", context.getPackageName());
        }
    }
}
