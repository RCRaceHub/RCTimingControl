package dev.monkeypatch.rctiming.retention;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RCTC is the permanent record of everything it timed (#472): the nightly clean-up must never remove a race, a lap,
 * a result or a practice session. If someone adds a table of race history, list it in {@link #HISTORY_TABLES}.
 */
class RaceHistoryKeptIT extends AbstractIntegrationTest {

    /** Tables that hold what happened on the track; laps live inside {@code result_snapshots} and {@code practice_laps}. */
    static final List<String> HISTORY_TABLES = List.of("events", "rounds", "races", "race_entries", "result_snapshots",
            "practice_sessions", "practice_drivers", "practice_laps", "marshal_adjustments", "penalties",
            "race_point_penalties", "championships");

    @Autowired RetentionService retention;
    @Autowired JdbcTemplate jdbc;

    private Map<String, Integer> counts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : HISTORY_TABLES) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Integer.class));
        }
        return counts;
    }

    @Test
    void theNightlyCleanUpRemovesNoRaceLapOrResultRows() {
        Map<String, Integer> before = counts();

        retention.purge();

        assertThat(counts()).isEqualTo(before);
    }
}
