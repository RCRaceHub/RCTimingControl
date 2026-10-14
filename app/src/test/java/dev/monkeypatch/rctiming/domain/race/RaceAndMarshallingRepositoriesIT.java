package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplate;
import dev.monkeypatch.rctiming.domain.format.RaceFormatTemplateRepository;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static dev.monkeypatch.rctiming.persistence.RoundTrip.assertSavedAndReloaded;
import static org.assertj.core.api.Assertions.assertThat;

/** The race and marshalling repositories save and load every field (#73). */
class RaceAndMarshallingRepositoriesIT extends AbstractIntegrationTest {

    private static final Instant T1 = Instant.parse("2026-10-05T10:15:30.123456Z");
    private static final Instant T2 = T1.plus(1, ChronoUnit.HOURS);

    @Autowired RoundRepository rounds;
    @Autowired RaceRepository races;
    @Autowired RaceEntryRepository raceEntries;
    @Autowired ResultSnapshotRepository snapshots;
    @Autowired MarshalAdjustmentRepository adjustments;
    @Autowired MarshalAbsenceRepository absences;
    @Autowired MarshalPenaltyRepository marshalPenalties;
    @Autowired PenaltyRepository penalties;
    @Autowired IncidentReportRepository incidents;
    @Autowired EventRepository events;
    @Autowired EventClassRepository eventClasses;
    @Autowired RaceFormatTemplateRepository templates;
    @Autowired EntryRepository entries;
    @Autowired UserRepository users;
    @Autowired RaceStateMachineService stateMachine;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<Runnable> cleanup = new ArrayList<>();
    private Event event;
    private EventClass eventClass;
    private RaceFormatTemplate template;
    private Entry entry;
    private User official;

    @BeforeEach
    void setUp() {
        event = events.save(event());
        cleanup.add(() -> events.deleteById(event.getId()));
        template = templates.save(template());
        cleanup.add(() -> templates.deleteById(template.getId()));
        eventClass = eventClasses.save(eventClass());
        cleanup.add(() -> eventClasses.deleteById(eventClass.getId()));
        entry = entries.save(entry());
        cleanup.add(() -> entries.deleteById(entry.getId()));
        official = users.save(official());
        cleanup.add(() -> users.deleteById(official.getId()));
    }

    @AfterEach
    void cleanUp() {
        cleanup.reversed().forEach(Runnable::run);
    }

    @Test
    void roundAndRace() {
        Round savedRound = assertSavedAndReloaded(rounds, round(RoundType.QUALIFIER, 1), r -> {
            r.setType(RoundType.FINAL);
            r.setRoundNumber(2);
            r.setSequenceInEvent(5);
            r.setStatus(RoundStatus.COMPLETED);
            r.setCreatedAt(T2);
            r.setUpdatedAt(T2);
            return r;
        }, Round::getId);
        cleanup.add(() -> rounds.deleteById(savedRound.getId()));

        Race r = race(savedRound.getId(), 1);
        r.setFinalLetter("B");
        r.setStartType(StartType.GRID);
        r.setStatus(RaceStatus.RUNNING);
        r.setStartedAt(T1);
        Race savedRace = assertSavedAndReloaded(races, r, c -> {
            c.setHeatNumber(3);
            c.setSequenceInRound(4);
            c.setFinalLetter(null);
            c.setStartType(StartType.STAGGER);
            c.setStatus(RaceStatus.FINISHED);
            c.setStartedAt(null);
            c.setFinishedAt(T2);
            c.setAbandonedAt(T2);
            c.setCreatedAt(T2);
            c.setUpdatedAt(T2);
            return c;
        }, Race::getId);

        assertThat(rounds.findByEventIdOrderBySequenceInEvent(event.getId())).extracting(Round::getId)
                .containsExactly(savedRound.getId());
        assertThat(rounds.existsByEventId(event.getId())).isTrue();
        assertThat(races.findByRoundIdOrderBySequenceInRound(savedRound.getId())).extracting(Race::getId)
                .containsExactly(savedRace.getId());
        assertThat(races.findByEventClassIdAndRoundType(eventClass.getId(), RoundType.FINAL)).extracting(Race::getId)
                .containsExactly(savedRace.getId());
        assertThat(races.findByEventClassIdAndRoundType(eventClass.getId(), RoundType.QUALIFIER)).isEmpty();
        savedRace.setFinalLetter("A");
        races.save(savedRace);
        assertThat(races.findByEventClassIdAndFinalLetter(eventClass.getId(), "A")).extracting(Race::getId)
                .containsExactly(savedRace.getId());
        assertThat(races.findFirstByStatus(RaceStatus.FINISHED)).isPresent();
    }

    @Test
    void raceEntriesAndResultSnapshot() {
        Round round = rounds.save(round(RoundType.QUALIFIER, 1));
        cleanup.add(() -> rounds.deleteById(round.getId()));
        Race race = races.save(race(round.getId(), 1));

        RaceEntry e = new RaceEntry();
        e.setRaceId(race.getId());
        e.setEntryId(entry.getId());
        e.setGridPosition(2);
        e.setBumped(true);
        e.setCarNumber(7);
        RaceEntry saved = assertSavedAndReloaded(raceEntries, e, c -> {
            c.setGridPosition(null);
            c.setBumped(false);
            c.setCarNumber(null);
            return c;
        }, RaceEntry::getId);
        assertThat(raceEntries.findByRaceIdOrderByGridPosition(race.getId())).extracting(RaceEntry::getId)
                .containsExactly(saved.getId());
        raceEntries.deleteAll(List.of(saved));
        assertThat(raceEntries.existsById(saved.getId())).isFalse();

        ResultSnapshot s = new ResultSnapshot();
        s.setRaceId(race.getId());
        s.setFinishedAt(T1);
        s.setPositionsJson("[]");
        s.setLapHistoryJson("[]");
        s.setCreatedAt(T1);
        ResultSnapshot snapshot = assertSavedAndReloaded(snapshots, s, c -> {
            c.setFinishedAt(T2);
            c.setPositionsJson("[{\"position\":1}]");
            c.setLapHistoryJson("[{\"lap\":1}]");
            c.setCreatedAt(T2);
            return c;
        }, ResultSnapshot::getId);
        assertThat(snapshots.findByRaceId(race.getId())).get().extracting(ResultSnapshot::getId)
                .isEqualTo(snapshot.getId());
    }

    @Test
    void marshallingAndRefereeRecords() {
        Round round = rounds.save(round(RoundType.QUALIFIER, 1));
        cleanup.add(() -> rounds.deleteById(round.getId()));
        Race race = races.save(race(round.getId(), 1));
        cleanup.add(() -> races.deleteById(race.getId()));

        MarshalAdjustment a = new MarshalAdjustment();
        a.setRaceId(race.getId());
        a.setEntryId(entry.getId());
        a.setTransponderNumber("1234567");
        a.setLapDelta(1);
        a.setRaceStateAtTime("RUNNING");
        a.setActingUserId(official.getId());
        a.setActingUserName("Race Director");
        a.setAdjustedAt(T1);
        MarshalAdjustment adjustment = assertSavedAndReloaded(adjustments, a, c -> {
            c.setTransponderNumber("7654321");
            c.setLapDelta(-1);
            c.setRaceStateAtTime("STOPPED");
            c.setActingUserName("Other Director");
            c.setAdjustedAt(T2);
            return c;
        }, MarshalAdjustment::getId);
        cleanup.add(() -> adjustments.deleteById(adjustment.getId()));
        assertThat(adjustments.findByRaceIdOrderByAdjustedAt(race.getId())).extracting(MarshalAdjustment::getId)
                .containsExactly(adjustment.getId());

        MarshalAbsence ab = new MarshalAbsence();
        ab.setRaceId(race.getId());
        ab.setEntryId(entry.getId());
        ab.setEventId(event.getId());
        ab.setRecordedAt(T1);
        ab.setRecordedBy(official.getId());
        MarshalAbsence absence = assertSavedAndReloaded(absences, ab, c -> {
            c.setRecordedAt(T2);
            c.setRecordedBy(official.getId() + 1);
            return c;
        }, MarshalAbsence::getId);
        cleanup.add(() -> absences.deleteById(absence.getId()));
        assertThat(absences.findByEventId(event.getId())).extracting(MarshalAbsence::getId)
                .containsExactly(absence.getId());

        MarshalPenalty mp = new MarshalPenalty();
        mp.setAbsenceId(absence.getId());
        mp.setEntryId(entry.getId());
        mp.setEventId(event.getId());
        mp.setAppliedBy(official.getId());
        mp.setAppliedAt(T1);
        mp.setNotes("Missed marshalling");
        MarshalPenalty marshalPenalty = assertSavedAndReloaded(marshalPenalties, mp, c -> {
            c.setAbsenceId(null);
            c.setAppliedAt(T2);
            c.setNotes(null);
            return c;
        }, MarshalPenalty::getId);
        cleanup.add(() -> marshalPenalties.deleteById(marshalPenalty.getId()));

        Penalty p = new Penalty();
        p.setRaceId(race.getId());
        p.setEntryId(entry.getId());
        p.setPenaltyType(PenaltyType.TIME);
        p.setValue(new BigDecimal("2.5"));
        p.setReason("Cutting");
        p.setAppliedBy(official.getId());
        p.setAppliedAt(T1);
        Penalty penalty = assertSavedAndReloaded(penalties, p, c -> {
            c.setPenaltyType(PenaltyType.LAP);
            c.setValue(new BigDecimal("1"));
            c.setReason(null);
            c.setAppliedAt(T2);
            return c;
        }, Penalty::getId);
        cleanup.add(() -> penalties.deleteById(penalty.getId()));
        assertThat(penalties.findByRaceId(race.getId())).extracting(Penalty::getId).containsExactly(penalty.getId());

        IncidentReport ir = new IncidentReport();
        ir.setRaceId(race.getId());
        ir.setEntryId(entry.getId());
        ir.setIncidentType("CONTACT");
        ir.setDescription("Turn 3");
        ir.setRaisedBy(official.getId());
        ir.setRaisedAt(T1);
        IncidentReport incident = assertSavedAndReloaded(incidents, ir, c -> {
            c.setIncidentType("BLOCKING");
            c.setDescription(null);
            c.setRaisedAt(T2);
            return c;
        }, IncidentReport::getId);
        cleanup.add(() -> incidents.deleteById(incident.getId()));
    }

    @Test
    void finishingARaceSnapshotsItWithItsFinishTime() {
        Round round = rounds.save(round(RoundType.QUALIFIER, 1));
        cleanup.add(() -> rounds.deleteById(round.getId()));
        Race r = race(round.getId(), 1);
        r.setStatus(RaceStatus.RUNNING);
        r.setStartedAt(T1);
        Race race = races.save(r);

        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                stateMachine.abandon(races.findById(race.getId()).orElseThrow()));

        Race finished = races.findById(race.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(RaceStatus.FINISHED);
        assertThat(finished.getFinishedAt()).isNotNull().isEqualTo(finished.getAbandonedAt());
        assertThat(snapshots.findByRaceId(race.getId())).get()
                .extracting(ResultSnapshot::getFinishedAt).isEqualTo(finished.getFinishedAt());
    }

    @Test
    void theRunningRaceLookupWaitsForARaceBeingStartedToCommit() throws Exception {
        assertThat(races.findFirstByStatus(RaceStatus.RUNNING)).as("no other race is running").isEmpty();
        Round round = rounds.save(round(RoundType.QUALIFIER, 1));
        cleanup.add(() -> rounds.deleteById(round.getId()));
        Race r = race(round.getId(), 1);
        r.setStatus(RaceStatus.GRID);
        Race race = races.save(r);

        CountDownLatch saved = new CountDownLatch(1);
        Thread starter = new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Race loaded = races.findById(race.getId()).orElseThrow();
            loaded.setStatus(RaceStatus.RUNNING);
            races.save(loaded);
            saved.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        starter.start();
        assertThat(saved.await(5, TimeUnit.SECONDS)).isTrue();

        // As the decoder listener does, outside any transaction, while the start is still uncommitted
        Optional<Race> running = races.findFirstByStatus(RaceStatus.RUNNING);
        starter.join();

        assertThat(running).get().extracting(Race::getId).isEqualTo(race.getId());
    }

    private Round round(RoundType type, int sequence) {
        Round r = new Round();
        r.setEventId(event.getId());
        r.setType(type);
        r.setRoundNumber(1);
        r.setSequenceInEvent(sequence);
        r.setStatus(RoundStatus.PENDING);
        r.setCreatedAt(T1);
        r.setUpdatedAt(T1);
        return r;
    }

    private Race race(Long roundId, int heat) {
        Race r = new Race();
        r.setRoundId(roundId);
        r.setEventClassId(eventClass.getId());
        r.setHeatNumber(heat);
        r.setSequenceInRound(heat);
        r.setStartType(StartType.STAGGER);
        r.setStatus(RaceStatus.PENDING);
        r.setCreatedAt(T1);
        r.setUpdatedAt(T1);
        return r;
    }

    private static Event event() {
        Event e = new Event();
        e.setName("Race repository event");
        e.setEventDate(LocalDate.of(2026, 10, 5));
        e.setCreatedAt(T1);
        e.setUpdatedAt(T1);
        return e;
    }

    private static RaceFormatTemplate template() {
        RaceFormatTemplate t = new RaceFormatTemplate();
        t.setName("5-minute timed");
        t.setConfig(new TimedRaceConfig(5, StartType.STAGGER,
                QualifyingType.FTQ, 2, 3));
        t.setCreatedAt(T1);
        t.setUpdatedAt(T1);
        return t;
    }

    private EventClass eventClass() {
        EventClass ec = new EventClass();
        ec.setEventId(event.getId());
        ec.setConfigSnapshot(template.getConfig());
        ec.setCreatedAt(T1);
        ec.setUpdatedAt(T1);
        return ec;
    }

    private Entry entry() {
        Entry e = new Entry();
        e.setEventId(event.getId());
        e.setEventClassId(eventClass.getId());
        e.setTransponderNumberSnapshot("1234567");
        e.setStatus(EntryStatus.CONFIRMED);
        e.setSubmittedAt(T1);
        e.setUpdatedAt(T1);
        return e;
    }

    private static User official() {
        User u = new User();
        u.setEmail("race-official-" + System.nanoTime() + "@example.com");
        u.setPasswordHash("x");
        u.setFirstName("Race");
        u.setLastName("Official");
        u.setRoles(Set.of(Role.RACE_DIRECTOR));
        u.setCreatedAt(T1);
        u.setUpdatedAt(T1);
        return u;
    }
}
