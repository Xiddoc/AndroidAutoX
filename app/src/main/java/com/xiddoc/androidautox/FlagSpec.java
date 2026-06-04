package com.xiddoc.androidautox;

/**
 * Describes a single flag override to apply to the new "phixit" Phenotype
 * snapshot: which config package it belongs to, its name, and its value/type
 * (carried in {@link #flag}). A spec may also mark a flag for removal (used by
 * the revert path to drop flags that were appended).
 */
public final class FlagSpec {

    public static final String PKG_GEARHEAD = "com.google.android.projection.gearhead";
    public static final String PKG_CAR = "com.google.android.gms.car";

    public final String pkg;
    public final String name;
    public final PhixitSnapshot.Flag flag; // value/type to write (null when remove==true)
    public final boolean remove;

    private FlagSpec(String pkg, String name, PhixitSnapshot.Flag flag, boolean remove) {
        this.pkg = pkg;
        this.name = name;
        this.flag = flag;
        this.remove = remove;
    }

    private static PhixitSnapshot.Flag base(String name, int type) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.numericName = false;
        f.type = type;
        return f;
    }

    public static FlagSpec bool(String pkg, String name, boolean value) {
        return new FlagSpec(pkg, name,
                base(name, value ? PhixitSnapshot.TYPE_BOOL_TRUE : PhixitSnapshot.TYPE_BOOL_FALSE), false);
    }

    public static FlagSpec lng(String pkg, String name, long value) {
        PhixitSnapshot.Flag f = base(name, PhixitSnapshot.TYPE_LONG);
        f.longValue = value;
        return new FlagSpec(pkg, name, f, false);
    }

    public static FlagSpec dbl(String pkg, String name, double value) {
        PhixitSnapshot.Flag f = base(name, PhixitSnapshot.TYPE_DOUBLE);
        f.doubleBits = Double.doubleToRawLongBits(value);
        return new FlagSpec(pkg, name, f, false);
    }

    public static FlagSpec str(String pkg, String name, String value) {
        PhixitSnapshot.Flag f = base(name, PhixitSnapshot.TYPE_STRING);
        f.stringValue = value;
        return new FlagSpec(pkg, name, f, false);
    }

    public static FlagSpec bytes(String pkg, String name, byte[] value) {
        PhixitSnapshot.Flag f = base(name, PhixitSnapshot.TYPE_BYTES);
        f.bytesValue = value;
        return new FlagSpec(pkg, name, f, false);
    }

    public static FlagSpec remove(String pkg, String name) {
        return new FlagSpec(pkg, name, null, true);
    }
}
