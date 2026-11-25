package dev.monkeypatch.rctiming.modules;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleSwitchRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ModuleSwitchRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesAndUpdatesAClubSwitch() {
        repository.saveSwitch("it-switch", false, null, Instant.parse("2026-11-25T09:00:00Z"));
        assertThat(repository.findAllSwitches()).containsEntry("it-switch", false);

        repository.saveSwitch("it-switch", true, null, Instant.parse("2026-11-25T10:00:00Z"));
        assertThat(repository.findAllSwitches()).containsEntry("it-switch", true);
    }

    @Test
    void anEventsOffSwitchIsAddedOnceRemovedAndFollowsTheEventWhenDeleted() {
        long eventId = jdbc.queryForObject("select id from events order by id limit 1", Long.class);

        repository.addEventOff(eventId, "it-event");
        repository.addEventOff(eventId, "it-event");
        assertThat(repository.findEventOffs(eventId)).containsExactly("it-event");

        repository.removeEventOff(eventId, "it-event");
        assertThat(repository.findEventOffs(eventId)).isEmpty();
    }
}
