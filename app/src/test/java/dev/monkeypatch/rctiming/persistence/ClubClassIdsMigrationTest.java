package dev.monkeypatch.rctiming.persistence;

import dev.monkeypatch.rctiming.persistence.vendor.DatabaseVendor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V59 moves RaceHub from per-meeting class ids to club class ids. A per-meeting id cannot be turned into a club
 * class id, so what was keyed by one is cleared, and what the CSV import keys by class name is kept.
 */
class ClubClassIdsMigrationTest {

    @TempDir Path dataDirectory;

    private SingleConnectionDataSource dataSource;

    private JdbcTemplate jdbc;

    @AfterEach
    void close() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void upgradingClearsPerMeetingRaceHubClassDataAndKeepsTheCsvMappings() {
        DatabaseVendor vendor = DatabaseVendor.SQLITE;
        dataSource = new SingleConnectionDataSource(vendor.jdbcUrl(dataDirectory), true);
        dataSource.setConnectionProperties(vendor.connectionProperties());
        jdbc = new JdbcTemplate(dataSource);

        migrate("58");
        jdbc.execute("PRAGMA foreign_keys = OFF");
        jdbc.update("""
                insert into entries (id, event_id, event_class_id, competitor_id, transponder_number, status,
                                     external_source, external_entry_id, racehub_event_class_id)
                values (5, 1, 1, 4, '101', 'CONFIRMED', 'RACEHUB', 'rh-1', 'per-meeting-12')""");
        jdbc.update("insert into racehub_class_links (class_key, racing_class_id) values ('12', 1)");
        jdbc.update("insert into racehub_class_links (class_key, racing_class_id) values ('OTHER:7', 1)");
        jdbc.update("""
                insert into racehub_class_mappings (event_id, racehub_event_class_id, event_class_id)
                values (1, '12', 1)""");
        jdbc.update("""
                insert into racehub_class_mappings (event_id, racehub_event_class_id, event_class_id)
                values (1, 'CSV:2WD Buggy', 1)""");
        jdbc.execute("PRAGMA foreign_keys = ON");

        migrate("59");

        assertThat(jdbc.queryForMap("select external_entry_id, racehub_club_class_id from entries where id = 5"))
                .containsEntry("external_entry_id", "rh-1").containsEntry("racehub_club_class_id", null);
        assertThat(jdbc.queryForObject("""
                select count(*) from pragma_table_info('entries') where name = 'racehub_event_class_id'""",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from racehub_class_links", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select racehub_event_class_id from racehub_class_mappings", String.class))
                .containsExactly("CSV:2WD Buggy");
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/sqlite").target(target)
                .load().migrate();
    }
}
