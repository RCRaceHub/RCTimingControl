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
 * V21 drops schema nothing uses (#141), rebuilding {@code races} and {@code entries}, which every other test only ever
 * sees empty. Here a club database at V20 holding a race and an entry, with rows that cascade from them, is upgraded on
 * one connection set up as the app's write pool sets it up. If the script ever ran inside Flyway's transaction, foreign
 * keys would stay on and dropping the tables would delete those rows.
 */
class DropUnusedColumnsMigrationTest {

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
    void upgradingKeepsEveryRaceAndEntryAndWhatHangsOffThemAndDropsTheUnusedColumns() {
        DatabaseVendor vendor = DatabaseVendor.SQLITE;
        dataSource = new SingleConnectionDataSource(vendor.jdbcUrl(dataDirectory), true);
        dataSource.setConnectionProperties(vendor.connectionProperties());
        jdbc = new JdbcTemplate(dataSource);

        migrate("20");
        // Rows a club would have, without building their parents: only the rebuilds are under test
        jdbc.execute("PRAGMA foreign_keys = OFF");
        jdbc.update("""
                insert into races (id, round_id, event_class_id, heat_number, sequence_in_round, start_type, status,
                                   format_id, format_overrides, abandoned_at, bump_slots)
                values (7, 1, 1, 1, 1, 'GRID', 'FINISHED', 3, '{}', 123, 2)""");
        jdbc.update("""
                insert into entries (id, user_id, event_id, event_class_id, competitor_id, transponder_number,
                                     transponder_label, status, external_source, external_entry_id,
                                     racehub_event_class_id, imported_transponder_number, checked_in_by_user_id)
                values (5, 1, 1, 1, 4, '101', 'Buggy', 'CONFIRMED', 'RACEHUB', 'rh-1', 'rh-class', '202', 1)""");
        // A later entry that was deleted: its id must not be handed out again
        jdbc.update("""
                insert into entries (id, event_id, competitor_id, transponder_number, status)
                values (8, 1, 6, '102', 'PENDING')""");
        jdbc.update("delete from entries where id = 8");
        jdbc.update("insert into race_entries (race_id, entry_id) values (7, 5)");
        jdbc.update("""
                insert into entry_audit_log (entry_id, admin_user_id, action, created_at)
                values (5, 1, 'ADMIN_CREATE', 1)""");
        jdbc.update("""
                insert into result_snapshots (race_id, finished_at, positions_json, lap_history_json)
                values (7, 456, '[]', '[]')""");
        jdbc.update("""
                insert into unknown_transponder_link (race_id, transponder_number, entry_id, linked_by_user_id)
                values (7, '999', 5, 1)""");
        jdbc.update("""
                insert into events (id, name, event_date, status, entry_opens_at, entry_closes_at)
                values (1, 'Round 1', '2026-10-05', 'OPEN', 1, 2)""");
        jdbc.update("""
                insert into club_profiles (id, name, logo, logo_type, logo_url)
                values (1, 'Club', x'01', 'png', '/l.png')""");
        jdbc.execute("PRAGMA foreign_keys = ON");

        migrate(null);

        assertThat(count("select count(*) from race_entries where race_id = 7 and entry_id = 5")).isEqualTo(1);
        assertThat(count("select count(*) from result_snapshots where race_id = 7")).isEqualTo(1);
        assertThat(count("select count(*) from entry_audit_log where entry_id = 5")).isEqualTo(1);
        assertThat(count("select count(*) from unknown_transponder_link where race_id = 7 and entry_id = 5"))
                .isEqualTo(1);
        assertThat(jdbc.queryForMap("select start_type, abandoned_at, bump_slots from races where id = 7"))
                .containsEntry("start_type", "GRID").containsEntry("abandoned_at", 123).containsEntry("bump_slots", 2);
        assertThat(jdbc.queryForMap("""
                select competitor_id, transponder_number, external_entry_id, racehub_event_class_id,
                       imported_transponder_number, checked_in_by_user_id
                from entries where id = 5"""))
                .containsEntry("competitor_id", 4).containsEntry("transponder_number", "101")
                .containsEntry("external_entry_id", "rh-1").containsEntry("racehub_event_class_id", "rh-class")
                .containsEntry("imported_transponder_number", "202").containsEntry("checked_in_by_user_id", 1);
        assertThat(jdbc.queryForObject("select name from events where id = 1", String.class)).isEqualTo("Round 1");
        assertThat(jdbc.queryForObject("select logo_url from club_profiles where id = 1", String.class))
                .isEqualTo("/l.png");
        assertThat(jdbc.queryForObject("select seq from sqlite_sequence where name = 'entries'", Integer.class))
                .isEqualTo(8);

        assertThat(columns("races", "format_id", "format_overrides")).isZero();
        assertThat(columns("entries", "user_id", "transponder_label")).isZero();
        assertThat(columns("events", "entry_opens_at", "entry_closes_at")).isZero();
        assertThat(columns("club_profiles", "logo", "logo_type")).isZero();
        assertThat(count("select count(*) from sqlite_master where name = 'unknown_transponder_links'")).isZero();
        assertThat(count("select count(*) from sqlite_master where name = 'idx_entries_no_duplicate'")).isEqualTo(1);
        assertThat(count("PRAGMA foreign_keys")).isEqualTo(1);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private int columns(String table, String... names) {
        return jdbc.queryForObject("select count(*) from pragma_table_info(?) where name in (?, ?)", Integer.class,
                table, names[0], names[1]);
    }

    private void migrate(String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/sqlite");
        if (target != null) {
            config.target(target);
        }
        config.load().migrate();
    }
}
