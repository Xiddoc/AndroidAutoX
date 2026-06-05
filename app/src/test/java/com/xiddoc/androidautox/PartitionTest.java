package com.xiddoc.androidautox;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link Partition} parcelable. The pure {@code (long, byte[])}
 * constructor and fields are checked directly; the Parcel constructor and
 * {@code writeToParcel} are exercised via a real Robolectric {@link Parcel}.
 */
@RunWith(AndroidJUnit4.class)
public class PartitionTest {

    @Test
    public void defaultCtor_hasZeroIdAndNullBlob() {
        Partition p = new Partition();
        assertEquals(0L, p.id);
        assertNull(p.blob);
    }

    @Test
    public void valueCtor_setsFields() {
        byte[] blob = {1, 2, 3};
        Partition p = new Partition(7L, blob);
        assertEquals(7L, p.id);
        assertArrayEquals(blob, p.blob);
    }

    @Test
    public void describeContents_isZero() {
        assertEquals(0, new Partition(1L, new byte[0]).describeContents());
    }

    @Test
    public void parcelRoundTrip() {
        Partition original = new Partition(99L, new byte[]{4, 5, 6, 7});

        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Partition restored = Partition.CREATOR.createFromParcel(parcel);
            assertEquals(99L, restored.id);
            assertArrayEquals(new byte[]{4, 5, 6, 7}, restored.blob);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void parcelRoundTrip_nullBlob() {
        Partition original = new Partition(3L, null);

        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Partition restored = Partition.CREATOR.createFromParcel(parcel);
            assertEquals(3L, restored.id);
            assertNull(restored.blob);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void creator_newArray() {
        Partition[] arr = Partition.CREATOR.newArray(3);
        assertEquals(3, arr.length);
        assertNull(arr[0]);
    }
}
