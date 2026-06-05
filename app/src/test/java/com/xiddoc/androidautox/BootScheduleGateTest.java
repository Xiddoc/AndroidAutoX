package com.xiddoc.androidautox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Intent;

import org.junit.Test;

/**
 * Tests for {@link BootScheduleGate}: the pure should-schedule predicate pulled
 * out of {@link BootReceiver}. Plain JUnit + Mockito (no Robolectric runner) so the
 * coverage probes register; the {@link Intent} overload is exercised with a mocked
 * {@code Intent} whose {@code getAction()} is stubbed.
 */
public class BootScheduleGateTest {

    @Test
    public void bootCompleted_schedules() {
        assertTrue(BootScheduleGate.shouldSchedule(Intent.ACTION_BOOT_COMPLETED));
    }

    @Test
    public void lockedBootCompleted_schedules() {
        assertTrue(BootScheduleGate.shouldSchedule(
                BootScheduleGate.ACTION_LOCKED_BOOT_COMPLETED));
    }

    @Test
    public void myPackageReplaced_schedules() {
        assertTrue(BootScheduleGate.shouldSchedule(Intent.ACTION_MY_PACKAGE_REPLACED));
    }

    @Test
    public void unrelatedAction_doesNotSchedule() {
        assertFalse(BootScheduleGate.shouldSchedule(Intent.ACTION_SCREEN_ON));
    }

    @Test
    public void nullAction_doesNotSchedule() {
        assertFalse(BootScheduleGate.shouldSchedule((String) null));
    }

    @Test
    public void nullIntent_doesNotSchedule() {
        assertFalse(BootScheduleGate.shouldSchedule((Intent) null));
    }

    @Test
    public void intentWithBootAction_schedules() {
        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(Intent.ACTION_BOOT_COMPLETED);
        assertTrue(BootScheduleGate.shouldSchedule(intent));
    }

    @Test
    public void intentWithUnrelatedAction_doesNotSchedule() {
        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(Intent.ACTION_SCREEN_OFF);
        assertFalse(BootScheduleGate.shouldSchedule(intent));
    }

    // isReboot: only a genuine device boot clears reboot-pending markers; an in-place
    // app update (MY_PACKAGE_REPLACED) must NOT count as a reboot.

    @Test
    public void bootCompleted_isReboot() {
        assertTrue(BootScheduleGate.isReboot(Intent.ACTION_BOOT_COMPLETED));
    }

    @Test
    public void lockedBootCompleted_isReboot() {
        assertTrue(BootScheduleGate.isReboot(BootScheduleGate.ACTION_LOCKED_BOOT_COMPLETED));
    }

    @Test
    public void myPackageReplaced_isNotReboot() {
        assertFalse(BootScheduleGate.isReboot(Intent.ACTION_MY_PACKAGE_REPLACED));
    }

    @Test
    public void nullAction_isNotReboot() {
        assertFalse(BootScheduleGate.isReboot((String) null));
    }

    @Test
    public void nullIntent_isNotReboot() {
        assertFalse(BootScheduleGate.isReboot((Intent) null));
    }

    @Test
    public void intentWithBootAction_isReboot() {
        Intent intent = mock(Intent.class);
        when(intent.getAction()).thenReturn(Intent.ACTION_BOOT_COMPLETED);
        assertTrue(BootScheduleGate.isReboot(intent));
    }
}
