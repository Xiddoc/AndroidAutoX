package com.xiddoc.androidautox;

import android.os.Parcel;
import android.os.Parcelable;

/** A single {@code param_partitions} row: its id and raw {@code flags_content} blob. */
public class Partition implements Parcelable {
    public long id;
    public byte[] blob;

    public Partition() {}

    public Partition(long id, byte[] blob) {
        this.id = id;
        this.blob = blob;
    }

    protected Partition(Parcel in) {
        id = in.readLong();
        blob = in.createByteArray();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeByteArray(blob);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Partition> CREATOR = new Creator<Partition>() {
        @Override
        public Partition createFromParcel(Parcel in) {
            return new Partition(in);
        }

        @Override
        public Partition[] newArray(int size) {
            return new Partition[size];
        }
    };
}
