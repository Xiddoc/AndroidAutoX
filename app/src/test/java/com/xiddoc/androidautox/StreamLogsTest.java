package com.xiddoc.androidautox;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pure JUnit tests for {@link StreamLogs} — no Android runtime needed.
 *
 * Covers:
 *  - null streams return empty string (not NPE)
 *  - trimming of leading/trailing whitespace (including tab-only content)
 *  - \r\n handling (pinned behaviour)
 *  - WithLabel formatting for each individual stream
 *  - null-stream WithLabel parity for Input and Error
 *  - getStreamLogsWithLabels: always includes output, conditionally adds
 *    input/error only when non-empty; exact separator strings; correct order
 *  - idempotency / whitespace-suppression edge cases
 */
public class StreamLogsTest {

    // -----------------------------------------------------------------------
    // Shared fixture — shared @Before reduces boilerplate
    // -----------------------------------------------------------------------

    /** A fresh StreamLogs with all fields null (nothing set). */
    private StreamLogs sl;

    @Before
    public void setUp() {
        sl = new StreamLogs();
    }

    // -----------------------------------------------------------------------
    // getOutputStreamLog
    // -----------------------------------------------------------------------

    @Test
    public void outputStream_null_returnsEmpty() {
        // outputStreamLog is not set → null
        assertEquals("", sl.getOutputStreamLog());
    }

    @Test
    public void outputStream_trimsWhitespace() {
        sl.setOutputStreamLog("  hello world  \n");
        assertEquals("hello world", sl.getOutputStreamLog());
    }

    /**
     * A mid-content newline must be preserved; only leading/trailing whitespace is trimmed.
     * Renamed from the misleading "returnedAsIs" because trim() IS applied to the edges.
     */
    @Test
    public void outputStream_midlineNewlines_preservedAfterTrim() {
        sl.setOutputStreamLog("line1\nline2");
        assertEquals("line1\nline2", sl.getOutputStreamLog());
    }

    // -----------------------------------------------------------------------
    // getInputStreamLog
    // -----------------------------------------------------------------------

    @Test
    public void inputStream_null_returnsEmpty() {
        assertEquals("", sl.getInputStreamLog());
    }

    @Test
    public void inputStream_trimsWhitespace() {
        sl.setInputStreamLog("\t  input data  \t");
        assertEquals("input data", sl.getInputStreamLog());
    }

    /** Whitespace-only input (spaces + tabs) trims to "" — treated as absent. */
    @Test
    public void inputStream_whitespaceOnly_returnsEmpty() {
        sl.setInputStreamLog("   \t  \t");
        assertEquals("", sl.getInputStreamLog());
    }

    /** Tab-only content trims to "" — treated as absent (parity with whitespace-only). */
    @Test
    public void inputStream_tabOnly_returnsEmpty() {
        sl.setInputStreamLog("\t\t\t");
        assertEquals("", sl.getInputStreamLog());
    }

    // -----------------------------------------------------------------------
    // getErrorStreamLog
    // -----------------------------------------------------------------------

    @Test
    public void errorStream_null_returnsEmpty() {
        assertEquals("", sl.getErrorStreamLog());
    }

    @Test
    public void errorStream_trimsWhitespace() {
        sl.setErrorStreamLog("  error text  ");
        assertEquals("error text", sl.getErrorStreamLog());
    }

    /** Whitespace-only error (spaces + tabs) trims to "" — treated as absent. */
    @Test
    public void errorStream_whitespaceOnly_returnsEmpty() {
        sl.setErrorStreamLog("   \t  ");
        assertEquals("", sl.getErrorStreamLog());
    }

    /** Tab-only error trims to "" — treated as absent (parity with input). */
    @Test
    public void errorStream_tabOnly_returnsEmpty() {
        sl.setErrorStreamLog("\t");
        assertEquals("", sl.getErrorStreamLog());
    }

    // -----------------------------------------------------------------------
    // \r\n handling — pinned behaviour
    // -----------------------------------------------------------------------

    /**
     * \r\n line endings: the implementation uses String.trim() which strips leading/
     * trailing whitespace including \r.  An interior \r\n is preserved as-is by
     * getOutputStreamLog(); the replaceAll in *WithLabel replaces only bare \n, so
     * the \r remains part of the line content.  Pin this so a future change to
     * normalise \r\n is a conscious, visible decision.
     */
    @Test
    public void outputStream_crlfContent_crPreservedInMiddle() {
        sl.setOutputStreamLog("line1\r\nline2");
        // trim() strips trailing \r only if it was on the outside; interior \r stays.
        String got = sl.getOutputStreamLog();
        // The \r\n interior survives: "line1\r\nline2" — trim() doesn't touch the middle.
        assertEquals("line1\r\nline2", got);
    }

    /**
     * Trailing \r\n is trimmed away — only the non-whitespace content remains.
     */
    @Test
    public void outputStream_trailingCrLf_isTrimmed() {
        sl.setOutputStreamLog("result\r\n");
        assertEquals("result", sl.getOutputStreamLog());
    }

    // -----------------------------------------------------------------------
    // getOutputStreamLogWithLabel
    // -----------------------------------------------------------------------

    @Test
    public void outputWithLabel_singleLine_hasHeader() {
        sl.setOutputStreamLog("ok");
        String result = sl.getOutputStreamLogWithLabel();
        assertTrue(result.startsWith("\tOutputStream:\n\t\t"));
        assertTrue(result.endsWith("ok"));
    }

    @Test
    public void outputWithLabel_multiline_indentsEachLine() {
        sl.setOutputStreamLog("line1\nline2\nline3");
        String result = sl.getOutputStreamLogWithLabel();
        assertEquals("\tOutputStream:\n\t\tline1\n\t\tline2\n\t\tline3", result);
    }

    /** Null output stream → WithLabel still emits a header with an empty body. */
    @Test
    public void outputWithLabel_nullStream_hasHeaderAndEmptyBody() {
        // null → getOutputStreamLog() returns "" → replaceAll on "" → ""
        String result = sl.getOutputStreamLogWithLabel();
        assertEquals("\tOutputStream:\n\t\t", result);
    }

    // -----------------------------------------------------------------------
    // getInputStreamLogWithLabel
    // -----------------------------------------------------------------------

    @Test
    public void inputWithLabel_singleLine_hasHeader() {
        sl.setInputStreamLog("stdin content");
        String result = sl.getInputStreamLogWithLabel();
        assertTrue(result.startsWith("\tInputStream:\n\t\t"));
        assertTrue(result.endsWith("stdin content"));
    }

    @Test
    public void inputWithLabel_multiline_indentsEachLine() {
        sl.setInputStreamLog("a\nb");
        String result = sl.getInputStreamLogWithLabel();
        assertEquals("\tInputStream:\n\t\ta\n\t\tb", result);
    }

    /** Null input stream → WithLabel still emits a header (parity with output). */
    @Test
    public void inputWithLabel_nullStream_hasHeaderAndEmptyBody() {
        // inputStreamLog is null → getInputStreamLog() returns ""
        String result = sl.getInputStreamLogWithLabel();
        assertEquals("\tInputStream:\n\t\t", result);
    }

    // -----------------------------------------------------------------------
    // getErrorStreamLogWithLabel
    // -----------------------------------------------------------------------

    @Test
    public void errorWithLabel_singleLine_hasHeader() {
        sl.setErrorStreamLog("some error");
        String result = sl.getErrorStreamLogWithLabel();
        assertTrue(result.startsWith("\tErrorStream:\n\t\t"));
        assertTrue(result.endsWith("some error"));
    }

    @Test
    public void errorWithLabel_multiline_indentsEachLine() {
        sl.setErrorStreamLog("err1\nerr2");
        String result = sl.getErrorStreamLogWithLabel();
        assertEquals("\tErrorStream:\n\t\terr1\n\t\terr2", result);
    }

    /** Null error stream → WithLabel still emits a header (parity with input). */
    @Test
    public void errorWithLabel_nullStream_hasHeaderAndEmptyBody() {
        // errorStreamLog is null → getErrorStreamLog() returns ""
        String result = sl.getErrorStreamLogWithLabel();
        assertEquals("\tErrorStream:\n\t\t", result);
    }

    // -----------------------------------------------------------------------
    // getStreamLogsWithLabels — the combined view
    // -----------------------------------------------------------------------

    /**
     * All-null case: exact full-string assertion.
     * Expected: leading "\n" + output section with empty body; no Input/Error sections.
     */
    @Test
    public void streamLogsWithLabels_allNull_exactOutput() {
        // all fields null: output="" → WithLabel = "\tOutputStream:\n\t\t"
        String result = sl.getStreamLogsWithLabels();
        assertEquals("\n\tOutputStream:\n\t\t", result);
        assertFalse(result.contains("InputStream"));
        assertFalse(result.contains("ErrorStream"));
    }

    /**
     * Output-only (non-empty) case: starts with "\n" + output section; no Input/Error.
     */
    @Test
    public void streamLogsWithLabels_onlyOutput_noInputNoError() {
        sl.setOutputStreamLog("done");
        String result = sl.getStreamLogsWithLabels();

        assertTrue(result.startsWith("\n\tOutputStream:\n\t\tdone"));
        assertFalse(result.contains("InputStream"));
        assertFalse(result.contains("ErrorStream"));
    }

    @Test
    public void streamLogsWithLabels_outputAndInput_noError() {
        sl.setOutputStreamLog("out");
        sl.setInputStreamLog("in");
        String result = sl.getStreamLogsWithLabels();

        assertTrue(result.contains("OutputStream"));
        assertTrue(result.contains("InputStream"));
        assertFalse(result.contains("ErrorStream"));
    }

    @Test
    public void streamLogsWithLabels_outputAndError_noInput() {
        sl.setOutputStreamLog("out");
        sl.setErrorStreamLog("err");
        String result = sl.getStreamLogsWithLabels();

        assertTrue(result.contains("OutputStream"));
        assertFalse(result.contains("InputStream"));
        assertTrue(result.contains("ErrorStream"));
    }

    /**
     * All three non-empty: exact full-string assertion verifying separator "\n" between sections.
     */
    @Test
    public void streamLogsWithLabels_allThreeNonEmpty_exactOutput() {
        sl.setOutputStreamLog("OUT");
        sl.setInputStreamLog("IN");
        sl.setErrorStreamLog("ERR");
        String result = sl.getStreamLogsWithLabels();

        String expected =
                "\n\tOutputStream:\n\t\tOUT"
                + "\n\tInputStream:\n\t\tIN"
                + "\n\tErrorStream:\n\t\tERR";
        assertEquals(expected, result);
    }

    /**
     * Output + Error only (skip middle/Input): single "\n" separator between the two sections.
     */
    @Test
    public void streamLogsWithLabels_outputAndErrorOnly_exactSeparator() {
        sl.setOutputStreamLog("OUT");
        sl.setErrorStreamLog("ERR");
        String result = sl.getStreamLogsWithLabels();

        String expected =
                "\n\tOutputStream:\n\t\tOUT"
                + "\n\tErrorStream:\n\t\tERR";
        assertEquals(expected, result);
        assertFalse(result.contains("InputStream"));
    }

    /**
     * Whitespace-only Input suppresses the Input section (parity with empty-string check).
     */
    @Test
    public void streamLogsWithLabels_whitespaceOnlyInput_suppressedFromOutput() {
        sl.setOutputStreamLog("out");
        sl.setInputStreamLog("   ");  // whitespace-only → trimmed to "" → not included
        sl.setErrorStreamLog("");
        String result = sl.getStreamLogsWithLabels();

        assertFalse(result.contains("InputStream"));
        assertFalse(result.contains("ErrorStream"));
    }

    /**
     * Tab-only Input suppresses the Input section (parity with whitespace-only).
     */
    @Test
    public void streamLogsWithLabels_tabOnlyInput_suppressedFromOutput() {
        sl.setOutputStreamLog("out");
        sl.setInputStreamLog("\t\t");
        String result = sl.getStreamLogsWithLabels();

        assertFalse(result.contains("InputStream"));
    }

    /**
     * Whitespace-only Error suppresses the Error section (parity with Input).
     */
    @Test
    public void streamLogsWithLabels_whitespaceOnlyError_suppressedFromOutput() {
        sl.setOutputStreamLog("out");
        sl.setErrorStreamLog("   \t  ");
        String result = sl.getStreamLogsWithLabels();

        assertFalse(result.contains("ErrorStream"));
    }

    /**
     * Tab-only Error suppresses the Error section.
     */
    @Test
    public void streamLogsWithLabels_tabOnlyError_suppressedFromOutput() {
        sl.setOutputStreamLog("out");
        sl.setErrorStreamLog("\t");
        String result = sl.getStreamLogsWithLabels();

        assertFalse(result.contains("ErrorStream"));
    }

    @Test
    public void streamLogsWithLabels_emptyInput_notIncluded() {
        sl.setOutputStreamLog("out");
        sl.setInputStreamLog("   ");  // whitespace-only → trimmed to "" → empty
        sl.setErrorStreamLog("");
        String result = sl.getStreamLogsWithLabels();

        assertFalse(result.contains("InputStream"));
        assertFalse(result.contains("ErrorStream"));
    }

    @Test
    public void streamLogsWithLabels_nullStreams_onlyOutputSection() {
        // All three unset → input+error are null → treated as empty
        String result = sl.getStreamLogsWithLabels();

        assertTrue(result.contains("OutputStream"));
        assertFalse(result.contains("InputStream"));
        assertFalse(result.contains("ErrorStream"));
    }

    @Test
    public void streamLogsWithLabels_orderIsOutputThenInputThenError() {
        sl.setOutputStreamLog("OUT");
        sl.setInputStreamLog("IN");
        sl.setErrorStreamLog("ERR");
        String result = sl.getStreamLogsWithLabels();

        int outIdx = result.indexOf("OutputStream");
        int inIdx  = result.indexOf("InputStream");
        int errIdx = result.indexOf("ErrorStream");

        assertTrue(outIdx < inIdx);
        assertTrue(inIdx < errIdx);
    }

    /**
     * Trailing newline on output content is trimmed before the indent is applied.
     * The "\n" in "line1\nline2\n" at the end is stripped by trim(), so the WithLabel
     * result must NOT end with an extra "\n\t\t".
     */
    @Test
    public void outputWithLabel_trailingNewlineTrimmedBeforeIndent() {
        sl.setOutputStreamLog("line1\nline2\n");
        String result = sl.getOutputStreamLogWithLabel();
        // trim() removes the trailing \n so we get exactly two indented lines.
        assertEquals("\tOutputStream:\n\t\tline1\n\t\tline2", result);
    }
}
