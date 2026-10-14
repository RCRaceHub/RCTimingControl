package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** L4 acceptance: an entry can belong to a competitor with no login user. */
class CompetitorEntryIT extends AbstractIntegrationTest {

    private Long eventId;

    @Autowired
    CompetitorRepository competitorRepository;

    @Autowired
    EntryRepository entryRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    RacingClassRepository racingClassRepository;

    @Autowired
    EventClassRepository eventClassRepository;

    @Test
    void entryWithCompetitorAndNoUser_persistsAndReloads() {
        Long eventClassId = seedEventClass();
        Competitor competitor = saveCompetitor("walk-in-" + unique());

        Entry entry = newEntry(competitor, eventClassId, "101");
        Long entryId = entryRepository.save(entry).getId();

        Entry reloaded = entryRepository.findById(entryId).orElseThrow();
        assertThat(reloaded.getCompetitorId()).isEqualTo(competitor.getId());
    }

    @Test
    void sameCompetitorTwiceInOneClass_isRejected() {
        Long eventClassId = seedEventClass();
        Competitor competitor = saveCompetitor("dup-" + unique());
        entryRepository.save(newEntry(competitor, eventClassId, "201"));

        assertThatThrownBy(() -> entryRepository.save(newEntry(competitor, eventClassId, "201")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void anEntryForAMissingClass_isAnIntegrityViolationButNotADuplicate() {
        seedEventClass();
        Competitor competitor = saveCompetitor("orphan-" + unique());

        assertThatThrownBy(() -> entryRepository.save(newEntry(competitor, Long.MAX_VALUE, "301")))
                .isExactlyInstanceOf(DataIntegrityViolationException.class);
    }

    private Competitor saveCompetitor(String name) {
        Competitor competitor = new Competitor();
        competitor.setDisplayName(name);
        competitor.setCreatedAt(Instant.now());
        competitor.setUpdatedAt(Instant.now());
        return competitorRepository.save(competitor);
    }

    private Entry newEntry(Competitor competitor, Long eventClassId, String transponder) {
        Entry entry = new Entry();
        entry.setCompetitorId(competitor.getId());
        entry.setEventId(eventId);
        entry.setEventClassId(eventClassId);
        entry.setTransponderNumberSnapshot(transponder);
        entry.setStatus(EntryStatus.CONFIRMED);
        entry.setSubmittedAt(Instant.now());
        entry.setUpdatedAt(Instant.now());
        return entry;
    }

    /** Creates an event with one class and returns the event class id. */
    private Long seedEventClass() {
        Instant now = Instant.now();
        Event event = new Event();
        event.setName("Competitor Test " + unique());
        event.setEventDate(LocalDate.of(2026, 6, 1));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        eventId = eventRepository.save(event).getId();

        RacingClass racingClass = new RacingClass();
        racingClass.setName("TestClass-" + unique());
        racingClass.setCreatedAt(now);
        racingClass.setUpdatedAt(now);
        racingClass = racingClassRepository.save(racingClass);

        EventClass eventClass = new EventClass();
        eventClass.setEventId(eventId);
        eventClass.setRacingClassId(racingClass.getId());
        eventClass.setConfigSnapshot(new TimedRaceConfig(5, StartType.GRID, QualifyingType.FASTEST_LAP, 1, 0));
        return eventClassRepository.save(eventClass).getId();
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
