package dev.monkeypatch.rctiming.persistence.vendor;

import org.jooq.SQLDialect;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

/**
 * The databases the app can run on. Everything that differs between them is listed here and
 * applied by {@code DatabaseConfig}, so moving to another database means a new constant, a
 * migrations folder and a config change, not edits across the codebase.
 */
public enum DatabaseVendor {

    /**
     * One SQLite file inside the app process (#26). WAL lets readers carry on while a write
     * commits; a single writer connection means writes never contend for the lock.
     */
    SQLITE("sqlite", SQLDialect.SQLITE, 1) {
        @Override
        public String jdbcUrl(Path dataDirectory) {
            return "jdbc:sqlite:" + dataDirectory.resolve(DATABASE_FILE);
        }

        @Override
        public Properties connectionProperties() {
            SQLiteConfig config = new SQLiteConfig();
            config.setJournalMode(SQLiteConfig.JournalMode.WAL);
            config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
            config.enforceForeignKeys(true);
            config.setBusyTimeout(5000);
            return config.toProperties();
        }

        @Override
        public boolean hasDatabase(Path dataDirectory) {
            return Files.isRegularFile(dataDirectory.resolve(DATABASE_FILE));
        }

        @Override
        public String readOnlySessionSql() {
            return "PRAGMA query_only = 1";
        }

        /**
         * A failed unique or primary key constraint becomes Spring's {@link DuplicateKeyException}, and any
         * other failed constraint (foreign key, check, not-null) its parent {@link DataIntegrityViolationException},
         * so the API can tell "already exists" from "still in use".
         */
        @Override
        public SQLExceptionTranslator exceptionTranslator() {
            return (task, sql, ex) -> {
                if ((ex.getErrorCode() & 0xFF) != SQLITE_CONSTRAINT) {
                    return null;
                }
                String message = task + "; " + ex.getMessage();
                boolean duplicate = ex instanceof SQLiteException sqlite
                        && (sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE
                        || sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY);
                return duplicate
                        ? new DuplicateKeyException(message, ex)
                        : new DataIntegrityViolationException(message, ex);
            };
        }

        /**
         * {@code VACUUM INTO} writes a compact, consistent copy from one read transaction, so
         * laps keep committing while it runs (#22). It needs a connection of its own: the read
         * pool's connections are query-only, which SQLite treats as forbidding it.
         */
        @Override
        public void backup(Path dataDirectory, Path target) throws SQLException {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(dataDirectory), connectionProperties());
                 PreparedStatement vacuum = connection.prepareStatement("VACUUM INTO ?")) {
                vacuum.setString(1, target.toString());
                vacuum.execute();
            }
        }

        /**
         * Opens the file read-only and runs {@code PRAGMA integrity_check}, then looks for Flyway's history
         * table, so a damaged file or some other SQLite database is refused before a restore replaces anything.
         */
        @Override
        public void checkBackup(Path backup) throws SQLException {
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backup, config.toProperties());
                 Statement statement = connection.createStatement()) {
                try (ResultSet integrity = statement.executeQuery("PRAGMA integrity_check")) {
                    String result = integrity.next() ? integrity.getString(1) : "no result";
                    if (!"ok".equals(result)) {
                        throw new SQLException("Backup " + backup + " is damaged: " + result);
                    }
                }
                try (ResultSet history = statement.executeQuery(
                        "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'flyway_schema_history'")) {
                    if (!history.next() || history.getInt(1) == 0) {
                        throw new SQLException(backup + " is not an RCTimingControl database");
                    }
                }
            }
        }

        /**
         * Checks the backup, refuses if another process holds the database, moves the database and its
         * {@code -wal} and {@code -shm} files aside with a {@code .before-restore-} timestamp suffix, then copies the
         * backup in under a temporary name and renames it into place atomically so a crash never leaves half a file.
         */
        @Override
        public void restore(Path backup, Path dataDirectory) throws SQLException, IOException {
            checkBackup(backup);
            Path database = dataDirectory.resolve(DATABASE_FILE);
            if (Files.exists(database)) {
                ensureNotInUse(database);
            }
            Files.createDirectories(dataDirectory);
            String suffix = ".before-restore-" + System.currentTimeMillis();
            for (Path file : List.of(database, sibling(database, "-wal"), sibling(database, "-shm"))) {
                if (Files.exists(file)) {
                    Files.move(file, sibling(file, suffix));
                }
            }
            Path partial = sibling(database, ".restoring");
            Files.copy(backup, partial, StandardCopyOption.REPLACE_EXISTING);
            giveToFolderOwner(partial, dataDirectory);
            Files.move(partial, database, StandardCopyOption.ATOMIC_MOVE);
        }

        /**
         * The installed Linux service runs as its own user (#23) but a restore is run with sudo, so
         * the copy would belong to root and the service could not write to it. It gets the data
         * folder's owner and group instead, as the files the service made itself have.
         */
        private void giveToFolderOwner(Path file, Path dataDirectory) throws IOException {
            PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (view == null) {
                return;
            }
            PosixFileAttributes folder = Files.readAttributes(dataDirectory, PosixFileAttributes.class);
            PosixFileAttributes current = view.readAttributes();
            if (!current.owner().equals(folder.owner())) {
                view.setOwner(folder.owner());
            }
            if (!current.group().equals(folder.group())) {
                view.setGroup(folder.group());
            }
        }

        /** The running app keeps the file open, so an exclusive lock can only be had when it is stopped. */
        private void ensureNotInUse(Path database) throws SQLException {
            Properties settings = new Properties();
            settings.setProperty("busy_timeout", "0");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database, settings);
                 Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA locking_mode = EXCLUSIVE");
                statement.execute("BEGIN EXCLUSIVE");
                statement.execute("ROLLBACK");
            } catch (SQLException e) {
                throw new SQLException("The database " + database + " is in use. Stop the app before restoring.", e);
            }
        }

        private static Path sibling(Path file, String suffix) {
            return file.resolveSibling(file.getFileName() + suffix);
        }
    };

    private static final String DATABASE_FILE = "rctiming.db";

    /** SQLite's primary result code for any failed constraint. */
    private static final int SQLITE_CONSTRAINT = 19;

    private final String migrationFolder;
    private final SQLDialect jooqDialect;
    private final int maxWriteConnections;

    DatabaseVendor(String migrationFolder, SQLDialect jooqDialect, int maxWriteConnections) {
        this.migrationFolder = migrationFolder;
        this.jooqDialect = jooqDialect;
        this.maxWriteConnections = maxWriteConnections;
    }

    /** JDBC URL of the app's database inside the data directory. */
    public abstract String jdbcUrl(Path dataDirectory);

    /** Settings applied to every connection. */
    public abstract Properties connectionProperties();

    /** True when the data directory already holds a database, so connecting won't create an empty one. */
    public abstract boolean hasDatabase(Path dataDirectory);

    /** Statement that makes a connection refuse writes; run on each read-pool connection. */
    public abstract String readOnlySessionSql();

    /** Writes a consistent copy of the live database to {@code target}, while the app keeps running. */
    public abstract void backup(Path dataDirectory, Path target) throws SQLException;

    /** Fails unless {@code backup} is an undamaged copy of the app's database. */
    public abstract void checkBackup(Path backup) throws SQLException;

    /**
     * Puts {@code backup} in place as the database in {@code dataDirectory}, keeping the files it
     * replaces beside it. Refuses while the app has the database open.
     */
    public abstract void restore(Path backup, Path dataDirectory) throws SQLException, IOException;

    /**
     * Turns this database's errors into Spring's exceptions for jOOQ, or returns null to leave
     * one to Spring's own translation.
     */
    public abstract SQLExceptionTranslator exceptionTranslator();

    /** Sub-folder holding this vendor's Flyway scripts under each migration location. */
    public String migrationFolder() {
        return migrationFolder;
    }

    /** The jOOQ dialect {@code DatabaseConfig} sets on the shared jOOQ configuration. */
    public SQLDialect jooqDialect() {
        return jooqDialect;
    }

    /** Size of the pool that writes and transactions use. */
    public int maxWriteConnections() {
        return maxWriteConnections;
    }
}
