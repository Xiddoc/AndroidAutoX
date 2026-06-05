package com.xiddoc.androidautox;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure string helpers extracted from {@link PhixitRootService} (the root SQLite
 * bridge, which itself stays excluded from the coverage gate because it is a
 * framework {@code RootService} shell).
 *
 * <p>Covers the two pieces of non-trivial text logic the service inlined:
 * <ul>
 *   <li>{@link #formatRow(String[])} / row joining — how a query result row's
 *       columns are joined ({@code '|'} between columns) and how rows are joined
 *       ({@code '\n'} between rows, no trailing newline), with {@code null}
 *       columns rendered as empty.</li>
 *   <li>{@link #normalizeStatement(String)} — the lenient, {@code sqlite3 -batch}
 *       style normalisation applied before {@code execSQL}: trim, strip a single
 *       outermost trailing {@code ';'} (inner {@code ';'} in a trigger body are
 *       kept), and treat {@code null}/blank statements as skippable.</li>
 * </ul>
 *
 * The service delegates to these so the row/column and statement text rules are
 * unit-testable without a real {@code SQLiteDatabase} / root process.
 */
public final class RootSqlText {

    private RootSqlText() {
    }

    /**
     * Join one result row's column values with {@code '|'}, rendering {@code null}
     * columns as empty string. Mirrors the per-row inner loop in
     * {@code PhixitRootService.Impl.query}.
     */
    public static String formatRow(String[] columns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sb.append('|');
            String v = columns[i];
            if (v != null) sb.append(v);
        }
        return sb.toString();
    }

    /**
     * Join already-formatted rows with {@code '\n'} (no leading/trailing newline),
     * mirroring how {@code query} accumulates rows into its returned string.
     */
    public static String joinRows(List<String> rows) {
        StringBuilder sb = new StringBuilder();
        boolean firstRow = true;
        for (String row : rows) {
            if (!firstRow) sb.append('\n');
            firstRow = false;
            sb.append(row);
        }
        return sb.toString();
    }

    /**
     * Normalise a single statement the way {@code execStatements} did before handing
     * it to {@code execSQL}:
     * <ul>
     *   <li>{@code null} → {@code null} (caller skips it),</li>
     *   <li>trim surrounding whitespace,</li>
     *   <li>strip a single trailing {@code ';'} (the outermost terminator) and trim
     *       again — inner {@code ';'} inside a trigger body survive,</li>
     *   <li>an empty/blank statement → {@code ""} (caller skips it).</li>
     * </ul>
     *
     * @return the SQL to execute, or {@code null}/{@code ""} when there is nothing
     *         to run (matching the {@code continue} skips in the service loop).
     */
    static String normalizeStatement(String statement) {
        if (statement == null) return null;
        String t = statement.trim();
        if (t.endsWith(";")) t = t.substring(0, t.length() - 1).trim();
        if (t.isEmpty()) return "";
        return t;
    }

    /**
     * Normalise a whole batch, dropping anything that resolves to nothing to run
     * (null or blank), preserving order. Convenience over repeatedly calling
     * {@link #normalizeStatement(String)}.
     */
    public static List<String> normalizeBatch(List<String> statements) {
        List<String> out = new ArrayList<String>();
        for (String s : statements) {
            String n = normalizeStatement(s);
            if (n != null && !n.isEmpty()) out.add(n);
        }
        return out;
    }
}
