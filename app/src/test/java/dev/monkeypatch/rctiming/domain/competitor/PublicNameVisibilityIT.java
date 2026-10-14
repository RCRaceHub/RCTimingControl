package dev.monkeypatch.rctiming.domain.competitor;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.championship.Championship;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipEventLink;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipEventLinkRepository;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipPointsScaleEntry;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipPointsScaleRepository;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipRepository;
import dev.monkeypatch.rctiming.domain.championship.ScoringSource;
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
import dev.monkeypatch.rctiming.livefeed.LiveFeedRaceLookup;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipEventLinks.CHAMPIONSHIP_EVENT_LINKS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.CompetitorAuditLog.COMPETITOR_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipExclusions.CHAMPIONSHIP_EXCLUSIONS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Initials-only and hidden names on every public surface and in the live feed (#265). */
class PublicNameVisibilityIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired CompetitorNameVisibilityService visibilityService;
    @Autowired LiveFeedRaceLookup raceLookup;
    @Autowired LapTimingService lapTimingService;
    @Autowired CompetitorService competitorService;
    @Autowired CompetitorRepository competitorRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired EntryAuditLogRepository auditLogRepository;
    @Autowired EventRepository eventRepository;
    @Autowired RacingClassRepository racingClassRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ChampionshipRepository championshipRepository;
    @Autowired ChampionshipEventLinkRepository eventLinkRepository;
    @Autowired ChampionshipPointsScaleRepository pointsScaleRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RaceRepository raceRepository;
    @Autowired RaceEntryRepository raceEntryRepository;
    @Autowired ResultSnapshotRepository resultSnapshotRepository;
    @Autowired DSLContext dsl;

    private String run;
    private Long racingClassId;
    private Long adminId;

    /** Undo what the test created, newest first, so later tests share a clean database. */
    private final List<Runnable> cleanup = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        cleanup.reversed().forEach(Runnable::run);
    }

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().substring(0, 8);
        RacingClass rc = new RacingClass();
        rc.setName("Merge class " + run);
        rc.setCreatedAt(Instant.now());
        rc.setUpdatedAt(Instant.now());
        racingClassId = racingClassRepository.save(rc).getId();
        cleanup.add(() -> racingClassRepository.deleteById(racingClassId));

        User admin = new User();
        admin.setEmail("merge-" + run + "@test.com");
        admin.setPasswordHash(passwordEncoder.encode("pass12345"));
        admin.setFirstName("Merge");
        admin.setLastName("Admin");
        admin.setRoles(Set.of(Role.ADMIN));
        admin.setCreatedAt(Instant.now());
        admin.setUpdatedAt(Instant.now());
        adminId = userRepository.save(admin).getId();
        cleanup.add(() -> userRepository.deleteById(adminId));
    }

    @Test
    void initialsAndHiddenApplyToResultsBoardsStandingsAndTheLiveFeed() {
        Competitor junior = competitor("Ada Lovelace (" + run + ")", null, null, null, null, null);
        Competitor hidden = competitor("Grace Hopper (" + run + ")", null, null, null, null, null);
        Competitor shown = competitor("Alan Turing (" + run + ")", null, null, null, null, null);
        Championship champ = championship(ScoringSource.FINALS);
        pointsScaleRepository.save(new ChampionshipPointsScaleEntry(champ.getId(), 1, 10));
        pointsScaleRepository.save(new ChampionshipPointsScaleEntry(champ.getId(), 2, 8));
        pointsScaleRepository.save(new ChampionshipPointsScaleEntry(champ.getId(), 3, 6));
        long[] juniorRace = finishedFinal(champ, 1, junior.getId(), 1);
        long[] hiddenRace = finishedFinal(champ, 2, hidden.getId(), 2);
        long[] shownRace = finishedFinal(champ, 3, shown.getId(), 3);
        visibilityService.set(junior.getId(), NameVisibility.INITIALS, adminId);
        visibilityService.set(hidden.getId(), NameVisibility.HIDE, adminId);

        // Results page
        assertThat(get("/api/v1/results/" + juniorRace[1])).contains("\"A. L.\"").doesNotContain("Ada Lovelace");
        assertThat(get("/api/v1/results/" + hiddenRace[1])).contains("\"Driver 1\"").doesNotContain("Grace Hopper");
        assertThat(get("/api/v1/results/" + shownRace[1])).contains("Alan Turing (" + run + ")");
        // Results board
        assertThat(get("/api/v1/boards/results?eventId=" + juniorRace[0]))
                .contains("\"A. L.\"").doesNotContain("Ada Lovelace");
        // Championship standings
        String standings = get("/api/v1/championships/" + champ.getId());
        assertThat(standings).contains("\"A. L.\"").contains("\"Driver\"").contains("Alan Turing (" + run + ")")
                .doesNotContain("Ada Lovelace").doesNotContain("Grace Hopper");
        // Live feed grid
        assertThat(raceLookup.find(juniorRace[1]).orElseThrow().grid()).singleElement()
                .extracting(LiveFeedRaceLookup.GridCar::displayName).isEqualTo("A. L.");
        assertThat(raceLookup.find(hiddenRace[1]).orElseThrow().grid()).singleElement()
                .extracting(LiveFeedRaceLookup.GridCar::displayName).isEqualTo("Driver");
        // Live timing, which goes to anonymous screens and the live feed
        lapTimingService.releaseState(juniorRace[1]);
        lapTimingService.stateFor(juniorRace[1]).applyLapPassing(
                new LapPassingEvent(1L, "TX", 1_000_000L), juniorRace[2]);
        assertThat(lapTimingService.peek(juniorRace[1]).orElseThrow().calculatePositions())
                .singleElement().extracting(LiveTimingRowDto::driverName).isEqualTo("A. L.");
        lapTimingService.releaseState(juniorRace[1]);
    }

    @Test
    void showingTheNameAgainPutsItBack() {
        Competitor c = competitor("Edsger Dijkstra (" + run + ")", null, null, null, null, null);
        Championship champ = championship(ScoringSource.FINALS);
        pointsScaleRepository.save(new ChampionshipPointsScaleEntry(champ.getId(), 1, 10));
        long[] race = finishedFinal(champ, 1, c.getId(), 1);
        visibilityService.set(c.getId(), NameVisibility.HIDE, adminId);
        assertThat(get("/api/v1/results/" + race[1])).doesNotContain("Edsger");

        visibilityService.set(c.getId(), NameVisibility.SHOW, adminId);

        assertThat(get("/api/v1/results/" + race[1])).contains("Edsger Dijkstra (" + run + ")");
    }

    private String get(String url) {
        return restTemplate.getForObject(url, String.class);
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private Competitor competitor(String name, String source, String externalId, String brca, String club,
                                  String spoken) {
        Competitor c = new Competitor();
        c.setDisplayName(name);
        c.setExternalSource(source);
        c.setExternalId(externalId);
        c.setBrcaNumber(brca);
        c.setHomeClub(club);
        c.setSpokenName(spoken);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        Competitor saved = competitorRepository.save(c);
        // The merge deletes the duplicate, so this may find nothing
        cleanup.add(() -> {
            dsl.transaction(tx -> tx.dsl().deleteFrom(COMPETITOR_AUDIT_LOG)
                    .where(COMPETITOR_AUDIT_LOG.COMPETITOR_ID.eq(saved.getId())).execute());
            competitorRepository.deleteById(saved.getId());
        });
        return saved;
    }

    private Event event(String name) {
        Event e = new Event();
        e.setName(name);
        e.setEventDate(LocalDate.of(2026, 1, 1));
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        Event saved = eventRepository.save(e);
        cleanup.add(() -> eventRepository.deleteById(saved.getId()));
        return saved;
    }

    private Long eventClass(Long eventId) {
        // Writes go through a transaction, onto the write connection
        Long id = dsl.transactionResult(tx -> tx.dsl().insertInto(EVENT_CLASSES)
                .set(EVENT_CLASSES.EVENT_ID, eventId)
                .set(EVENT_CLASSES.RACING_CLASS_ID, racingClassId)
                .set(EVENT_CLASSES.CONFIG_SNAPSHOT, "{\"type\":\"TIMED\"}")
                .returning(EVENT_CLASSES.ID).fetchOne().get(EVENT_CLASSES.ID));
        cleanup.add(() -> dsl.transaction(tx -> tx.dsl().deleteFrom(EVENT_CLASSES).where(EVENT_CLASSES.ID.eq(id)).execute()));
        return id;
    }

    private Entry entry(Long competitorId, Long eventId, Long eventClassId, EntryStatus status) {
        Entry e = new Entry();
        e.setCompetitorId(competitorId);
        e.setEventId(eventId);
        e.setEventClassId(eventClassId);
        e.setTransponderNumberSnapshot("T-" + UUID.randomUUID().toString().substring(0, 8));
        e.setStatus(status);
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

    private Championship championship(ScoringSource source) {
        Championship c = new Championship();
        c.setName("Merge champ " + UUID.randomUUID());
        c.setTqBonusPoints(0);
        c.setAfinalWinnerBonusPoints(0);
        c.setScoringSource(source);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        Championship saved = championshipRepository.save(c);
        cleanup.add(() -> {
            dsl.transaction(tx -> tx.dsl().deleteFrom(CHAMPIONSHIP_EXCLUSIONS)
                    .where(CHAMPIONSHIP_EXCLUSIONS.CHAMPIONSHIP_ID.eq(saved.getId())).execute());
            championshipRepository.deleteById(saved.getId());
        });
        return saved;
    }

    /** A meeting in the championship where the competitor finished the A final in the given position. */
    private long[] finishedFinal(Championship champ, int roundNumber, Long competitorId, int position) {
        Event event = event("Champ meeting " + roundNumber + " " + run);
        ChampionshipEventLink link = new ChampionshipEventLink();
        link.setChampionshipId(champ.getId());
        link.setEventId(event.getId());
        link.setRoundNumber(roundNumber);
        link.setCreatedAt(Instant.now());
        eventLinkRepository.save(link);
        cleanup.add(() -> dsl.transaction(tx -> tx.dsl().deleteFrom(CHAMPIONSHIP_EVENT_LINKS)
                .where(CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID.eq(champ.getId()))
                .and(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID.eq(event.getId())).execute()));
        Long ecId = eventClass(event.getId());
        Round round = new Round();
        round.setEventId(event.getId());
        round.setType(RoundType.FINAL);
        round.setRoundNumber(1);
        round.setSequenceInEvent(1);
        round.setCreatedAt(Instant.now());
        round.setUpdatedAt(Instant.now());
        round = roundRepository.save(round);
        Long roundId = round.getId();
        cleanup.add(() -> roundRepository.deleteById(roundId));
        Race race = new Race();
        race.setRoundId(round.getId());
        race.setEventClassId(ecId);
        race.setHeatNumber(1);
        race.setSequenceInRound(1);
        race.setFinalLetter("A");
        race.setStartType(StartType.GRID);
        race.setStatus(RaceStatus.FINISHED);
        race.setCreatedAt(Instant.now());
        race.setUpdatedAt(Instant.now());
        race.setFinishedAt(Instant.now());
        race = raceRepository.save(race);
        Long raceId = race.getId();
        cleanup.add(() -> raceRepository.deleteById(raceId));
        Entry entry = entry(competitorId, event.getId(), ecId, EntryStatus.CONFIRMED);
        RaceEntry re = new RaceEntry();
        re.setRaceId(race.getId());
        re.setEntryId(entry.getId());
        raceEntryRepository.save(re);
        ResultSnapshot snap = new ResultSnapshot();
        snap.setRaceId(race.getId());
        snap.setPositionsJson(String.format(
                "[{\"position\":%d,\"entryId\":%d,\"competitorId\":%d,\"driverName\":\"%s\",\"carNumber\":\"1\","
                        + "\"lapsCompleted\":10,\"totalTimeMs\":60000,\"bestLapMs\":6000,\"gapToLeaderMs\":0}]",
                position, entry.getId(), competitorId, competitorRepository.findById(competitorId).orElseThrow().getDisplayName()));
        snap.setLapHistoryJson("[]");
        snap.setFinishedAt(Instant.now());
        snap.setCreatedAt(Instant.now());
        resultSnapshotRepository.save(snap);
        cleanup.add(() -> dsl.transaction(tx -> tx.dsl().deleteFrom(RESULT_SNAPSHOTS)
                .where(RESULT_SNAPSHOTS.RACE_ID.eq(raceId)).execute()));
        return new long[] {event.getId(), raceId, entry.getId()};
    }
}
