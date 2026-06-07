package com.xiddoc.androidautox.autox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-JUnit tests for {@link HostAllowlist} — no Android runtime required.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link HostAllowlist#normalizeDigest} — null, blank, correct plain hex,
 *       correct colon-separated hex, lowercase input, wrong length, non-hex chars.</li>
 *   <li>{@link HostAllowlist#check} — every {@link HostAllowlist.AllowResult} branch:
 *       ALLOWED (colon form), ALLOWED (plain hex), ALLOWED (lowercase), ALLOWED (second
 *       digest), REJECTED_NULL_PACKAGE (null + blank), REJECTED_MALFORMED_DIGEST (null
 *       digest + bad digest), REJECTED_UNKNOWN_PACKAGE, REJECTED_WRONG_DIGEST.</li>
 *   <li>{@link HostAllowlist#isAllowed} — true and false paths.</li>
 *   <li>{@link HostAllowlist#entries()} — returns the right entries.</li>
 *   <li>{@link HostAllowlist#createDefault()} — default entry present, gearhead
 *       constants correct.</li>
 *   <li>{@link HostAllowlist.HostEntry} construction — valid and invalid args.</li>
 *   <li>{@link HostAllowlist} constructor — null/empty entries rejected.</li>
 * </ul>
 */
public class HostAllowlistTest {

    // ------------------------------------------------------------------
    // Test fixtures
    // ------------------------------------------------------------------

    // 64-char plain hex (valid SHA-256)
    private static final String GOOD_DIGEST_PLAIN =
            "F0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB83";
    // Same digest colon-separated (the canonical keytool form stored in HostAllowlist)
    private static final String GOOD_DIGEST_COLON =
            "F0:FD:6C:5B:41:0F:25:CB:25:C3:B5:33:46:C8:97:2F:" +
            "AE:30:F8:EE:74:11:DF:91:04:80:AD:6B:2D:60:DB:83";
    // Same digest, lowercase
    private static final String GOOD_DIGEST_LOWER =
            "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83";

    private static final String ALT_DIGEST_PLAIN =
            "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899";

    private static final String PACKAGE_OK = "com.google.android.projection.gearhead";
    private static final String PACKAGE_BAD = "com.evil.fake.autoapk";

    private static HostAllowlist singleEntry(String pkg, String digest) {
        List<String> digests = new ArrayList<>();
        digests.add(digest);
        List<HostAllowlist.HostEntry> entries = new ArrayList<>();
        entries.add(new HostAllowlist.HostEntry(pkg, digests));
        return new HostAllowlist(entries);
    }

    private static HostAllowlist twoDigestEntry(String pkg,
                                                String digest1,
                                                String digest2) {
        List<String> digests = new ArrayList<>();
        digests.add(digest1);
        digests.add(digest2);
        List<HostAllowlist.HostEntry> entries = new ArrayList<>();
        entries.add(new HostAllowlist.HostEntry(pkg, digests));
        return new HostAllowlist(entries);
    }

    // ------------------------------------------------------------------
    // normalizeDigest — null / blank
    // ------------------------------------------------------------------

    @Test
    public void normalizeDigest_null_returnsNull() {
        assertNull(HostAllowlist.normalizeDigest(null));
    }

    @Test
    public void normalizeDigest_empty_returnsNull() {
        assertNull(HostAllowlist.normalizeDigest(""));
    }

    @Test
    public void normalizeDigest_blank_returnsNull() {
        assertNull(HostAllowlist.normalizeDigest("   "));
    }

    // ------------------------------------------------------------------
    // normalizeDigest — valid inputs
    // ------------------------------------------------------------------

    @Test
    public void normalizeDigest_plainUppercaseHex_returnsSame() {
        String result = HostAllowlist.normalizeDigest(GOOD_DIGEST_PLAIN);
        assertEquals(GOOD_DIGEST_PLAIN, result);
    }

    @Test
    public void normalizeDigest_colonSeparated_stripsColons() {
        String result = HostAllowlist.normalizeDigest(GOOD_DIGEST_COLON);
        assertEquals(GOOD_DIGEST_PLAIN, result);
    }

    @Test
    public void normalizeDigest_lowercaseHex_uppercases() {
        String result = HostAllowlist.normalizeDigest(GOOD_DIGEST_LOWER);
        assertEquals(GOOD_DIGEST_PLAIN, result);
    }

    @Test
    public void normalizeDigest_validResult_is64chars() {
        String result = HostAllowlist.normalizeDigest(GOOD_DIGEST_PLAIN);
        assertNotNull(result);
        assertEquals(64, result.length());
    }

    // ------------------------------------------------------------------
    // normalizeDigest — invalid length
    // ------------------------------------------------------------------

    @Test
    public void normalizeDigest_tooShort_returnsNull() {
        // 63 hex chars
        assertNull(HostAllowlist.normalizeDigest(
                "F0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB8"));
    }

    @Test
    public void normalizeDigest_tooLong_returnsNull() {
        // 65 hex chars
        assertNull(HostAllowlist.normalizeDigest(
                "F0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB83FF"));
    }

    // ------------------------------------------------------------------
    // normalizeDigest — non-hex characters
    // ------------------------------------------------------------------

    @Test
    public void normalizeDigest_containsG_returnsNull() {
        // Replace one valid hex char with 'G' (invalid)
        String bad = "G0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB83";
        assertEquals(64, bad.length());
        assertNull(HostAllowlist.normalizeDigest(bad));
    }

    @Test
    public void normalizeDigest_containsSpace_returnsNull() {
        // 64 chars but with a space
        String bad = "F0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB 3";
        assertEquals(64, bad.length());
        assertNull(HostAllowlist.normalizeDigest(bad));
    }

    // ------------------------------------------------------------------
    // check — ALLOWED
    // ------------------------------------------------------------------

    @Test
    public void check_colonDigest_allowed() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.ALLOWED,
                al.check(PACKAGE_OK, GOOD_DIGEST_COLON));
    }

    @Test
    public void check_plainDigest_allowed() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.ALLOWED,
                al.check(PACKAGE_OK, GOOD_DIGEST_PLAIN));
    }

    @Test
    public void check_lowercaseDigest_allowed() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.ALLOWED,
                al.check(PACKAGE_OK, GOOD_DIGEST_LOWER));
    }

    @Test
    public void check_secondDigest_allowed() {
        HostAllowlist al = twoDigestEntry(PACKAGE_OK, ALT_DIGEST_PLAIN, GOOD_DIGEST_PLAIN);
        assertEquals(HostAllowlist.AllowResult.ALLOWED,
                al.check(PACKAGE_OK, GOOD_DIGEST_PLAIN));
    }

    @Test
    public void check_firstOfTwoDigests_allowed() {
        HostAllowlist al = twoDigestEntry(PACKAGE_OK, GOOD_DIGEST_PLAIN, ALT_DIGEST_PLAIN);
        assertEquals(HostAllowlist.AllowResult.ALLOWED,
                al.check(PACKAGE_OK, GOOD_DIGEST_PLAIN));
    }

    // ------------------------------------------------------------------
    // check — REJECTED_NULL_PACKAGE
    // ------------------------------------------------------------------

    @Test
    public void check_nullPackage_rejectedNullPackage() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.REJECTED_NULL_PACKAGE,
                al.check(null, GOOD_DIGEST_PLAIN));
    }

    @Test
    public void check_emptyPackage_rejectedNullPackage() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.REJECTED_NULL_PACKAGE,
                al.check("", GOOD_DIGEST_PLAIN));
    }

    @Test
    public void check_blankPackage_rejectedNullPackage() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.REJECTED_NULL_PACKAGE,
                al.check("   ", GOOD_DIGEST_PLAIN));
    }

    // ------------------------------------------------------------------
    // check — REJECTED_MALFORMED_DIGEST
    // ------------------------------------------------------------------

    @Test
    public void check_nullDigest_rejectedMalformed() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.REJECTED_MALFORMED_DIGEST,
                al.check(PACKAGE_OK, null));
    }

    @Test
    public void check_badDigest_rejectedMalformed() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.REJECTED_MALFORMED_DIGEST,
                al.check(PACKAGE_OK, "not-a-digest"));
    }

    @Test
    public void check_blankDigest_rejectedMalformed() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.REJECTED_MALFORMED_DIGEST,
                al.check(PACKAGE_OK, "  "));
    }

    // ------------------------------------------------------------------
    // check — REJECTED_UNKNOWN_PACKAGE
    // ------------------------------------------------------------------

    @Test
    public void check_unknownPackage_rejectedUnknown() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.REJECTED_UNKNOWN_PACKAGE,
                al.check(PACKAGE_BAD, GOOD_DIGEST_PLAIN));
    }

    // ------------------------------------------------------------------
    // check — REJECTED_WRONG_DIGEST
    // ------------------------------------------------------------------

    @Test
    public void check_wrongDigest_rejectedWrongDigest() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertEquals(HostAllowlist.AllowResult.REJECTED_WRONG_DIGEST,
                al.check(PACKAGE_OK, ALT_DIGEST_PLAIN));
    }

    /**
     * Covers the {@code normalizedStored == null} branch inside the inner loop of
     * {@link HostAllowlist#check}: when a stored digest is malformed (normalizeDigest
     * returns null), the inner comparison is skipped and the loop continues. If no
     * valid stored digest matches, the method returns REJECTED_WRONG_DIGEST.
     */
    @Test
    public void check_malformedStoredDigest_skippedAndReturnsWrongDigest() {
        // HostEntry allows any string as a digest; a malformed one should be skipped.
        HostAllowlist al = twoDigestEntry(PACKAGE_OK,
                "not-a-valid-sha256-digest",   // malformed — normalizeDigest returns null
                ALT_DIGEST_PLAIN);             // valid but doesn't match the candidate
        assertEquals(HostAllowlist.AllowResult.REJECTED_WRONG_DIGEST,
                al.check(PACKAGE_OK, GOOD_DIGEST_PLAIN));
    }

    /**
     * Verifies that a malformed stored digest is skipped but a valid second digest
     * that matches is still found, returning ALLOWED.
     */
    @Test
    public void check_malformedStoredDigestThenValidMatch_allowed() {
        HostAllowlist al = twoDigestEntry(PACKAGE_OK,
                "not-a-valid-sha256",   // malformed — skipped
                GOOD_DIGEST_PLAIN);     // valid and matches the candidate
        assertEquals(HostAllowlist.AllowResult.ALLOWED,
                al.check(PACKAGE_OK, GOOD_DIGEST_PLAIN));
    }

    // ------------------------------------------------------------------
    // isAllowed — convenience wrapper
    // ------------------------------------------------------------------

    @Test
    public void isAllowed_matchingHostAndDigest_returnsTrue() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_PLAIN);
        assertTrue(al.isAllowed(PACKAGE_OK, GOOD_DIGEST_PLAIN));
    }

    @Test
    public void isAllowed_unknownPackage_returnsFalse() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_PLAIN);
        assertFalse(al.isAllowed(PACKAGE_BAD, GOOD_DIGEST_PLAIN));
    }

    @Test
    public void isAllowed_wrongDigest_returnsFalse() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_COLON);
        assertFalse(al.isAllowed(PACKAGE_OK, ALT_DIGEST_PLAIN));
    }

    @Test
    public void isAllowed_nullPackage_returnsFalse() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_PLAIN);
        assertFalse(al.isAllowed(null, GOOD_DIGEST_PLAIN));
    }

    @Test
    public void isAllowed_nullDigest_returnsFalse() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_PLAIN);
        assertFalse(al.isAllowed(PACKAGE_OK, null));
    }

    // ------------------------------------------------------------------
    // entries()
    // ------------------------------------------------------------------

    @Test
    public void entries_returnsCorrectSize() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_PLAIN);
        assertEquals(1, al.entries().size());
    }

    @Test
    public void entries_returnsCorrectPackageName() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_PLAIN);
        assertEquals(PACKAGE_OK, al.entries().get(0).packageName);
    }

    @Test
    public void entries_returnsCorrectDigest() {
        HostAllowlist al = singleEntry(PACKAGE_OK, GOOD_DIGEST_PLAIN);
        assertEquals(GOOD_DIGEST_PLAIN,
                al.entries().get(0).sha256Digests.get(0));
    }

    // ------------------------------------------------------------------
    // createDefault()
    // ------------------------------------------------------------------

    @Test
    public void createDefault_hasOneEntry() {
        HostAllowlist al = HostAllowlist.createDefault();
        assertEquals(1, al.entries().size());
    }

    @Test
    public void createDefault_entryIsGearhead() {
        HostAllowlist al = HostAllowlist.createDefault();
        assertEquals(HostAllowlist.GEARHEAD_PACKAGE,
                al.entries().get(0).packageName);
    }

    @Test
    public void createDefault_digestIsGearheadSha256() {
        HostAllowlist al = HostAllowlist.createDefault();
        assertEquals(HostAllowlist.GEARHEAD_SHA256,
                al.entries().get(0).sha256Digests.get(0));
    }

    @Test
    public void createDefault_gearheadDigest_isValidNormalizable() {
        assertNotNull(HostAllowlist.normalizeDigest(HostAllowlist.GEARHEAD_SHA256));
    }

    @Test
    public void createDefault_gearheadAllowed_withPlainDigest() {
        HostAllowlist al = HostAllowlist.createDefault();
        // The normalized plain form of GEARHEAD_SHA256
        String plain = HostAllowlist.normalizeDigest(HostAllowlist.GEARHEAD_SHA256);
        assertNotNull(plain);
        assertTrue(al.isAllowed(HostAllowlist.GEARHEAD_PACKAGE, plain));
    }

    // ------------------------------------------------------------------
    // HostEntry construction — valid
    // ------------------------------------------------------------------

    @Test
    public void hostEntry_valid_storesFields() {
        List<String> digests = new ArrayList<>();
        digests.add(GOOD_DIGEST_PLAIN);
        HostAllowlist.HostEntry entry =
                new HostAllowlist.HostEntry(PACKAGE_OK, digests);
        assertEquals(PACKAGE_OK, entry.packageName);
        assertEquals(1, entry.sha256Digests.size());
        assertEquals(GOOD_DIGEST_PLAIN, entry.sha256Digests.get(0));
    }

    // ------------------------------------------------------------------
    // HostEntry construction — invalid
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void hostEntry_nullPackage_throws() {
        List<String> digests = new ArrayList<>();
        digests.add(GOOD_DIGEST_PLAIN);
        new HostAllowlist.HostEntry(null, digests);
    }

    @Test(expected = IllegalArgumentException.class)
    public void hostEntry_blankPackage_throws() {
        List<String> digests = new ArrayList<>();
        digests.add(GOOD_DIGEST_PLAIN);
        new HostAllowlist.HostEntry("   ", digests);
    }

    @Test(expected = IllegalArgumentException.class)
    public void hostEntry_nullDigests_throws() {
        new HostAllowlist.HostEntry(PACKAGE_OK, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void hostEntry_emptyDigests_throws() {
        new HostAllowlist.HostEntry(PACKAGE_OK, new ArrayList<>());
    }

    // ------------------------------------------------------------------
    // HostAllowlist constructor — invalid
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullEntries_throws() {
        new HostAllowlist(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyEntries_throws() {
        new HostAllowlist(new ArrayList<>());
    }

    // ------------------------------------------------------------------
    // AllowResult enum sanity
    // ------------------------------------------------------------------

    @Test
    public void allowResult_allValuesExist() {
        assertNotNull(HostAllowlist.AllowResult.ALLOWED);
        assertNotNull(HostAllowlist.AllowResult.REJECTED_NULL_PACKAGE);
        assertNotNull(HostAllowlist.AllowResult.REJECTED_MALFORMED_DIGEST);
        assertNotNull(HostAllowlist.AllowResult.REJECTED_UNKNOWN_PACKAGE);
        assertNotNull(HostAllowlist.AllowResult.REJECTED_WRONG_DIGEST);
        assertEquals(5, HostAllowlist.AllowResult.values().length);
    }

    // ------------------------------------------------------------------
    // canonicalDigestForCarAppSdk
    // ------------------------------------------------------------------

    @Test
    public void canonical_fromColonUpper_isColonLower() {
        String out = HostAllowlist.canonicalDigestForCarAppSdk(HostAllowlist.GEARHEAD_SHA256);
        org.junit.Assert.assertNotNull(out);
        // 95 chars: 64 hex + 31 colons.
        assertEquals(95, out.length());
        assertEquals(out.toLowerCase(java.util.Locale.US), out); // already lowercase
        assertTrue(out.contains(":"));
        // Round-trips back to the same normalized digest.
        assertEquals(HostAllowlist.normalizeDigest(HostAllowlist.GEARHEAD_SHA256),
                HostAllowlist.normalizeDigest(out));
    }

    @Test
    public void canonical_fromPlainHex_addsColonsAndLowercases() {
        String plain = "F0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB83";
        String out = HostAllowlist.canonicalDigestForCarAppSdk(plain);
        assertEquals("f0:fd:6c:5b:41:0f:25:cb:25:c3:b5:33:46:c8:97:2f:"
                + "ae:30:f8:ee:74:11:df:91:04:80:ad:6b:2d:60:db:83", out);
    }

    @Test
    public void canonical_invalidDigest_returnsNull() {
        assertNull(HostAllowlist.canonicalDigestForCarAppSdk(null));
        assertNull(HostAllowlist.canonicalDigestForCarAppSdk("nothex"));
        assertNull(HostAllowlist.canonicalDigestForCarAppSdk("ABCD")); // wrong length
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private static void assertNull(Object obj) {
        org.junit.Assert.assertNull(obj);
    }
}
