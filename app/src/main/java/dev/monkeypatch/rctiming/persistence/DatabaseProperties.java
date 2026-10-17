package dev.monkeypatch.rctiming.persistence;

import dev.monkeypatch.rctiming.persistence.vendor.DatabaseVendor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code rctiming.database.*}: which database the app runs on and where it keeps it.
 *
 * @param vendor             the database; picks the dialects and the migrations folder
 * @param dataDirectory      folder holding the database file; defaults to the per-user app-data
 *                           folder (see {@link DataDirectories})
 * @param migrationLocations classpath folders holding Flyway scripts, each with one sub-folder per
 *                           vendor (so {@code db/migration} reads {@code db/migration/sqlite})
 * @param readConnections    size of the pool that read queries use outside a transaction
 */
@ConfigurationProperties(prefix = "rctiming.database")
public record DatabaseProperties(
        @DefaultValue("sqlite") DatabaseVendor vendor,
        Path dataDirectory,
        @DefaultValue("db/migration") List<String> migrationLocations,
        @DefaultValue("4") int readConnections) {

    /** The configured data folder, or the per-user or per-machine default when none is set. */
    public Path effectiveDataDirectory() {
        return dataDirectory != null ? dataDirectory : DataDirectories.defaultDirectory();
    }
}
