// Root-side SQLite bridge. The implementation runs inside a libsu RootService
// (a root process) so it can open GMS's private phenotype.db directly with the
// platform android.database.sqlite API -- no bundled sqlite3 binary, no SQL
// piped through a shell, and binary blobs are passed as real byte[] (no hex).
package com.xiddoc.androidautox;

import com.xiddoc.androidautox.Partition;

interface IPhixitRoot {
    // Reads {id, flags_content} for every param_partition belonging to a Phenotype
    // config package (e.g. com.google.android.projection.gearhead).
    List<Partition> readPartitions(String pkg);

    // Writes each partition's blob back in a single transaction. When
    // servingVersion >= 0, last_fetch.serving_version (type=1) is bumped too so
    // GMS treats the snapshot as fresh.
    void writePartitions(in List<Partition> parts, int servingVersion);

    // CLI-compatible read for arbitrary SQL: rows joined by '\n', columns by '|'
    // (matches the old `sqlite3 -batch` output the callers parse). hex() etc. work.
    String query(String dbPath, String sql);

    // Executes each complete statement in one transaction (DROP/CREATE/INSERT/...).
    // Each element must be a single statement; a CREATE TRIGGER ... BEGIN ... END
    // counts as one element.
    void execStatements(String dbPath, in List<String> statements);
}
