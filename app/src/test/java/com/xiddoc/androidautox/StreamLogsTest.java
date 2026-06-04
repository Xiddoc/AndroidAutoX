package com.xiddoc.androidautox;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pure JUnit tests for {@link StreamLogs} — no Android runtime needed.
 *
 * Covers:
 *  - null streams return empty string (not NPE)
 *  - trimming of leading/trailing whitespace
 *  - WithLabel formatting for each individual stream
 *  - getStreamLogsWithLabels: always includes output, conditionally adds
 *    input/error only when non-empty
 */
public class StreamLogsTest {

    // -----------------------------------------------------------------------
    // getOutputStreamLog
    // -----------------------------------------------------------------------

    @Test
    public void outputStream_null_returnsEmpty() {
        StreamLogs sl = new StreamLogs();
        // outputStreamLog is not set → null
        assertEquals("", sl.getOutputStreamLog());
    }

    @Test
    public void outputStream_trimsWhitespace() {
        StreamLogs sl = new StreamLogs();
        sl.setOutputStreamLog("  hello world  \n");
        assertEquals("hello world", sl.getOutputStreamLog());
    }

    @Test
    public void outputStream_plainText_returnedAsIs() {
        StreamLogs sl = new StreamLogs();
        sl.setOutputStreamLog("line1\nline2");
        assertEquals("line1\nline2", sl.getOutputStreamLog());
    }

    // -----------------------------------------------------------------------
    // getInputStreamLog
    // -----------------------------------------------------------------------

    @Test
    public void inputStream_null_returnsEmpty() {
        StreamLogs sl = new StreamLogs();
        assertEquals("", sl.getInputStreamLog());
    }

    @Test
    public void inputStream_trimsWhitespace() {
        StreamLogs sl = new StreamLogs();
        sl.setInputStreamLog("\t  input data  \t");
        assertEquals("input data", sl.getInputStreamLog());
    }

    // -----------------------------------------------------------------------
    // getErrorStreamLog
    // -----------------------------------------------------------------------

    @Test
    public void errorStream_null_returnsEmpty() {
        StreamLogs sl = new StreamLogs();
        assertEquals("", sl.getErrorStreamLog());
    }

    @Test
    public void errorStream_trimsWhitespace() {
        StreamLogs sl = new StreamLogs();
        sl.setErrorStreamLog("  error text  ");
        assertEquals("error text", sl.getErrorStreamLog());
    }

    // -----------------------------------------------------------------------
    // getOutputStreamLogWithLabel
    // -----------------------------------------------------------------------

    @Test
    public void outputWithLabel_singleLine_hasHeader() {
        StreamLogs sl = new StreamLogs();
        sl.setOutputStreamLog("ok");
        String result = sl.getOutputStreamLogWithLabel();
        assertTrue(result.startsWith("\tOutputStream:\n\t\t"));
        assertTrue(result.endsWith("ok"));
    }

    @Test
    public void outputWithLabel_multiline_indentsEachLine() {
        StreamLogs sl = new StreamLogs();
        sl.setOutputStreamLog("line1\nline2\nline3");
        String result = sl.getOutputStreamLogWithLabel();
        assertEquals("\tOutputStream:\n\t\tline1\n\t\tline2\n\t\tline3", result);
    }

    @Test
    public void outputWithLabel_nullStream_hasHeaderAndEmptyBody() {
        StreamLogs sl = new StreamLogs();
        // null → getOutputStreamLog() returns "" → replaceAll on "" → ""
        String result = sl.getOutputStreamLogWithLabel();
        assertEquals("\tOutputStream:\n\t\t", result);
    }

    // -----------------------------------------------------------------------
    // getInputStreamLogWithLabel
    // -----------------------------------------------------------------------

    @Test
    public void inputWithLabel_singleLine_hasHeader() {
        StreamLogs sl = new StreamLogs();
        sl.setInputStreamLog("stdin content");
        String result = sl.getInputStreamLogWithLabel();
        assertTrue(result.startsWith("\tInputStream:\n\t\t"));
        assertTrue(result.endsWith("stdin content"));
    }

    @Test
    public void inputWithLabel_multiline_indentsEachLine() {
        StreamLogs sl = new StreamLogs();
        sl.setInputStreamLog("a\nb");
        String result = sl.getInputStreamLogWithLabel();
        assertEquals("\tInputStream:\n\t\ta\n\t\tb", result);
    }

    // -----------------------------------------------------------------------
    // getErrorStreamLogWithLabel
    // -----------------------------------------------------------------------

    @Test
    public void errorWithLabel_singleLine_hasHeader() {
        StreamLogs sl = new StreamLogs();
        sl.setErrorStreamLog("some error");
        String result = sl.getErrorStreamLogWithLabel();
        assertTrue(result.startsWith("\tErrorStream:\n\t\t"));
        assertTrue(result.endsWith("some error"));
    }

    @Test
    public void errorWithLabel_multiline_indentsEachLine() {
        StreamLogs sl = new StreamLogs();
        sl.setErrorStreamLog("err1\nerr2");
        String result = sl.getErrorStreamLogWithLabel();
        assertEquals("\tErrorStream:\n\t\terr1\n\t\terr2", result);
    }

    // -----------------------------------------------------------------------
    // getStreamLogsWithLabels — the combined view
    // -----------------------------------------------------------------------

    @Test
    public void streamLogsWithLabels_onlyOutput_noInputNoError() {
        StreamLogs sl = new StreamLogs();
        sl.setOutputStreamLog("done");
        String result = sl.getStreamLogsWithLabels();

        // Must start with newline + output section
        assertTrue(result.startsWith("\n\tOutputStream:\n\t\tdone"));
        // Must NOT contain Input or Error sections
        assertFalse(result.contains("InputStream"));
        assertFalse(result.contains("ErrorStream"));
    }

    @Test
    public void streamLogsWithLabels_outputAndInput_noError() {
        StreamLogs sl = new StreamLogs();
        sl.setOutputStreamLog("out");
        sl.setInputStreamLog("in");
        String result = sl.getStreamLogsWithLabels();

        assertTrue(result.contains("OutputStream"));
        assertTrue(result.contains("InputStream"));
        assertFalse(result.contains("ErrorStream"));
    }

    @Test
    public void streamLogsWithLabels_outputAndError_noInput() {
        StreamLogs sl = new StreamLogs();
        sl.setOutputStreamLog("out");
        sl.setErrorStreamLog("err");
        String result = sl.getStreamLogsWithLabels();

        assertTrue(result.contains("OutputStream"));
        assertFalse(result.contains("InputStream"));
        assertTrue(result.contains("ErrorStream"));
    }

    @Test
    public void streamLogsWithLabels_allStreams_includesAll() {
        StreamLogs sl = new StreamLogs();
        sl.setOutputStreamLog("out");
        sl.setInputStreamLog("in");
        sl.setErrorStreamLog("err");
        String result = sl.getStreamLogsWithLabels();

        assertTrue(result.contains("OutputStream"));
        assertTrue(result.contains("InputStream"));
        assertTrue(result.contains("ErrorStream"));
    }

    @Test
    public void streamLogsWithLabels_emptyInput_notIncluded() {
        StreamLogs sl = new StreamLogs();
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
        StreamLogs sl = new StreamLogs();
        String result = sl.getStreamLogsWithLabels();

        assertTrue(result.contains("OutputStream"));
        assertFalse(result.contains("InputStream"));
        assertFalse(result.contains("ErrorStream"));
    }

    @Test
    public void streamLogsWithLabels_orderIsOutputThenInputThenError() {
        StreamLogs sl = new StreamLogs();
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
}
