package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.domain.practice.PracticeDriver;
import dev.monkeypatch.rctiming.domain.practice.PracticeDriverRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeLap;
import dev.monkeypatch.rctiming.domain.practice.PracticeLapRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeSession;
import dev.monkeypatch.rctiming.domain.practice.PracticeSessionRepository;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.practice.dto.PracticeTimingRowDto;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Practice session lap processing service.
 *
 * Unlike race timing, each practice lap is saved as it happens: the live table is built in memory
 * ({@link LivePracticeState}) and the lap row is written in the same transaction.
 *
 * Listens to the same LapPassingEvent as LapTimingService, but only acts when a practice session is
 * RUNNING. Practice is meant for the time between races: LapTimingService ignores a passing with no running
 * race ({@link LapPassingEvent#NO_RACE}), so then only practice counts it. A running race stops practice
 * and blocks it starting (PRACTICE-03), and a passing made during a race is ignored here as well, so a
 * practice session left running by a restart can't count the race's laps.
 *
 * Runs on the single timing thread, like LapTimingService, so passings arrive in decoder order. Lap time
 * is the difference between a transponder's consecutive rtcTimeMicros (UTC epoch microseconds, as in
 * LiveRaceState). CrossingTime uses Instant.now() (the time the server handled the passing).
 */
@Service
public class PracticeTimingService {

    private static final Logger log = LoggerFactory.getLogger(PracticeTimingService.class);

    private final PracticeSessionRepository sessionRepository;
    private final PracticeLapRepository lapRepository;
    private final PracticeDriverRepository driverRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final UserRepository userRepository;
    private final PracticeTimingHub timingHub;

    /** Active practice session states keyed by sessionId. */
    private final Map<Long, LivePracticeState> activeStates = new ConcurrentHashMap<>();

    public PracticeTimingService(PracticeSessionRepository sessionRepository,
                                 PracticeLapRepository lapRepository,
                                 PracticeDriverRepository driverRepository,
                                 EntryRepository entryRepository,
                                 CompetitorRepository competitorRepository,
                                 UserRepository userRepository,
                                 PracticeTimingHub timingHub) {
        this.sessionRepository = sessionRepository;
        this.lapRepository = lapRepository;
        this.driverRepository = driverRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.userRepository = userRepository;
        this.timingHub = timingHub;
    }

    /**
     * Begin tracking a newly started practice session.
     * Called from PracticeSessionService.start() after the session is persisted.
     */
    public void startSession(PracticeSession session) {
        LivePracticeState state = new LivePracticeState(session.getBestLapN(), session.getPauseSeconds());
        activeStates.put(session.getId(), state);
        log.info("Practice session {} ({}) started timing", session.getId(), session.getName());
    }

    /**
     * Stop tracking a practice session.
     * Called from PracticeSessionService.stop() after the session is persisted.
     */
    public void stopSession(Long sessionId) {
        activeStates.remove(sessionId);
        log.info("Practice session {} stopped timing", sessionId);
    }

    /**
     * Handle LapPassingEvent. Processes only when a practice session is RUNNING and no race is: a passing
     * with a race id belongs to the race and LapTimingService.
     *
     * <p>Runs on the timing thread, in its own write transaction per passing. The lap row is saved and the table
     * broadcast inside it. A failure rolls that passing's lap back and, being an event listener, reaches
     * {@code DecoderListener}, which logs it and carries on.
     */
    @EventListener
    @Transactional
    public void onLapPassing(LapPassingEvent event) {
        if (event.raceId() != LapPassingEvent.NO_RACE) {
            return;
        }
        // Check for a running practice session
        PracticeSession session = sessionRepository.findRunningSession().orElse(null);
        if (session == null) {
            return;
        }

        LivePracticeState state = activeStates.get(session.getId());
        if (state == null) {
            // Race condition: session found as RUNNING in DB but not yet tracked locally
            // (e.g. server restart). Re-initialise gracefully.
            startSession(session);
            state = activeStates.get(session.getId());
        }

        String transponderNumber = event.transponderNumber();
        Instant crossingTime = Instant.now();

        LivePracticeState.Passing passing = state.notePassing(transponderNumber, event.rtcTimeMicros(),
                () -> lapRepository.highestRunNumber(session.getId(), transponderNumber));
        Long lapTimeMs = passing.lapTimeMs();

        // Resolve transponder → competitor: named in this session first, then through the session's event entries
        // (L10, #18). A transponder neither names stays unknown.
        String racerName = state.knownName(transponderNumber);
        if (racerName == null) {
            racerName = resolveCompetitorName(session, transponderNumber);
        }

        // Record in in-memory state
        state.recordLap(transponderNumber, null, racerName, lapTimeMs, passing.runNumber());

        // Persist lap record (only when we have a real lap time)
        if (lapTimeMs != null) {
            // Asks the database once per transponder, for laps saved before a restart; after that it counts.
            // The highest saved number, not the row count: the numbers need not be unbroken
            int lapNumber = state.nextLapNumber(transponderNumber, () -> lapRepository
                    .findByPracticeSessionIdAndTransponderNumberOrderByLapNumberAsc(
                            session.getId(), transponderNumber).stream()
                    .map(PracticeLap::getLapNumber)
                    .filter(java.util.Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0));

            PracticeLap lap = new PracticeLap();
            lap.setPracticeSessionId(session.getId());
            lap.setTransponderNumber(transponderNumber);
            lap.setLapNumber(lapNumber);
            lap.setRunNumber(passing.runNumber());
            lap.setLapTimeMs(lapTimeMs);
            lap.setCrossingTime(crossingTime);
            lapRepository.save(lap);
        }

        // Broadcast positions
        List<PracticeTimingRowDto> rows = state.calculatePositions();
        timingHub.broadcastTimingUpdate(session.getId(), rows);

        // Broadcast unknown transponders if any
        Set<String> unknown = state.getUnknownTransponders();
        if (!unknown.isEmpty()) {
            timingHub.broadcastUnknownTransponders(session.getId(), unknown);
        }
    }

    /**
     * Get current timing snapshot. Called from a request thread when a screen loads, before the live broadcasts
     * take over.
     * Returns in-memory state if session is active, else rebuilds from DB (for stopped sessions).
     */
    public List<PracticeTimingRowDto> getSnapshot(Long sessionId) {
        LivePracticeState state = activeStates.get(sessionId);
        if (state != null) {
            return state.calculatePositions();
        }
        // Session not active — build from persisted laps
        return buildSnapshotFromDb(sessionId);
    }

    /**
     * Shows a name against a transponder in a running session, after {@link PracticeDriverService} has saved it.
     * Laps already counted take the name too; a stopped session reads it from the saved match.
     */
    public void nameTransponder(Long sessionId, String transponderNumber, String racerName) {
        LivePracticeState state = activeStates.get(sessionId);
        if (state != null) {
            state.linkTransponder(transponderNumber, null, racerName);
            timingHub.broadcastTimingUpdate(sessionId, state.calculatePositions());
        }
    }

    private List<PracticeTimingRowDto> buildSnapshotFromDb(Long sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return Collections.emptyList();
        }

        LivePracticeState state = new LivePracticeState(session.getBestLapN(), session.getPauseSeconds());
        List<PracticeLap> laps = lapRepository.findByPracticeSessionIdOrderByCrossingTimeAsc(sessionId);

        // Laps recorded before L10 carry a user; later ones are named through the event's entries
        Map<String, Optional<String>> competitorNames = new HashMap<>();
        Map<Long, Optional<String>> userNames = new HashMap<>();
        for (PracticeLap lap : laps) {
            Long userId = lap.getUserId();
            String racerName = userId != null
                    ? userNames.computeIfAbsent(userId, id -> userRepository.findById(id)
                            .map(u -> u.getFirstName() + " " + u.getLastName())).orElse(null)
                    : competitorNames.computeIfAbsent(lap.getTransponderNumber(),
                            t -> Optional.ofNullable(resolveCompetitorName(session, t))).orElse(null);
            state.recordLap(
                    lap.getTransponderNumber(),
                    userId,
                    racerName,
                    lap.getLapTimeMs(),
                    lap.getRunNumber() == null ? 1 : lap.getRunNumber()
            );
        }

        return state.calculatePositions();
    }

    /**
     * The competitor this transponder was named as in the session, else the one whose active entry in the session's
     * event uses it, if any.
     */
    private String resolveCompetitorName(PracticeSession session, String transponderNumber) {
        Optional<String> named = driverRepository
                .findByPracticeSessionIdAndTransponderNumber(session.getId(), transponderNumber)
                .map(PracticeDriver::getCompetitorId)
                .flatMap(competitorRepository::findById)
                .map(Competitor::getDisplayName);
        if (named.isPresent()) {
            return named.get();
        }
        if (session.getEventId() == null) {
            return null;
        }
        return entryRepository.findByEventId(session.getEventId()).stream()
                .filter(e -> e.getStatus() != EntryStatus.WITHDRAWN)
                .filter(e -> e.hasTransponder(transponderNumber))
                .map(Entry::getCompetitorId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .flatMap(competitorRepository::findById)
                .map(Competitor::getDisplayName)
                .orElse(null);
    }
}
