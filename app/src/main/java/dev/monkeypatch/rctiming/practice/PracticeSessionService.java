package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeDriver;
import dev.monkeypatch.rctiming.domain.practice.PracticeDriverRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeSession;
import dev.monkeypatch.rctiming.domain.practice.PracticeSessionRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeStatus;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.practice.dto.PracticeSessionDto;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CRUD and state-machine service for practice sessions ({@code IDLE -> RUNNING -> STOPPED}).
 * Delegates to PracticeTimingService for live timing start/stop lifecycle. Commands run on request threads in a
 * write transaction, each with an audit row. A session stops by an official's command or when a race starts
 * ({@link #onRaceStatusChanged}), because practice and racing share the one decoder.
 */
@Service
public class PracticeSessionService {

    private static final Logger log = LoggerFactory.getLogger(PracticeSessionService.class);

    /** How far back a new session looks for transponder names to keep. */
    private static final Duration CARRY_OVER_WINDOW = Duration.ofHours(24);

    private final PracticeSessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PracticeTimingService timingService;
    private final AuditService audit;
    private final RaceRepository raceRepository;
    private final PracticeDriverRepository driverRepository;

    public PracticeSessionService(PracticeSessionRepository sessionRepository,
                                  EventRepository eventRepository,
                                  UserRepository userRepository,
                                  PracticeTimingService timingService,
                                  AuditService audit,
                                  RaceRepository raceRepository,
                                  PracticeDriverRepository driverRepository) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.timingService = timingService;
        this.audit = audit;
        this.raceRepository = raceRepository;
        this.driverRepository = driverRepository;
    }

    /**
     * Creates an idle session, optionally tied to an event. {@code bestLapN} and {@code pauseSeconds} fall back to
     * the session defaults when not given.
     *
     * @throws EntityNotFoundException (404) for an unknown event
     * @throws IllegalArgumentException (400) for a lap count or pause below 1
     */
    @Transactional
    public PracticeSessionDto create(Actor actor, CreateRequest request) {
        PracticeSession session = new PracticeSession();
        session.setName(request.name());

        if (request.eventId() != null) {
            Event event = eventRepository.findById(request.eventId())
                    .orElseThrow(() -> new EntityNotFoundException("Event not found: " + request.eventId()));
            session.setEventId(event.getId());
        }

        if (request.bestLapN() != null) {
            if (request.bestLapN() < 1) {
                throw new IllegalArgumentException("The number of laps to average must be at least 1");
            }
            session.setBestLapN(request.bestLapN());
        }
        if (request.pauseSeconds() != null) {
            if (request.pauseSeconds() < 1) {
                throw new IllegalArgumentException("The pause that starts a new run must be at least 1 second");
            }
            session.setPauseSeconds(request.pauseSeconds());
        }

        session.setCreatedByUserId(actor.userId());

        session = sessionRepository.save(session);
        audit.entry(actor, "PRACTICE_SESSION_CREATED").entity("practice_session", session.getId())
                .event(session.getEventId())
                .summary("Created the practice session " + session.getName())
                .after(sessionValues(session)).record();
        return toDto(session);
    }

    public Optional<PracticeSessionDto> findById(Long id) {
        return sessionRepository.findById(id).map(this::toDto);
    }

    /** The newest sessions, by creation time, up to {@code limit}. */
    public List<PracticeSessionDto> findRecent(int limit) {
        return sessionRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .map(this::toDto)
                .toList();
    }

    /**
     * Starts an idle session and begins live timing for it.
     *
     * @throws StateConflictException (409) if the session is not idle, or a race is running
     */
    @Transactional
    public PracticeSessionDto start(Actor actor, Long sessionId) {
        PracticeSession session = sessionRepository.getOrThrow(sessionId);

        if (session.getStatus() != PracticeStatus.IDLE) {
            throw new StateConflictException("Session must be IDLE to start; current: " + session.getStatus());
        }
        // Practice and a live race share the one decoder, so a passing would count for both
        if (raceRepository.findFirstByStatus(RaceStatus.RUNNING).isPresent()) {
            throw new StateConflictException(
                    "A race is running, so practice can't start until it has finished or been stopped");
        }

        session.start();
        session = sessionRepository.save(session);
        audit.entry(actor, "PRACTICE_SESSION_STARTED").entity("practice_session", sessionId)
                .event(session.getEventId())
                .summary("Started the practice session " + session.getName())
                .before(PracticeStatus.IDLE).after(session.getStatus()).record();
        carryOverDrivers(session);
        timingService.startSession(session);
        return toDto(session);
    }

    /**
     * Names are saved per session, so a new session would otherwise forget who each transponder is. Copies the
     * newest name given to each transponder in the sessions started in the last {@link #CARRY_OVER_WINDOW} into
     * the session starting, where an official can still change it. Runs in the start transaction.
     */
    private void carryOverDrivers(PracticeSession session) {
        Instant now = Instant.now();
        Map<String, Long> latest = new LinkedHashMap<>();
        for (PracticeSession earlier : sessionRepository.findStartedBetween(now.minus(CARRY_OVER_WINDOW), now)) {
            if (earlier.getId().equals(session.getId())) {
                continue;
            }
            // Oldest session first, so a later naming replaces an earlier one
            for (PracticeDriver d : driverRepository.findByPracticeSessionId(earlier.getId())) {
                latest.put(d.getTransponderNumber(), d.getCompetitorId());
            }
        }
        latest.forEach((transponder, competitorId) -> {
            PracticeDriver driver = new PracticeDriver();
            driver.setPracticeSessionId(session.getId());
            driver.setTransponderNumber(transponder);
            driver.setCompetitorId(competitorId);
            driverRepository.save(driver);
        });
    }

    /**
     * Stops a running session and ends its live timing; the laps already saved stay.
     *
     * @throws StateConflictException (409) if the session is not running
     */
    @Transactional
    public PracticeSessionDto stop(Actor actor, Long sessionId) {
        return stop(actor, sessionId, null);
    }

    /** @param causedByRaceId the race whose start stops the session, or null when an official stopped it */
    private PracticeSessionDto stop(Actor actor, Long sessionId, Long causedByRaceId) {
        PracticeSession session = sessionRepository.getOrThrow(sessionId);

        if (session.getStatus() != PracticeStatus.RUNNING) {
            throw new StateConflictException("Session must be RUNNING to stop; current: " + session.getStatus());
        }

        session.stop();
        session = sessionRepository.save(session);
        audit.entry(actor, "PRACTICE_SESSION_STOPPED").entity("practice_session", sessionId)
                .event(session.getEventId()).race(causedByRaceId)
                .summary("Stopped the practice session " + session.getName()
                        + (causedByRaceId == null ? "" : " because race " + causedByRaceId + " started"))
                .before(PracticeStatus.RUNNING).after(session.getStatus()).record();
        timingService.stopSession(sessionId);
        return toDto(session);
    }

    private static java.util.Map<String, Object> sessionValues(PracticeSession s) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", s.getName());
        m.put("eventId", s.getEventId());
        m.put("bestLapN", s.getBestLapN());
        m.put("pauseSeconds", s.getPauseSeconds());
        return m;
    }

    /**
     * A race starting or resuming takes the track over from practice: every running practice session is stopped,
     * so the race's passings are not counted as practice laps too. It runs in the race command's own transaction
     * and is not caught: if a session can't be stopped the race command fails with it and nothing is half done.
     * (A separate transaction would wait for the single write connection the race command is holding.)
     */
    @EventListener
    @Transactional
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        if (event.getNewStatus() != RaceStatus.RUNNING) {
            return;
        }
        for (PracticeSession running : sessionRepository.findRunningSessions()) {
            stop(Actor.system("race-start"), running.getId(), event.getRaceId());
            log.info("Race {} started, so practice session {} was stopped", event.getRaceId(), running.getId());
        }
    }

    /**
     * The session as sent to the screens, with its event's name looked up. {@code raceRunning} is a snapshot taken
     * now and only looked up for an idle session, the one that can't start while a race runs (one extra read on the
     * write connection, so the list stays cheap); it is false for any other status.
     */
    public PracticeSessionDto toDto(PracticeSession session) {
        return new PracticeSessionDto(
                session.getId(),
                session.getName(),
                session.getEventId(),
                session.getEventId() != null
                        ? eventRepository.findById(session.getEventId()).map(Event::getName).orElse(null)
                        : null,
                session.getStatus(),
                session.getBestLapN(),
                session.getPauseSeconds(),
                session.getStartedAt(),
                session.getStoppedAt(),
                session.getStatus() == PracticeStatus.IDLE
                        && raceRepository.findFirstByStatus(RaceStatus.RUNNING).isPresent()
        );
    }

    /**
     * @param bestLapN     how many consecutive laps the average is taken over (default 5)
     * @param pauseSeconds how long a transponder may be quiet before its next lap starts a new run (default 180)
     */
    public record CreateRequest(@Size(max = 200) String name, Long eventId, Integer bestLapN, Integer pauseSeconds) {}
}
