package com.xiddoc.androidautox;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pure JUnit tests for {@link RootSqlText} — the query-result formatting and
 * statement-normalisation text logic extracted from {@link PhixitRootService}.
 */
public class RootSqlTextTest {

    // ------------------------------------------------------------------
    // formatRow
    // ------------------------------------------------------------------

    @Test
    public void formatRow_singleColumn() {
        assertEquals("a", RootSqlText.formatRow(new String[]{"a"}));
    }

    @Test
    public void formatRow_multipleColumns_pipeSeparated() {
        assertEquals("a|b|c", RootSqlText.formatRow(new String[]{"a", "b", "c"}));
    }

    @Test
    public void formatRow_nullColumn_renderedEmptyButSeparatorKept() {
        assertEquals("a||c", RootSqlText.formatRow(new String[]{"a", null, "c"}));
    }

    @Test
    public void formatRow_allNull_onlySeparators() {
        assertEquals("|", RootSqlText.formatRow(new String[]{null, null}));
    }

    @Test
    public void formatRow_empty_returnsEmpty() {
        assertEquals("", RootSqlText.formatRow(new String[]{}));
    }

    @Test
    public void formatRow_singleNull_returnsEmpty() {
        assertEquals("", RootSqlText.formatRow(new String[]{null}));
    }

    // ------------------------------------------------------------------
    // joinRows
    // ------------------------------------------------------------------

    @Test
    public void joinRows_empty_returnsEmpty() {
        assertEquals("", RootSqlText.joinRows(Collections.<String>emptyList()));
    }

    @Test
    public void joinRows_single_noNewline() {
        assertEquals("row1", RootSqlText.joinRows(Arrays.asList("row1")));
    }

    @Test
    public void joinRows_multiple_newlineSeparatedNoTrailing() {
        assertEquals("row1\nrow2\nrow3",
                RootSqlText.joinRows(Arrays.asList("row1", "row2", "row3")));
    }

    @Test
    public void joinRows_blankRowsPreserved() {
        // empty-string rows still produce blank lines (query returns them as-is)
        assertEquals("\n\n", RootSqlText.joinRows(Arrays.asList("", "", "")));
    }

    // ------------------------------------------------------------------
    // normalizeStatement
    // ------------------------------------------------------------------

    @Test
    public void normalizeStatement_null_returnsNull() {
        assertNull(RootSqlText.normalizeStatement(null));
    }

    @Test
    public void normalizeStatement_trimsWhitespace() {
        assertEquals("SELECT 1", RootSqlText.normalizeStatement("   SELECT 1   "));
    }

    @Test
    public void normalizeStatement_stripsSingleTrailingSemicolon() {
        assertEquals("DELETE FROM x", RootSqlText.normalizeStatement("DELETE FROM x;"));
    }

    @Test
    public void normalizeStatement_stripsTrailingSemicolonThenTrims() {
        assertEquals("DELETE FROM x",
                RootSqlText.normalizeStatement("  DELETE FROM x ;  "));
    }

    @Test
    public void normalizeStatement_keepsInnerSemicolons() {
        // a trigger body's inner ';' must survive; only the OUTERMOST ';' is stripped
        String trigger = "CREATE TRIGGER t BEGIN DELETE FROM a; DELETE FROM b; END;";
        assertEquals("CREATE TRIGGER t BEGIN DELETE FROM a; DELETE FROM b; END",
                RootSqlText.normalizeStatement(trigger));
    }

    @Test
    public void normalizeStatement_empty_returnsEmpty() {
        assertEquals("", RootSqlText.normalizeStatement(""));
    }

    @Test
    public void normalizeStatement_whitespaceOnly_returnsEmpty() {
        assertEquals("", RootSqlText.normalizeStatement("   "));
    }

    @Test
    public void normalizeStatement_semicolonOnly_returnsEmpty() {
        assertEquals("", RootSqlText.normalizeStatement(";"));
    }

    // ------------------------------------------------------------------
    // normalizeBatch
    // ------------------------------------------------------------------

    @Test
    public void normalizeBatch_dropsNullBlankAndSemicolonOnly_keepsOrder() {
        List<String> in = Arrays.asList(
                "SELECT 1;",
                null,
                "   ",
                ";",
                "  DELETE FROM x ;");
        List<String> out = RootSqlText.normalizeBatch(in);
        assertEquals(Arrays.asList("SELECT 1", "DELETE FROM x"), out);
    }

    @Test
    public void normalizeBatch_empty_returnsEmpty() {
        assertTrue(RootSqlText.normalizeBatch(Collections.<String>emptyList()).isEmpty());
    }

    @Test
    public void normalizeBatch_allKept() {
        List<String> out = RootSqlText.normalizeBatch(Arrays.asList("A", "B;"));
        assertEquals(Arrays.asList("A", "B"), out);
    }
}
