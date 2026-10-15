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
 * V26 drops the check-in columns by rebuilding {@code entries}, which every other test only ever sees empty. Here a
 * club database at V25 holding an entry that was checked in, with a race entry hanging off it, is upgraded on one
 * connection set up as the app's write pool sets it up. If the script ever ran inside Flyway's transaction, foreign
 * keys would stay on and dropping the table would delete the race entry.
 */
class DropCheckInMigrationTest {

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
    void upgradingKeepsEveryEntryAndWhatHangsOffItAndDropsTheCheckInColumns() {
        DatabaseVendor vendor = DatabaseVendor.SQLITE;
        dataSource = new SingleConnectionDataSource(vendor.jdbcUrl(dataDirectory), true);
        dataSource.setConnectionProperties(vendor.connectionProperties());
        jdbc = new JdbcTemplate(dataSource);

        migrate("25");
        // Rows a club would have, without building their parents: only the rebuild is under test
        jdbc.execute("PRAGMA foreign_keys = OFF");
        jdbc.update("""
                insert into entries (id, event_id, event_class_id, competitor_id, transponder_number,
                                     secondary_transponder_number, status, external_source, external_entry_id,
                                     racehub_arrival, racehub_event_class_id, imported_transponder_number,
                                     checked_in_at, checked_in_by_user_id)
                values (5, 1, 1, 4, '101', '102', 'CONFIRMED', 'RACEHUB', 'rh-1', 'ARRIVED', 'rh-class', '202',
                        123, 1)""");
        // A later entry that was deleted: its id must not be handed out again
        jdbc.update("""
                insert into entries (id, event_id, competitor_id, transponder_number, status)
                values (8, 1, 6, '103', 'PENDING')""");
        jdbc.update("delete from entries where id = 8");
        jdbc.update("insert into race_entries (race_id, entry_id) values (7, 5)");
        jdbc.update("""
                insert into entry_audit_log (entry_id, admin_user_id, action, created_at)
                values (5, 1, 'ADMIN_CREATE', 1)""");
        jdbc.execute("PRAGMA foreign_keys = ON");

        migrate(null);

        assertThat(count("select count(*) from race_entries where race_id = 7 and entry_id = 5")).isEqualTo(1);
        assertThat(count("select count(*) from entry_audit_log where entry_id = 5")).isEqualTo(1);
        assertThat(jdbc.queryForMap("""
                select competitor_id, transponder_number, secondary_transponder_number, external_entry_id,
                       racehub_arrival, racehub_event_class_id, imported_transponder_number
                from entries where id = 5"""))
                .containsEntry("competitor_id", 4).containsEntry("transponder_number", "101")
                .containsEntry("secondary_transponder_number", "102").containsEntry("external_entry_id", "rh-1")
                .containsEntry("racehub_arrival", "ARRIVED").containsEntry("racehub_event_class_id", "rh-class")
                .containsEntry("imported_transponder_number", "202");
        assertThat(jdbc.queryForObject("select seq from sqlite_sequence where name = 'entries'", Integer.class))
                .isEqualTo(8);
        assertThat(count("select count(*) from pragma_table_info('entries') "
                + "where name in ('checked_in_at', 'checked_in_by_user_id')")).isZero();
        assertThat(count("select count(*) from sqlite_master where name = 'idx_entries_no_duplicate'")).isEqualTo(1);
        assertThat(count("PRAGMA foreign_keys")).isEqualTo(1);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private void migrate(String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/sqlite");
        if (target != null) {
            config.target(target);
        }
        config.load().migrate();
    }
}
