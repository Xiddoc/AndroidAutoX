package com.xiddoc.androidautox;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-JUnit tests for the pure {@link PhixitSnapshot} codec: encode/decode
 * round-trips for every flag type and name encoding, the raw-DEFLATE helpers,
 * hex<->bytes interchange, and the {@link PhixitSnapshot.Flag} accessors.
 */
public class PhixitSnapshotTest {

    private static PhixitSnapshot.Flag strFlag(String name, String value) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.numericName = false;
        f.type = PhixitSnapshot.TYPE_STRING;
        f.stringValue = value;
        return f;
    }

    private static PhixitSnapshot.Flag longFlag(String name, long value) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.type = PhixitSnapshot.TYPE_LONG;
        f.longValue = value;
        return f;
    }

    private static PhixitSnapshot.Flag dblFlag(String name, double value) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.type = PhixitSnapshot.TYPE_DOUBLE;
        f.doubleBits = Double.doubleToRawLongBits(value);
        return f;
    }

    private static PhixitSnapshot.Flag bytesFlag(String name, byte[] value) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.type = PhixitSnapshot.TYPE_BYTES;
        f.bytesValue = value;
        return f;
    }

    /** Build a bool flag; {@code value} selects TYPE_BOOL_TRUE / TYPE_BOOL_FALSE. */
    private static PhixitSnapshot.Flag boolFlag(String name, boolean value) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = name;
        f.type = value ? PhixitSnapshot.TYPE_BOOL_TRUE : PhixitSnapshot.TYPE_BOOL_FALSE;
        return f;
    }

    /** Numeric (delta-coded) bool flag for the numeric-name round-trip tests. */
    private static PhixitSnapshot.Flag numericBoolFlag(long id) {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = Long.toString(id);
        f.numericName = true;
        f.type = PhixitSnapshot.TYPE_BOOL_TRUE;
        return f;
    }

    // --- Flag.boolValue / describe ---------------------------------------

    @Test
    public void boolValue_trueOnlyForBoolTrueType() {
        PhixitSnapshot.Flag t = new PhixitSnapshot.Flag();
        t.type = PhixitSnapshot.TYPE_BOOL_TRUE;
        assertTrue(t.boolValue());

        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.type = PhixitSnapshot.TYPE_BOOL_FALSE;
        assertFalse(f.boolValue());

        PhixitSnapshot.Flag l = new PhixitSnapshot.Flag();
        l.type = PhixitSnapshot.TYPE_LONG;
        assertFalse(l.boolValue());
    }

    @Test
    public void describe_coversEveryType() {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = "x";

        f.type = PhixitSnapshot.TYPE_BOOL_FALSE;
        assertEquals("x = false (bool)", f.describe());

        f.type = PhixitSnapshot.TYPE_BOOL_TRUE;
        assertEquals("x = true (bool)", f.describe());

        f.type = PhixitSnapshot.TYPE_LONG;
        f.longValue = 42L;
        assertEquals("x = 42 (long)", f.describe());

        f.type = PhixitSnapshot.TYPE_DOUBLE;
        f.doubleBits = Double.doubleToRawLongBits(1.5);
        assertEquals("x = 1.5 (double)", f.describe());

        f.type = PhixitSnapshot.TYPE_STRING;
        f.stringValue = "hi";
        assertEquals("x = \"hi\" (string)", f.describe());

        f.type = PhixitSnapshot.TYPE_BYTES;
        f.bytesValue = new byte[]{1, 2, 3};
        assertEquals("x = [3 bytes]", f.describe());
    }

    @Test
    public void describe_bytesNull_reportsZero() {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = "b";
        f.type = PhixitSnapshot.TYPE_BYTES;
        f.bytesValue = null;
        assertEquals("b = [0 bytes]", f.describe());
    }

    @Test
    public void describe_unknownType_fallsThrough() {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = "u";
        f.type = 9;
        assertEquals("u = ? (type 9)", f.describe());
    }

    // --- encode/decode round-trip for each type --------------------------

    @Test
    public void roundTrip_allTypes_stringNames() {
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();
        flags.add(boolFlag("boolFalse", false));
        flags.add(boolFlag("boolTrue", true));
        flags.add(longFlag("someLong", 1234567890123L));
        flags.add(dblFlag("someDouble", 3.14159));
        flags.add(strFlag("someString", "hello é world"));
        flags.add(bytesFlag("someBytes", new byte[]{0, 1, 2, (byte) 0xFF, 0x7F}));

        byte[] encoded = PhixitSnapshot.encode(flags);
        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(encoded);

        assertEquals(6, out.size());

        assertEquals("boolFalse", out.get(0).name);
        assertEquals(PhixitSnapshot.TYPE_BOOL_FALSE, out.get(0).type);
        assertFalse(out.get(0).numericName);
        assertFalse(out.get(0).boolValue());

        assertEquals("boolTrue", out.get(1).name);
        assertTrue(out.get(1).boolValue());

        assertEquals("someLong", out.get(2).name);
        assertEquals(1234567890123L, out.get(2).longValue);

        assertEquals("someDouble", out.get(3).name);
        assertEquals(3.14159, Double.longBitsToDouble(out.get(3).doubleBits), 0.0);

        assertEquals("someString", out.get(4).name);
        assertEquals("hello é world", out.get(4).stringValue);

        assertEquals("someBytes", out.get(5).name);
        assertArrayEquals(new byte[]{0, 1, 2, (byte) 0xFF, 0x7F}, out.get(5).bytesValue);
    }

    @Test
    public void roundTrip_numericDeltaCodedNames() {
        // Numeric names are delta-coded: encode stores (id - prev), decode rebuilds id.
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();
        for (long id : new long[]{100L, 105L, 1000L}) {
            flags.add(numericBoolFlag(id));
        }

        byte[] encoded = PhixitSnapshot.encode(flags);
        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(encoded);

        assertEquals(3, out.size());
        assertEquals("100", out.get(0).name);
        assertTrue(out.get(0).numericName);
        assertEquals("105", out.get(1).name);
        assertTrue(out.get(1).numericName);
        assertEquals("1000", out.get(2).name);
        assertTrue(out.get(2).numericName);
    }

    @Test
    public void encode_nullStringAndBytes_writtenAsEmpty() {
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();

        PhixitSnapshot.Flag s = new PhixitSnapshot.Flag();
        s.name = "emptyStr";
        s.type = PhixitSnapshot.TYPE_STRING;
        s.stringValue = null;
        flags.add(s);

        PhixitSnapshot.Flag b = new PhixitSnapshot.Flag();
        b.name = "emptyBytes";
        b.type = PhixitSnapshot.TYPE_BYTES;
        b.bytesValue = null;
        flags.add(b);

        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(PhixitSnapshot.encode(flags));
        assertEquals("", out.get(0).stringValue);
        assertArrayEquals(new byte[0], out.get(1).bytesValue);
    }

    @Test
    public void encode_emptyList() {
        byte[] encoded = PhixitSnapshot.encode(new ArrayList<PhixitSnapshot.Flag>());
        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(encoded);
        assertTrue(out.isEmpty());
    }

    @Test(expected = IllegalStateException.class)
    public void encode_unknownType_throws() {
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = "bad";
        f.type = 6; // not a valid TYPE_*
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();
        flags.add(f);
        PhixitSnapshot.encode(flags);
    }

    @Test
    public void decode_unknownType_throws() {
        // Hand-craft a stream: count(1), theory=(0<<3)|6=6, name len(1)+'x',
        // then no value bytes for the (illegal) type 6 -> hits the default branch.
        byte[] stream = new byte[]{1, 6, 1, 'x'};
        try {
            PhixitSnapshot.decode(stream);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Unknown flag type"));
        }
    }

    // --- decode corrupt / truncated input (documented failure modes) -----

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void decode_emptyInput_throwsAioobe() {
        // An empty stream has no bytes for the leading count varint, so the reader
        // immediately runs off the end of the buffer. Documented failure mode: the
        // decoder is not defensive and surfaces an ArrayIndexOutOfBoundsException.
        PhixitSnapshot.decode(new byte[0]);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void decode_overlongVarint_throwsAioobe() {
        // A varint whose every byte has the continuation bit (0x80) set never
        // terminates within the buffer, so readVarint walks past the end. This makes
        // the unbounded varint loop's failure mode explicit.
        PhixitSnapshot.decode(new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80});
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void decode_truncatedAfterCount_throwsAioobe() {
        // count=1 but no following theory/name bytes: reading the first entry runs
        // off the end of the buffer.
        PhixitSnapshot.decode(new byte[]{1});
    }

    // --- tryParseLong (exercised via encode name handling) ---------------

    @Test
    public void encode_numericStringName_treatedAsNumericId() {
        // A flag whose name parses as a long is delta-coded even if numericName=false.
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = "777";
        f.numericName = false;
        f.type = PhixitSnapshot.TYPE_BOOL_TRUE;
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();
        flags.add(f);

        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(PhixitSnapshot.encode(flags));
        assertEquals("777", out.get(0).name);
        assertTrue(out.get(0).numericName); // decode marks delta-coded names numeric
    }

    @Test
    public void encode_emptyNameNotParsedAsLong() {
        // Empty string name -> tryParseLong returns null -> stored as string name.
        PhixitSnapshot.Flag f = strFlag("", "v");
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();
        flags.add(f);
        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(PhixitSnapshot.encode(flags));
        assertEquals("", out.get(0).name);
        assertFalse(out.get(0).numericName);
        assertEquals("v", out.get(0).stringValue);
    }

    @Test(expected = NullPointerException.class)
    public void encode_nullName_isRejected() {
        // Documented contract: encode does not support null flag names. A null name
        // falls out of tryParseLong's null guard (treated as a non-numeric name) and
        // then fails when writeString tries to UTF-8 encode it, so callers must give
        // every flag a non-null name. The tryParseLong(null) branch is also covered
        // independently by the non-null numeric-name path (encode_numericStringName_*).
        PhixitSnapshot.Flag f = new PhixitSnapshot.Flag();
        f.name = null;
        f.type = PhixitSnapshot.TYPE_STRING;
        f.stringValue = "v";
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();
        flags.add(f);
        PhixitSnapshot.encode(flags);
    }

    @Test
    public void encode_nonNumericName_storedAsString() {
        PhixitSnapshot.Flag f = strFlag("not_a_number", "v");
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();
        flags.add(f);
        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(PhixitSnapshot.encode(flags));
        assertEquals("not_a_number", out.get(0).name);
        assertFalse(out.get(0).numericName);
    }

    // --- raw DEFLATE round-trip ------------------------------------------

    @Test
    public void inflateRaw_isInverseOf_deflateRaw() {
        byte[] data = "The quick brown fox jumps over the lazy dog. Repeat repeat repeat."
                .getBytes();
        byte[] compressed = PhixitSnapshot.deflateRaw(data);
        byte[] back = PhixitSnapshot.inflateRaw(compressed);
        assertArrayEquals(data, back);
    }

    @Test
    public void deflateInflate_emptyData() {
        byte[] compressed = PhixitSnapshot.deflateRaw(new byte[0]);
        assertArrayEquals(new byte[0], PhixitSnapshot.inflateRaw(compressed));
    }

    @Test
    public void deflateInflate_largeData_multipleBuffers() {
        byte[] data = new byte[20000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31);
        }
        byte[] compressed = PhixitSnapshot.deflateRaw(data);
        assertArrayEquals(data, PhixitSnapshot.inflateRaw(compressed));
    }

    @Test
    public void encodeDecode_throughCompression() {
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();
        flags.add(strFlag("a", "alpha"));
        flags.add(strFlag("b", "beta"));

        byte[] blob = PhixitSnapshot.deflateRaw(PhixitSnapshot.encode(flags));
        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(PhixitSnapshot.inflateRaw(blob));
        assertEquals("alpha", out.get(0).stringValue);
        assertEquals("beta", out.get(1).stringValue);
    }

    @Test(expected = RuntimeException.class)
    public void inflateRaw_invalidData_throws() {
        // Random bytes are not a valid raw-DEFLATE stream.
        PhixitSnapshot.inflateRaw(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void inflateRaw_truncatedStream_terminatesWithPartial() {
        // A valid but truncated raw-DEFLATE stream makes inflate() return 0 while not
        // finished (input exhausted), exercising the n==0 -> unconditional break path
        // (so we get a partial — not exception, not infinite loop).
        byte[] data = new byte[5000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 7);
        }
        byte[] compressed = PhixitSnapshot.deflateRaw(data);
        byte[] truncated = new byte[Math.max(1, compressed.length / 2)];
        System.arraycopy(compressed, 0, truncated, 0, truncated.length);

        byte[] partial = PhixitSnapshot.inflateRaw(truncated);
        // For this fixed input the decoder recovers exactly 524 leading bytes before
        // running out of input, and those bytes are a verbatim prefix of the original.
        assertEquals(524, partial.length);
        byte[] expectedPrefix = new byte[partial.length];
        System.arraycopy(data, 0, expectedPrefix, 0, partial.length);
        assertArrayEquals(expectedPrefix, partial);
    }

    // --- hex helpers ------------------------------------------------------

    @Test
    public void bytesToHex_uppercase() {
        assertEquals("00010AFF", PhixitSnapshot.bytesToHex(new byte[]{0, 1, 10, (byte) 0xFF}));
    }

    @Test
    public void bytesToHex_empty() {
        assertEquals("", PhixitSnapshot.bytesToHex(new byte[0]));
    }

    @Test
    public void hexToBytes_acceptsUpperLowerAndDigits() {
        assertArrayEquals(new byte[]{0, 1, 10, (byte) 0xFF},
                PhixitSnapshot.hexToBytes("00010aFF"));
    }

    @Test
    public void hexToBytes_trimsWhitespace() {
        assertArrayEquals(new byte[]{(byte) 0xAB}, PhixitSnapshot.hexToBytes("  AB  "));
    }

    @Test
    public void hexRoundTrip() {
        byte[] data = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF, 0x00, 0x7F};
        assertArrayEquals(data, PhixitSnapshot.hexToBytes(PhixitSnapshot.bytesToHex(data)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void hexToBytes_oddLength_throws() {
        PhixitSnapshot.hexToBytes("ABC");
    }

    @Test(expected = IllegalArgumentException.class)
    public void hexToBytes_badChar_aboveDigitRange_throws() {
        // 'G' is above '9' / above 'F' (and not a-f): exercises the upper-bound
        // false branches of digit()'s three range checks.
        PhixitSnapshot.hexToBytes("GG");
    }

    @Test(expected = IllegalArgumentException.class)
    public void hexToBytes_badChar_belowDigitRange_throws() {
        // '/' (0x2F) is below '0', below 'A', below 'a': exercises the lower-bound
        // false branches of digit()'s three range checks.
        PhixitSnapshot.hexToBytes("//");
    }

    @Test(expected = IllegalArgumentException.class)
    public void hexToBytes_badChar_aboveLowercaseF_throws() {
        // 'g' (0x67) is >= 'a' but > 'f' (and not 0-9, not A-F): exercises the
        // upper-bound false branch of digit()'s a-f range.
        PhixitSnapshot.hexToBytes("gg");
    }

    @Test
    public void hexToBytes_lowercaseAndUppercaseBoundaries() {
        // Cover the true side of each of digit()'s three ranges at their edges.
        assertArrayEquals(new byte[]{0x09}, PhixitSnapshot.hexToBytes("09"));   // 0-9
        assertArrayEquals(new byte[]{(byte) 0xAF}, PhixitSnapshot.hexToBytes("af")); // a-f
        assertArrayEquals(new byte[]{(byte) 0xAF}, PhixitSnapshot.hexToBytes("AF")); // A-F
    }

    // --- varint / fixed64 edge cases via round-trip ----------------------

    @Test
    public void varint_multiByteAndNegativeLong() {
        // -1L encodes to a full 10-byte varint, exercising the multi-byte loop fully.
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();
        flags.add(longFlag("neg", -1L));
        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(PhixitSnapshot.encode(flags));
        assertEquals(-1L, out.get(0).longValue);
    }

    @Test
    public void fixed64_negativeBits() {
        List<PhixitSnapshot.Flag> flags = new ArrayList<>();
        flags.add(dblFlag("d", Double.NEGATIVE_INFINITY));
        List<PhixitSnapshot.Flag> out = PhixitSnapshot.decode(PhixitSnapshot.encode(flags));
        assertEquals(Double.NEGATIVE_INFINITY, Double.longBitsToDouble(out.get(0).doubleBits), 0.0);
    }
}
