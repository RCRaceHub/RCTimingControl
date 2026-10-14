package dev.monkeypatch.rctiming.domain.practice;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.monkeypatch.rctiming.persistence.RoundTrip.assertSavedAndReloaded;
import static org.assertj.core.api.Assertions.assertThat;

/** The practice session and lap repositories save and load every field (#75). */
class PracticeRepositoriesIT extends AbstractIntegrationTest {

    private static final Instant T1 = Instant.parse("2026-10-05T10:15:30.123456Z");
    private static final Instant T2 = T1.plus(1, ChronoUnit.HOURS);

    @Autowired PracticeSessionRepository sessions;
    @Autowired PracticeLapRepository laps;
    @Autowired PracticeDriverRepository drivers;
    @Autowired CompetitorRepository competitors;
    @Autowired EventRepository events;
    @Autowired UserRepository users;

    private final List<Runnable> cleanup = new ArrayList<>();
    private Event event;
    private User official;

    @BeforeEach
    void setUp() {
        event = events.save(event());
        cleanup.add(() -> events.deleteById(event.getId()));
        official = users.save(official());
        cleanup.add(() -> users.deleteById(official.getId()));
    }

    @AfterEach
    void cleanUp() {
        cleanup.reversed().forEach(Runnable::run);
    }

    @Test
    void sessionRoundTripAndFinders() {
        PracticeSession s = session();
        s.setEventId(event.getId());
        s.setStatus(PracticeStatus.STOPPED);
        s.setBestLapN(5);
        s.setCreatedByUserId(official.getId());
        s.setStartedAt(T1);
        s.setStoppedAt(T2);
        s.setUpdatedAt(T1);
        PracticeSession saved = assertSavedAndReloaded(sessions, s, x -> {
            x.setName("Renamed practice");
            x.setEventId(null);
            x.setStatus(PracticeStatus.IDLE);
            x.setBestLapN(3);
            x.setCreatedByUserId(null);
            x.setStartedAt(null);
            x.setStoppedAt(null);
            x.setUpdatedAt(T2);
            return x;
        }, PracticeSession::getId);
        cleanup.add(() -> sessions.deleteById(saved.getId()));

        Instant created = saved.getCreatedAt();
        saved.setCreatedAt(created.plus(1, ChronoUnit.DAYS));
        sessions.save(saved);
        assertThat(sessions.findById(saved.getId()).orElseThrow().getCreatedAt())
                .as("creation time is set on insert only").isEqualTo(created);

        PracticeSession running = session();
        running.setEventId(event.getId());
        running.setStatus(PracticeStatus.RUNNING);
        running.setUpdatedAt(T1);
        PracticeSession savedRunning = sessions.save(running);
        cleanup.add(() -> sessions.deleteById(savedRunning.getId()));

        assertThat(sessions.findRunningSession()).get().extracting(PracticeSession::getId)
                .isEqualTo(savedRunning.getId());
        assertThat(sessions.findByEventId(event.getId())).extracting(PracticeSession::getId)
                .containsExactly(savedRunning.getId());
    }

    @Test
    void lapRoundTripAndFinders() {
        PracticeSession session = sessions.save(session());
        cleanup.add(() -> sessions.deleteById(session.getId()));

        PracticeLap saved = assertSavedAndReloaded(laps, lap(session.getId(), "T1", 2, T2), x -> {
            x.setTransponderNumber("T2");
            x.setUserId(null);
            x.setLapNumber(3);
            x.setLapTimeMs(61_000L);
            x.setCrossingTime(T2.plusSeconds(61));
            return x;
        }, PracticeLap::getId);
        cleanup.add(() -> laps.deleteById(saved.getId()));

        Instant created = saved.getCreatedAt();
        saved.setCreatedAt(created.plus(1, ChronoUnit.DAYS));
        laps.save(saved);
        assertThat(laps.findById(saved.getId()).orElseThrow().getCreatedAt())
                .as("creation time is set on insert only").isEqualTo(created);

        PracticeLap first = laps.save(lap(session.getId(), "T2", 1, T1));
        cleanup.add(() -> laps.deleteById(first.getId()));
        PracticeLap other = laps.save(lap(session.getId(), "T9", 1, T1.plusSeconds(5)));
        cleanup.add(() -> laps.deleteById(other.getId()));

        assertThat(laps.findByPracticeSessionIdOrderByCrossingTimeAsc(session.getId()))
                .extracting(PracticeLap::getId).containsExactly(first.getId(), other.getId(), saved.getId());
        assertThat(laps.findByPracticeSessionIdAndTransponderNumberOrderByLapNumberAsc(session.getId(), "T2"))
                .extracting(PracticeLap::getLapNumber).containsExactly(1, 3);
    }

    @Test
    void highestRunNumberIsZeroWithNoLapsAndThenTheHighestSaved() {
        PracticeSession session = sessions.save(session());
        cleanup.add(() -> sessions.deleteById(session.getId()));
        assertThat(laps.highestRunNumber(session.getId(), "T1")).isZero();

        PracticeLap first = lap(session.getId(), "T1", 1, T1);
        first.setRunNumber(2);
        first = laps.save(first);
        Long firstId = first.getId();
        cleanup.add(() -> laps.deleteById(firstId));

        assertThat(laps.highestRunNumber(session.getId(), "T1")).isEqualTo(2);
        assertThat(laps.highestRunNumber(session.getId(), "T2")).isZero();
    }

    @Test
    void movingACompetitorMovesTheTranspondersTheyWereNamedAs() {
        PracticeSession session = sessions.save(session());
        cleanup.add(() -> sessions.deleteById(session.getId()));
        Competitor duplicate = competitors.save(competitor("Practice Duplicate"));
        cleanup.add(() -> competitors.deleteById(duplicate.getId()));
        Competitor keep = competitors.save(competitor("Practice Keep"));
        cleanup.add(() -> competitors.deleteById(keep.getId()));

        PracticeDriver moved = drivers.save(driver(session.getId(), "T1", duplicate.getId()));
        cleanup.add(() -> drivers.deleteById(moved.getId()));
        PracticeDriver stays = drivers.save(driver(session.getId(), "T2", keep.getId()));
        cleanup.add(() -> drivers.deleteById(stays.getId()));

        drivers.moveCompetitor(duplicate.getId(), keep.getId());

        assertThat(drivers.findByPracticeSessionId(session.getId()))
                .extracting(PracticeDriver::getTransponderNumber, PracticeDriver::getCompetitorId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("T1", keep.getId()),
                        org.assertj.core.groups.Tuple.tuple("T2", keep.getId()));
    }

    private PracticeDriver driver(Long sessionId, String transponder, Long competitorId) {
        PracticeDriver d = new PracticeDriver();
        d.setPracticeSessionId(sessionId);
        d.setTransponderNumber(transponder);
        d.setCompetitorId(competitorId);
        d.setCreatedAt(T1);
        return d;
    }

    private static Competitor competitor(String name) {
        Competitor c = new Competitor();
        c.setDisplayName(name);
        return c;
    }

    private PracticeLap lap(Long sessionId, String transponder, int lapNumber, Instant crossing) {
        PracticeLap l = new PracticeLap();
        l.setPracticeSessionId(sessionId);
        l.setTransponderNumber(transponder);
        l.setUserId(official.getId());
        l.setLapNumber(lapNumber);
        l.setLapTimeMs(60_000L);
        l.setCrossingTime(crossing);
        l.setCreatedAt(T1);
        return l;
    }

    private static PracticeSession session() {
        PracticeSession s = new PracticeSession();
        s.setName("Round trip practice");
        s.setCreatedAt(T1);
        s.setUpdatedAt(T1);
        return s;
    }

    private static Event event() {
        Event e = new Event();
        e.setName("Practice event");
        e.setEventDate(LocalDate.of(2026, 10, 5));
        e.setCreatedAt(T1);
        e.setUpdatedAt(T1);
        return e;
    }

    private static User official() {
        User u = new User();
        u.setEmail("practice-official-" + System.nanoTime() + "@example.com");
        u.setPasswordHash("x");
        u.setFirstName("Practice");
        u.setLastName("Official");
        u.setRoles(Set.of(Role.RACE_DIRECTOR));
        u.setCreatedAt(T1);
        u.setUpdatedAt(T1);
        return u;
    }
}
