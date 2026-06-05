package com.xiddoc.androidautox;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Codec for the "phixit" Phenotype snapshot blob stored in
 * {@code param_partitions.flags_content} on recent Google Play Services.
 *
 * <p>The blob is raw-DEFLATE compressed (no zlib/gzip header). The decompressed
 * payload is a hand-rolled protobuf-style stream:
 * <pre>
 *   uint32  count
 *   repeat count times:
 *     uint64 theory       // type = theory &amp; 7 ; shift = theory &gt;&gt;&gt; 3
 *     name                // if shift==0: length-delimited UTF-8 string
 *                         // else:        numeric id, delta-coded (shift + prev)
 *     value               // type 0/1 (bool false/true): no bytes
 *                         // type 2   (long):    uint64 varint
 *                         // type 3   (double):  fixed64 little-endian
 *                         // type 4   (string):  length-delimited UTF-8
 *                         // type 5   (bytes):   length-delimited bytes
 * </pre>
 *
 * This is a direct port of GMS-Phixit's {@code dumpFlags}/{@code encodeAndSave}
 * and {@code ZipUtils} so that AndroidAutoX can edit the served flag snapshot
 * the same way the schema change now requires.
 */
public final class PhixitSnapshot {

    public static final int TYPE_BOOL_FALSE = 0;
    public static final int TYPE_BOOL_TRUE = 1;
    public static final int TYPE_LONG = 2;
    public static final int TYPE_DOUBLE = 3;
    public static final int TYPE_STRING = 4;
    public static final int TYPE_BYTES = 5;

    private PhixitSnapshot() {
    }

    /** A single flag entry within a partition. */
    public static final class Flag {
        /** Flag name. For numeric (delta-coded) entries this is the decimal id. */
        public String name;
        /** True when {@link #name} was stored as a numeric delta-coded id. */
        public boolean numericName;
        /** One of the TYPE_* constants. */
        public int type;
        public long longValue;   // TYPE_LONG
        public long doubleBits;  // TYPE_DOUBLE (raw IEEE-754 bits)
        public String stringValue; // TYPE_STRING
        public byte[] bytesValue;  // TYPE_BYTES

        public boolean boolValue() {
            return type == TYPE_BOOL_TRUE;
        }

        public String describe() {
            switch (type) {
                case TYPE_BOOL_FALSE: return name + " = false (bool)";
                case TYPE_BOOL_TRUE:  return name + " = true (bool)";
                case TYPE_LONG:       return name + " = " + longValue + " (long)";
                case TYPE_DOUBLE:     return name + " = " + Double.longBitsToDouble(doubleBits) + " (double)";
                case TYPE_STRING:     return name + " = \"" + stringValue + "\" (string)";
                case TYPE_BYTES:      return name + " = [" + (bytesValue == null ? 0 : bytesValue.length) + " bytes]";
                default:              return name + " = ? (type " + type + ")";
            }
        }
    }

    // ---------------------------------------------------------------------
    // Decode
    // ---------------------------------------------------------------------

    public static List<Flag> decode(byte[] decompressed) {
        Reader in = new Reader(decompressed);
        long count = in.readVarint();
        long next = 0L;
        List<Flag> flags = new ArrayList<Flag>((int) count);
        for (long i = 0; i < count; i++) {
            long theory = in.readVarint();
            long shift = theory >>> 3;
            int type = (int) (theory & 7);

            Flag f = new Flag();
            f.type = type;
            if (shift == 0L) {
                f.name = in.readString();
                f.numericName = false;
            } else {
                next = shift + next;
                f.name = Long.toString(next);
                f.numericName = true;
            }

            switch (type) {
                case TYPE_BOOL_FALSE:
                case TYPE_BOOL_TRUE:
                    break; // no payload
                case TYPE_LONG:
                    f.longValue = in.readVarint();
                    break;
                case TYPE_DOUBLE:
                    f.doubleBits = in.readFixed64();
                    break;
                case TYPE_STRING:
                    f.stringValue = in.readString();
                    break;
                case TYPE_BYTES:
                    f.bytesValue = in.readBytes();
                    break;
                default:
                    throw new IllegalStateException("Unknown flag type: " + type);
            }
            flags.add(f);
        }
        return flags;
    }

    // ---------------------------------------------------------------------
    // Encode
    // ---------------------------------------------------------------------

    public static byte[] encode(List<Flag> flags) {
        Writer out = new Writer();
        out.writeVarint(flags.size());
        long next = 0L;
        for (Flag f : flags) {
            Long ln = tryParseLong(f.name);
            if (ln != null) {
                long theory = ((ln - next) << 3) | (f.type & 7);
                out.writeVarint(theory);
                next = ln;
            } else {
                out.writeVarint(f.type & 7); // shift == 0 => string name follows
                out.writeString(f.name);
            }

            switch (f.type) {
                case TYPE_BOOL_FALSE:
                case TYPE_BOOL_TRUE:
                    break;
                case TYPE_LONG:
                    out.writeVarint(f.longValue);
                    break;
                case TYPE_DOUBLE:
                    out.writeFixed64(f.doubleBits);
                    break;
                case TYPE_STRING:
                    out.writeString(f.stringValue == null ? "" : f.stringValue);
                    break;
                case TYPE_BYTES:
                    out.writeBytes(f.bytesValue == null ? new byte[0] : f.bytesValue);
                    break;
                default:
                    throw new IllegalStateException("Unknown flag type: " + f.type);
            }
        }
        return out.toByteArray();
    }

    private static Long tryParseLong(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Long.valueOf(Long.parseLong(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Raw DEFLATE (matches ZipUtils: Inflater(true) / Deflater(1, true))
    // ---------------------------------------------------------------------

    public static byte[] inflateRaw(byte[] compressed) {
        Inflater inflater = new Inflater(true);
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, compressed.length * 3));
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0) {
                    // Raw (nowrap) inflate produces no output only when it is
                    // finished or has run out of input; either way no further
                    // progress is possible, so stop. (needsDictionary() cannot
                    // occur for a raw stream.)
                    break;
                }
                out.write(buffer, 0, n);
            }
        } catch (DataFormatException e) {
            throw new RuntimeException("inflateRaw failed: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    public static byte[] deflateRaw(byte[] data) {
        Deflater deflater = new Deflater(1, true); // level 1, nowrap
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 2));
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.write(buffer, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------------
    // Hex helpers (sqlite3 hex()/x'..' interchange)
    // ---------------------------------------------------------------------

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    public static byte[] hexToBytes(String hex) {
        hex = hex.trim();
        int len = hex.length();
        if ((len & 1) != 0) throw new IllegalArgumentException("Odd hex length: " + len);
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((digit(hex.charAt(i)) << 4) | digit(hex.charAt(i + 1)));
        }
        return out;
    }

    private static int digit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        throw new IllegalArgumentException("Bad hex char: " + c);
    }

    // ---------------------------------------------------------------------
    // Minimal varint / fixed64 reader & writer
    // ---------------------------------------------------------------------

    private static final class Reader {
        private final byte[] buf;
        private int pos;

        Reader(byte[] buf) {
            this.buf = buf;
        }

        long readVarint() {
            long result = 0L;
            int shift = 0;
            while (true) {
                int b = buf[pos++] & 0xFF;
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return result;
                shift += 7;
            }
        }

        long readFixed64() {
            long v = 0L;
            for (int i = 0; i < 8; i++) {
                v |= (long) (buf[pos++] & 0xFF) << (8 * i);
            }
            return v;
        }

        String readString() {
            int len = (int) readVarint();
            String s = new String(buf, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }

        byte[] readBytes() {
            int len = (int) readVarint();
            byte[] out = new byte[len];
            System.arraycopy(buf, pos, out, 0, len);
            pos += len;
            return out;
        }
    }

    private static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void writeVarint(long value) {
            while ((value & ~0x7FL) != 0L) {
                out.write((int) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
            out.write((int) value);
        }

        void writeFixed64(long bits) {
            for (int i = 0; i < 8; i++) {
                out.write((int) ((bits >>> (8 * i)) & 0xFF));
            }
        }

        void writeString(String s) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            writeVarint(bytes.length);
            out.write(bytes, 0, bytes.length);
        }

        void writeBytes(byte[] bytes) {
            writeVarint(bytes.length);
            out.write(bytes, 0, bytes.length);
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
