package dev.monkeypatch.rctiming.practice;

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
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final UserRepository userRepository;
    private final PracticeTimingHub timingHub;

    /** Active practice session states keyed by sessionId. */
    private final Map<Long, LivePracticeState> activeStates = new ConcurrentHashMap<>();

    public PracticeTimingService(PracticeSessionRepository sessionRepository,
                                 PracticeLapRepository lapRepository,
                                 EntryRepository entryRepository,
                                 CompetitorRepository competitorRepository,
                                 UserRepository userRepository,
                                 PracticeTimingHub timingHub) {
        this.sessionRepository = sessionRepository;
        this.lapRepository = lapRepository;
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
        LivePracticeState state = new LivePracticeState(session.getBestLapN());
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

        Long lapTimeMs = state.lapTimeSincePrevious(transponderNumber, event.rtcTimeMicros());

        // Resolve transponder → competitor through the session's event entries (L10, #18).
        // A session with no event, or a transponder no entry uses, stays unknown.
        String racerName = resolveCompetitorName(session, transponderNumber);

        // Record in in-memory state
        state.recordLap(transponderNumber, null, racerName, lapTimeMs);

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
     * Get current timing snapshot.
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
     * Link an unknown transponder to a user in an active session.
     * Retroactively updates all lap rows for that transponder (in-memory only).
     */
    public void linkTransponder(Long sessionId, String transponderNumber, Long userId, String racerName) {
        LivePracticeState state = activeStates.get(sessionId);
        if (state != null) {
            state.linkTransponder(transponderNumber, userId, racerName);
            timingHub.broadcastTimingUpdate(sessionId, state.calculatePositions());
        }
    }

    private List<PracticeTimingRowDto> buildSnapshotFromDb(Long sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return Collections.emptyList();
        }

        LivePracticeState state = new LivePracticeState(session.getBestLapN());
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
                    lap.getLapTimeMs()
            );
        }

        return state.calculatePositions();
    }

    /** The competitor whose active entry in the session's event uses this transponder, if any. */
    private String resolveCompetitorName(PracticeSession session, String transponderNumber) {
        if (session.getEventId() == null) {
            return null;
        }
        return entryRepository.findByEventId(session.getEventId()).stream()
                .filter(e -> e.getStatus() != EntryStatus.WITHDRAWN)
                .filter(e -> transponderNumber.equals(e.getTransponderNumberSnapshot())
                        || transponderNumber.equals(e.getSecondaryTransponderNumber()))
                .map(Entry::getCompetitorId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .flatMap(competitorRepository::findById)
                .map(Competitor::getDisplayName)
                .orElse(null);
    }
}
