package com.xiddoc.androidautox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xiddoc.androidautox.AppCompatibilityClassifier.Category;

import org.junit.Test;

/**
 * Plain-JUnit tests for {@link AppInfo}: getters/setters and the {@code compareTo}
 * ordering, which sorts checked-before-unchecked, then known-AA-app-before-other,
 * then alphabetically by display name.
 */
public class AppInfoTest {

    private static final String KNOWN_AA_APP = "me.aap.fermata.auto";
    private static final String OTHER_APP = "com.example.other";

    @Test
    public void gettersReflectConstructor() {
        AppInfo a = new AppInfo("Name", "pkg", true);
        assertEquals("Name", a.getName());
        assertEquals("pkg", a.getPackageName());
        assertTrue(a.getIsChecked());
    }

    @Test
    public void setters() {
        AppInfo a = new AppInfo("Name", "pkg", false);
        a.setName("New");
        a.setPackageName("pkg2");
        a.setIsChecked(true);
        assertEquals("New", a.getName());
        assertEquals("pkg2", a.getPackageName());
        assertTrue(a.getIsChecked());
    }

    // --- compareTo: checked beats unchecked ------------------------------

    @Test
    public void compareTo_checkedSortsBeforeUnchecked() {
        AppInfo checked = new AppInfo("Zeta", OTHER_APP, true);
        AppInfo unchecked = new AppInfo("Alpha", OTHER_APP, false);
        // checked should order before unchecked despite name ordering.
        assertTrue(checked.compareTo(unchecked) < 0);
        assertTrue(unchecked.compareTo(checked) > 0);
    }

    // --- compareTo: known AA app beats unknown when checked equal --------

    @Test
    public void compareTo_knownAaAppSortsBeforeOther() {
        AppInfo known = new AppInfo("Zeta", KNOWN_AA_APP, false);
        AppInfo other = new AppInfo("Alpha", OTHER_APP, false);
        assertTrue(known.compareTo(other) < 0);
        assertTrue(other.compareTo(known) > 0);
    }

    @Test
    public void compareTo_bothKnownAaApps_fallsToName() {
        AppInfo a = new AppInfo("Alpha", "me.aap.fermata.auto", false);
        AppInfo b = new AppInfo("Beta", "com.garage.aastream", false);
        // Both are in the known list -> tie -> name ordering ("Alpha" < "Beta").
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }

    // --- compareTo: name fallback when checked & membership equal ---------

    @Test
    public void compareTo_sameCheckedAndMembership_ordersByName() {
        AppInfo a = new AppInfo("Apple", OTHER_APP, true);
        AppInfo b = new AppInfo("Banana", OTHER_APP, true);
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }

    @Test
    public void compareTo_identical_isZero() {
        AppInfo a = new AppInfo("Same", OTHER_APP, true);
        AppInfo b = new AppInfo("Same", OTHER_APP, true);
        assertEquals(0, a.compareTo(b));
    }

    @Test
    public void compareTo_checkedNotChecked_ignoresAaMembership() {
        // Checked-state dominates: an unchecked known AA app still sorts after a
        // checked non-AA app.
        AppInfo checkedOther = new AppInfo("Z", OTHER_APP, true);
        AppInfo uncheckedKnown = new AppInfo("A", KNOWN_AA_APP, false);
        assertTrue(checkedOther.compareTo(uncheckedKnown) < 0);
    }

    @Test
    public void isChecked_falseGetter() {
        assertFalse(new AppInfo("n", "p", false).getIsChecked());
    }

    // --- category ---------------------------------------------------------

    @Test
    public void threeArgConstructor_defaultsToNeedsBridge() {
        AppInfo a = new AppInfo("Name", "pkg", true);
        assertEquals(Category.NEEDS_BRIDGE, a.getCategory());
    }

    @Test
    public void fourArgConstructor_retainsCategory() {
        AppInfo a = new AppInfo("Name", "pkg", false, Category.NATIVE_AUTO);
        assertEquals(Category.NATIVE_AUTO, a.getCategory());
        assertEquals("Name", a.getName());
        assertEquals("pkg", a.getPackageName());
        assertFalse(a.getIsChecked());

        AppInfo mirror = new AppInfo("M", "p", true, Category.MIRROR_SHIM);
        assertEquals(Category.MIRROR_SHIM, mirror.getCategory());
    }
}
