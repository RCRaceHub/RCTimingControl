package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryAuditLog;
import dev.monkeypatch.rctiming.domain.entry.EntryAuditLogRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshot;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.query.competitor.CompetitorDataExportService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.monkeypatch.rctiming.jooq.generated.tables.AuditLog.AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.CompetitorAuditLog.COMPETITOR_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultsOutbox.RESULTS_OUTBOX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exporting and anonymising one competitor (#263). */
class CompetitorAnonymiseIT extends AbstractIntegrationTest {

    @Autowired CompetitorAnonymiseService anonymiseService;
    @Autowired CompetitorDataExportService exportService;
    @Autowired CompetitorRepository competitorRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired EntryAuditLogRepository entryAuditLogRepository;
    @Autowired EventRepository eventRepository;
    @Autowired RacingClassRepository racingClassRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RaceRepository raceRepository;
    @Autowired RaceEntryRepository raceEntryRepository;
    @Autowired ResultSnapshotRepository resultSnapshotRepository;
    @Autowired DSLContext dsl;

    private String run;
    private String name;
    private Long adminId;
    private Long racingClassId;
    private final List<Runnable> cleanup = new ArrayList<>();

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        name = "Pat Anonymous " + run;
        RacingClass rc = new RacingClass();
        rc.setName("Anon class " + run);
        rc.setCreatedAt(Instant.now());
        rc.setUpdatedAt(Instant.now());
        racingClassId = racingClassRepository.save(rc).getId();
        cleanup.add(() -> racingClassRepository.deleteById(racingClassId));

        User admin = new User();
        admin.setEmail("anon-" + run + "@test.com");
        admin.setPasswordHash("x");
        admin.setFirstName("Anon");
        admin.setLastName("Admin");
        admin.setRoles(Set.of(Role.ADMIN));
        admin.setCreatedAt(Instant.now());
        admin.setUpdatedAt(Instant.now());
        adminId = userRepository.save(admin).getId();
        cleanup.add(() -> {
            dsl.transaction(tx -> tx.dsl().deleteFrom(AUDIT_LOG).where(AUDIT_LOG.ACTOR_USER_ID.eq(adminId)).execute());
            userRepository.deleteById(adminId);
        });
    }

    @AfterEach
    void cleanUp() {
        cleanup.reversed().forEach(Runnable::run);
    }

    @Test
    void removesTheNameEverywhereButKeepsTheResultAndWritesAnAuditRowWithoutIt() {
        Fixture f = fixture(RaceStatus.FINISHED);

        CompetitorAnonymiseService.Result result = anonymiseService.anonymise(f.competitor.getId(), adminId);

        Competitor after = competitorRepository.findById(f.competitor.getId()).orElseThrow();
        assertThat(after.getDisplayName()).isEqualTo("Anonymised competitor #" + f.competitor.getId());
        assertThat(after.getBrcaNumber()).isNull();
        assertThat(after.getExternalId()).isNull();
        assertThat(after.getExternalSource()).isNull();
        assertThat(after.getHomeClub()).isNull();
        assertThat(after.getSpokenName()).isNull();
        assertThat(result.entries()).isEqualTo(1);
        assertThat(result.resultSnapshots()).isEqualTo(1);
        assertThat(result.exportPayloads()).isEqualTo(2);

        // The result is still there, under the new name
        String positions = dsl.select(RESULT_SNAPSHOTS.POSITIONS_JSON).from(RESULT_SNAPSHOTS)
                .where(RESULT_SNAPSHOTS.RACE_ID.eq(f.raceId)).fetchOne(RESULT_SNAPSHOTS.POSITIONS_JSON);
        assertThat(positions).contains("Anonymised competitor #" + f.competitor.getId())
                .contains("\"lapsCompleted\":10").doesNotContain(name);
        String payload = dsl.select(RESULTS_OUTBOX.PAYLOAD).from(RESULTS_OUTBOX)
                .where(RESULTS_OUTBOX.EVENT_ID.eq(f.eventId)).fetchOne(RESULTS_OUTBOX.PAYLOAD);
        assertThat(payload).doesNotContain(name).contains("Anonymised competitor");
        var exports = dsl.select(RESULTS_OUTBOX.PAYLOAD).from(RESULTS_OUTBOX)
                .where(RESULTS_OUTBOX.EVENT_ID.eq(laterEventId)).fetchOne(RESULTS_OUTBOX.PAYLOAD);
        assertThat(exports).doesNotContain(name).doesNotContain("rh-" + run).contains("Other Person " + run);
        assertThat(dsl.selectFrom(AUDIT_LOG).where(AUDIT_LOG.ACTION.eq("PENALTY_APPLIED_TEST")
                .and(AUDIT_LOG.ENTITY_ID.eq("999999"))).fetchOne().getSummary()).doesNotContain(name);
        // Someone else's row in the same snapshot is untouched
        assertThat(positions).contains("Someone Else " + run);

        Map<String, Object> competitorHistory = dsl.selectFrom(COMPETITOR_AUDIT_LOG)
                .where(COMPETITOR_AUDIT_LOG.COMPETITOR_ID.eq(f.competitor.getId())).fetchOneMap();
        assertThat(competitorHistory.get("before_value")).isNull();
        assertThat(dsl.selectFrom(ENTRY_AUDIT_LOG).where(ENTRY_AUDIT_LOG.ENTRY_ID.eq(f.entryId)).fetchOne()
                .getAfterSnapshot()).isNull();
        assertThat(dsl.selectFrom(AUDIT_LOG).where(AUDIT_LOG.ENTITY_TYPE.eq("competitor")
                        .and(AUDIT_LOG.ENTITY_ID.eq(String.valueOf(f.competitor.getId())))
                        .and(AUDIT_LOG.ACTION.eq("SOMETHING_ELSE"))).fetchOne().getSummary())
                .doesNotContain(name);

        // The audit row of the anonymisation itself holds counts and no name
        var rows = dsl.selectFrom(AUDIT_LOG).where(AUDIT_LOG.ACTION.eq(CompetitorAnonymiseService.AUDIT_ACTION)
                .and(AUDIT_LOG.ENTITY_ID.eq(String.valueOf(f.competitor.getId())))).fetch();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSummary() + rows.get(0).getAfterJson() + rows.get(0).getBeforeJson())
                .doesNotContain(name).doesNotContain("BRCA-" + run);
        assertThat(rows.get(0).getActorUserId()).isEqualTo(adminId);
    }

    @Test
    void refusesWhileTheCompetitorIsInARaceThatIsOnTheTrack() {
        Fixture f = fixture(RaceStatus.RUNNING);

        assertThatThrownBy(() -> anonymiseService.anonymise(f.competitor.getId(), adminId))
                .isInstanceOf(StateConflictException.class);

        assertThat(competitorRepository.findById(f.competitor.getId()).orElseThrow().getDisplayName()).isEqualTo(name);
    }

    @Test
    void theExportHoldsTheirDataAndIsAuditedWithoutIt() {
        Fixture f = fixture(RaceStatus.FINISHED);

        Map<String, Object> export = exportService.export(f.competitor.getId(), adminId);

        assertThat(export.toString()).contains(name).contains("BRCA-" + run);
        assertThat((List<?>) export.get("entries")).hasSize(1);
        assertThat((List<?>) export.get("results")).hasSize(1);
        assertThat(export.toString()).doesNotContain("Someone Else " + run);
        var rows = dsl.selectFrom(AUDIT_LOG).where(AUDIT_LOG.ACTION.eq(CompetitorDataExportService.AUDIT_ACTION)
                .and(AUDIT_LOG.ENTITY_ID.eq(String.valueOf(f.competitor.getId())))).fetch();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSummary()).doesNotContain(name);
        assertThat(rows.get(0).getActorUserId()).isEqualTo(adminId);
    }

    private Long laterEventId;

    private record Fixture(Competitor competitor, Long eventId, Long entryId, Long raceId) {}

    private Fixture fixture(RaceStatus raceStatus) {
        Competitor c = new Competitor();
        c.setDisplayName(name);
        c.setBrcaNumber("BRCA-" + run);
        c.setHomeClub("Fenland RC");
        c.setSpokenName("Pat Anon-imous");
        c.setExternalSource("RACEHUB");
        c.setExternalId("rh-" + run);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        Competitor competitor = competitorRepository.save(c);
        Long competitorId = competitor.getId();
        cleanup.add(() -> {
            dsl.transaction(tx -> tx.dsl().deleteFrom(AUDIT_LOG).where(AUDIT_LOG.ENTITY_ID.eq("999999")).execute());
            dsl.transaction(tx -> tx.dsl().deleteFrom(COMPETITOR_AUDIT_LOG)
                    .where(COMPETITOR_AUDIT_LOG.COMPETITOR_ID.eq(competitorId)).execute());
            dsl.transaction(tx -> tx.dsl().deleteFrom(AUDIT_LOG)
                    .where(AUDIT_LOG.ENTITY_TYPE.eq("competitor").and(AUDIT_LOG.ENTITY_ID.eq(String.valueOf(competitorId))))
                    .execute());
            competitorRepository.deleteById(competitorId);
        });

        Event event = new Event();
        event.setName("Anon meeting " + run);
        event.setEventDate(LocalDate.of(2026, 1, 1));
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        Event savedEvent = eventRepository.save(event);
        Long eventId = savedEvent.getId();
        cleanup.add(() -> eventRepository.deleteById(eventId));
        Event later = new Event();
        later.setName("Anon later meeting " + run);
        later.setEventDate(LocalDate.of(2026, 2, 1));
        later.setCreatedAt(Instant.now());
        later.setUpdatedAt(Instant.now());
        laterEventId = eventRepository.save(later).getId();
        Long laterId = laterEventId;
        cleanup.add(() -> eventRepository.deleteById(laterId));
        Long ecId = dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, eventId)
                .set(EVENT_CLASSES.RACING_CLASS_ID, racingClassId)
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"TIMED\"}")
                .returning(EVENT_CLASSES.ID).fetchOne().get(EVENT_CLASSES.ID));
        cleanup.add(() -> dsl.transaction(tx -> tx.dsl().deleteFrom(EVENT_CLASSES).where(EVENT_CLASSES.ID.eq(ecId)).execute()));

        Entry mine = entry(competitorId, eventId, ecId);
        Entry other = entry(null, eventId, ecId);

        Round round = new Round();
        round.setEventId(eventId);
        round.setType(RoundType.FINAL);
        round.setRoundNumber(1);
        round.setSequenceInEvent(1);
        round.setCreatedAt(Instant.now());
        round.setUpdatedAt(Instant.now());
        Long roundId = roundRepository.save(round).getId();
        cleanup.add(() -> roundRepository.deleteById(roundId));
        Race race = new Race();
        race.setRoundId(roundId);
        race.setEventClassId(ecId);
        race.setHeatNumber(1);
        race.setSequenceInRound(1);
        race.setFinalLetter("A");
        race.setStartType(StartType.GRID);
        race.setStatus(raceStatus);
        race.setCreatedAt(Instant.now());
        race.setUpdatedAt(Instant.now());
        Long raceId = raceRepository.save(race).getId();
        cleanup.add(() -> raceRepository.deleteById(raceId));
        for (Entry e : List.of(mine, other)) {
            RaceEntry re = new RaceEntry();
            re.setRaceId(raceId);
            re.setEntryId(e.getId());
            raceEntryRepository.save(re);
        }

        ResultSnapshot snap = new ResultSnapshot();
        snap.setRaceId(raceId);
        snap.setPositionsJson(String.format(
                "[{\"position\":1,\"entryId\":%d,\"competitorId\":%d,\"driverName\":\"%s\",\"carNumber\":\"1\","
                        + "\"lapsCompleted\":10,\"totalTimeMs\":60000,\"bestLapMs\":6000,\"gapToLeaderMs\":0},"
                        + "{\"position\":2,\"entryId\":%d,\"driverName\":\"Someone Else %s\",\"carNumber\":\"2\","
                        + "\"lapsCompleted\":9,\"totalTimeMs\":60000,\"bestLapMs\":6100,\"gapToLeaderMs\":1}]",
                mine.getId(), competitorId, name, other.getId(), run));
        snap.setLapHistoryJson("[]");
        snap.setFinishedAt(Instant.now());
        snap.setCreatedAt(Instant.now());
        resultSnapshotRepository.save(snap);
        cleanup.add(() -> dsl.transaction(tx -> tx.dsl().deleteFrom(RESULT_SNAPSHOTS)
                .where(RESULT_SNAPSHOTS.RACE_ID.eq(raceId)).execute()));

        String payload = String.format("{\"results\":[{\"rctcEntryId\":%d,\"rctcCompetitorId\":%d,\"displayName\":\"%s\"}]}",
                mine.getId(), competitorId, name);
        dsl.transaction(tx -> tx.dsl().insertInto(RESULTS_OUTBOX)
                .set(RESULTS_OUTBOX.EVENT_ID, eventId)
                .set(RESULTS_OUTBOX.REVISION, 1L)
                .set(RESULTS_OUTBOX.REASON, "RACE_FINISHED")
                .set(RESULTS_OUTBOX.PAYLOAD, payload)
                .set(RESULTS_OUTBOX.NEXT_ATTEMPT_AT, Instant.now())
                .set(RESULTS_OUTBOX.CREATED_AT, Instant.now()).execute());
        // A later round's export carries the standings, with the RaceHub identity, for an event they never entered.
        // Its "entryId" is RaceHub's, and happens to equal an RCTC entry id of this competitor's.
        String standings = String.format("{\"championships\":[{\"standings\":[{\"rctcCompetitorId\":%d,"
                + "\"driverProfileId\":\"rh-%s\",\"externalSource\":\"RACEHUB\",\"displayName\":\"%s\"}]}],"
                + "\"results\":[{\"entryId\":\"%d\",\"displayName\":\"Other Person %s\"}]}",
                competitorId, run, name, mine.getId(), run);
        dsl.transaction(tx -> tx.dsl().insertInto(RESULTS_OUTBOX)
                .set(RESULTS_OUTBOX.EVENT_ID, laterEventId)
                .set(RESULTS_OUTBOX.REVISION, 2L)
                .set(RESULTS_OUTBOX.REASON, "DAY_CLOSE")
                .set(RESULTS_OUTBOX.PAYLOAD, standings)
                .set(RESULTS_OUTBOX.NEXT_ATTEMPT_AT, Instant.now())
                .set(RESULTS_OUTBOX.CREATED_AT, Instant.now()).execute());
        cleanup.add(() -> dsl.transaction(tx -> tx.dsl().deleteFrom(RESULTS_OUTBOX)
                .where(RESULTS_OUTBOX.EVENT_ID.in(eventId, laterEventId)).execute()));

        entryAuditLogRepository.save(EntryAuditLog.of(mine.getId(), adminId, "TRANSPONDER_SWAPPED",
                "Swapped for " + name, "{\"displayName\":\"" + name + "\"}", "{\"displayName\":\"" + name + "\"}",
                Instant.now()));
        dsl.transaction(tx -> tx.dsl().insertInto(COMPETITOR_AUDIT_LOG)
                .set(COMPETITOR_AUDIT_LOG.COMPETITOR_ID, competitorId)
                .set(COMPETITOR_AUDIT_LOG.ACTOR_USER_ID, adminId)
                .set(COMPETITOR_AUDIT_LOG.ACTION, "SPOKEN_NAME_CHANGED")
                .set(COMPETITOR_AUDIT_LOG.BEFORE_VALUE, "Pat Anon-imous")
                .set(COMPETITOR_AUDIT_LOG.CREATED_AT, Instant.now()).execute());
        dsl.transaction(tx -> tx.dsl().insertInto(AUDIT_LOG)
                .set(AUDIT_LOG.OCCURRED_AT, Instant.now())
                .set(AUDIT_LOG.ACTOR_LABEL, "system:test")
                .set(AUDIT_LOG.SOURCE, "SYSTEM")
                .set(AUDIT_LOG.ACTION, "PENALTY_APPLIED_TEST")
                .set(AUDIT_LOG.ENTITY_TYPE, "penalty")
                .set(AUDIT_LOG.ENTITY_ID, "999999")
                .set(AUDIT_LOG.SUMMARY, "Gave " + name + " a penalty").execute());
        dsl.transaction(tx -> tx.dsl().insertInto(AUDIT_LOG)
                .set(AUDIT_LOG.OCCURRED_AT, Instant.now())
                .set(AUDIT_LOG.ACTOR_LABEL, "system:test")
                .set(AUDIT_LOG.SOURCE, "SYSTEM")
                .set(AUDIT_LOG.ACTION, "SOMETHING_ELSE")
                .set(AUDIT_LOG.ENTITY_TYPE, "competitor")
                .set(AUDIT_LOG.ENTITY_ID, String.valueOf(competitorId))
                .set(AUDIT_LOG.SUMMARY, "Changed " + name)
                .set(AUDIT_LOG.BEFORE_JSON, "{\"displayName\":\"" + name + "\"}").execute());
        return new Fixture(competitor, eventId, mine.getId(), raceId);
    }

    private Entry entry(Long competitorId, Long eventId, Long eventClassId) {
        Entry e = new Entry();
        e.setCompetitorId(competitorId);
        e.setEventId(eventId);
        e.setEventClassId(eventClassId);
        e.setTransponderNumberSnapshot("T-" + UUID.randomUUID().toString().substring(0, 8));
        e.setStatus(EntryStatus.CONFIRMED);
        e.setSubmittedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        Entry saved = entryRepository.save(e);
        cleanup.add(() -> {
            dsl.transaction(tx -> tx.dsl().deleteFrom(RACE_ENTRIES).where(RACE_ENTRIES.ENTRY_ID.eq(saved.getId())).execute());
            dsl.transaction(tx -> tx.dsl().deleteFrom(ENTRY_AUDIT_LOG).where(ENTRY_AUDIT_LOG.ENTRY_ID.eq(saved.getId())).execute());
            entryRepository.deleteById(saved.getId());
        });
        return saved;
    }
}
