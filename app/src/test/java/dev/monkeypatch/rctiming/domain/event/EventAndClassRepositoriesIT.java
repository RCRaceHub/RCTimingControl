package dev.monkeypatch.rctiming.domain.event;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository.EventClassRef;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplate;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplateRepository;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassMapping;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassMappingRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.persistence.RoundTrip.assertSavedAndReloaded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.cast;

/** The class, format and event repositories save and load every field (#71). */
class EventAndClassRepositoriesIT extends AbstractIntegrationTest {

    private static final Instant T1 = Instant.parse("2026-10-05T10:15:30.123456Z");
    private static final Instant T2 = T1.plus(1, ChronoUnit.HOURS);

    @Autowired RacingClassRepository racingClasses;
    @Autowired RaceFormatTemplateRepository templates;
    @Autowired EventClassRepository eventClasses;
    @Autowired EventRepository events;
    @Autowired RaceHubClassMappingRepository mappings;
    @Autowired DSLContext dsl;

    private final List<Runnable> cleanup = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        cleanup.reversed().forEach(Runnable::run);
    }

    @Test
    void racingClassRoundTrip() {
        RacingClass saved = assertSavedAndReloaded(racingClasses, racingClass(), c -> {
            c.setName(c.getName() + " renamed");
            c.setDescription(null);
            c.setCreatedAt(T2);
            c.setUpdatedAt(T2);
            return c;
        }, RacingClass::getId);
        cleanup.add(() -> racingClasses.deleteById(saved.getId()));
        assertThat(racingClasses.findAllById(List.of(saved.getId(), -1L)))
                .extracting(RacingClass::getId).containsExactly(saved.getId());
    }

    @Test
    void raceFormatTemplateRoundTrip() {
        RaceFormatTemplate saved = assertSavedAndReloaded(templates, template(), t -> {
            t.setName("10-minute timed");
            t.setConfig(new TimedRaceConfig(10, StartType.GRID, QualifyingType.FTQ, 3, 4));
            t.setUpdatedAt(T2);
            return t;
        }, RaceFormatTemplate::getId);
        cleanup.add(() -> templates.deleteById(saved.getId()));

        saved.setCreatedAt(T2);
        templates.save(saved);
        assertThat(templates.findById(saved.getId()).orElseThrow().getCreatedAt())
                .as("creation time is set on insert only").isEqualTo(T1);
    }

    @Test
    void eventStoresItsDateAsIsoText() {
        Event e = new Event();
        e.setName("Club round 1");
        e.setEventDate(LocalDate.of(2026, 10, 5));
        e.setStatus(EventStatus.OPEN);
        e.setCreatedAt(T1);
        e.setUpdatedAt(T1);
        e.setRacehubLastImportAt(T1);
        e.setRacehubLastRevision(7L);
        e.setRacehubEventId("rh-event-1");
        e.nextResultsExportRevision();
        e.setResultsExportPending("CORRECTION");
        e.setLiveFeedEnabled(true);

        Event saved = assertSavedAndReloaded(events, e, c -> {
            c.setName("Club round 2");
            c.setEventDate(LocalDate.of(2026, 11, 1));
            c.setStatus(EventStatus.COMPLETED);
            c.setCreatedAt(T2);
            c.setUpdatedAt(T2);
            c.setRacehubLastImportAt(T2);
            c.setRacehubLastRevision(8L);
            c.setRacehubEventId("rh-event-2");
            c.nextResultsExportRevision();
            c.setResultsExportPending(null);
            c.setLiveFeedEnabled(false);
            return c;
        }, Event::getId);
        cleanup.add(() -> events.deleteById(saved.getId()));

        assertThat(saved.getResultsExportRevision()).isEqualTo(2);
        String stored = dsl.select(cast(EVENTS.EVENT_DATE, String.class)).from(EVENTS)
                .where(EVENTS.ID.eq(saved.getId())).fetchSingle().value1();
        assertThat(stored).isEqualTo("2026-11-01");
    }

    @Test
    void eventClassAndRaceHubMapping() {
        Event event = events.save(event());
        cleanup.add(() -> events.deleteById(event.getId()));
        RacingClass racingClass = racingClasses.save(racingClass());
        cleanup.add(() -> racingClasses.deleteById(racingClass.getId()));
        RaceFormatTemplate template = templates.save(template());
        cleanup.add(() -> templates.deleteById(template.getId()));

        EventClass ec = new EventClass();
        ec.setEventId(event.getId());
        ec.setRacingClassId(racingClass.getId());
        ec.setTemplateId(template.getId());
        ec.setConfigSnapshot(template.getConfig());
        ec.setConfigOverride(Map.of("durationMinutes", 6));
        ec.setCombinedRaceGroup(3L);
        ec.setFinalsCount(2);
        ec.setCreatedAt(T1);
        ec.setUpdatedAt(T1);
        EventClass saved = assertSavedAndReloaded(eventClasses, ec, c -> {
            c.setRacingClassId(null);
            c.setTemplateId(null);
            c.setConfigSnapshot(new TimedRaceConfig(8, StartType.GRID, QualifyingType.FTQ, 1, 2));
            c.setConfigOverride(null);
            c.setCombinedRaceGroup(null);
            c.setFinalsCount(null);
            c.setUpdatedAt(T2);
            return c;
        }, EventClass::getId);
        saved.setCreatedAt(T2);
        eventClasses.save(saved);
        assertThat(eventClasses.findById(saved.getId()).orElseThrow().getCreatedAt())
                .as("creation time is set on insert only").isEqualTo(T1);

        assertThat(eventClasses.findByEventId(event.getId())).extracting(EventClass::getId).containsExactly(saved.getId());
        assertThat(eventClasses.findRefsByEventId(event.getId()))
                .containsExactly(new EventClassRef(saved.getId(), null));

        RaceHubClassMapping m = new RaceHubClassMapping();
        m.setEventId(event.getId());
        m.setRacehubEventClassId("rh-class-b");
        m.setEventClassId(saved.getId());
        m.setCreatedAt(T1);
        m.setUpdatedAt(T1);
        assertSavedAndReloaded(mappings, m, c -> {
            c.setRacehubEventClassId("rh-class-c");
            c.setCreatedAt(T2);
            c.setUpdatedAt(T2);
            return c;
        }, RaceHubClassMapping::getId);
        RaceHubClassMapping first = new RaceHubClassMapping();
        first.setEventId(event.getId());
        first.setRacehubEventClassId("rh-class-a");
        first.setEventClassId(saved.getId());
        first.setCreatedAt(T1);
        first.setUpdatedAt(T1);
        mappings.save(first);

        assertThat(mappings.findByEventIdOrderByRacehubEventClassId(event.getId()))
                .extracting(RaceHubClassMapping::getRacehubEventClassId).containsExactly("rh-class-a", "rh-class-c");
        mappings.deleteByEventId(event.getId());
        assertThat(mappings.findByEventIdOrderByRacehubEventClassId(event.getId())).isEmpty();
    }

    private static RacingClass racingClass() {
        RacingClass c = new RacingClass();
        c.setName("Round trip class " + System.nanoTime());
        c.setDescription("2WD buggy");
        c.setCreatedAt(T1);
        c.setUpdatedAt(T1);
        return c;
    }

    private static RaceFormatTemplate template() {
        RaceFormatTemplate t = new RaceFormatTemplate();
        t.setName("5-minute timed");
        t.setConfig(new TimedRaceConfig(5, StartType.STAGGER, QualifyingType.FTQ, 2, 3));
        t.setCreatedAt(T1);
        t.setUpdatedAt(T1);
        return t;
    }

    private static Event event() {
        Event e = new Event();
        e.setName("Mapping event");
        e.setEventDate(LocalDate.of(2026, 10, 5));
        e.setCreatedAt(T1);
        e.setUpdatedAt(T1);
        return e;
    }
}
