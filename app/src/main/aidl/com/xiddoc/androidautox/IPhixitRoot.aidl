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

    // Returns {st_uid, st_gid} for path via Os.stat (replaces parsing `stat -c %U:%G`).
    // Numeric ids are more robust than name parsing and round-trip cleanly to chownPath.
    int[] statOwner(String path);

    // Os.chown(path, uid, gid) -- restores ownership after a root-process DB edit
    // (replaces shelling out to `chown`).
    void chownPath(String path, int uid, int gid);

    // Recursively deletes the file/dir at path (replaces `rm -rf`). Used to clear the
    // phenotype cache dir. The implementation never follows symlinks (a root delete that
    // descended a symlink would reach the link's target outside the tree) and guards against
    // empty/root/traversal paths and anything outside the GMS data-dir allowlist.
    boolean deleteRecursive(String path);

    // Consistent, restorable backup of a (possibly live) SQLite DB inside the root process,
    // so a GMS-private DB can be copied to an app-controlled backup location. Checkpoints the
    // source WAL into the main file, then copies the main file AND any -wal/-shm/-journal
    // sidecars; each file is written to a ".part" temp, fsynced, then atomically renamed so a
    // torn file never appears under the final name. When uid >= 0 the written files are
    // chown'd to uid/gid (the app uid) so the app can prune/restore them. Parent dirs are
    // created if missing.
    void backupFile(String srcPath, String destPath, int uid, int gid);
}
