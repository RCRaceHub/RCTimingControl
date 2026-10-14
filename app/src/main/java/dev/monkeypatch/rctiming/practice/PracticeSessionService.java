package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeSession;
import dev.monkeypatch.rctiming.domain.practice.PracticeSessionRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeStatus;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.practice.dto.PracticeSessionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * CRUD and state-machine service for practice sessions.
 * Delegates to PracticeTimingService for live timing start/stop lifecycle.
 */
@Service
public class PracticeSessionService {

    private static final Logger log = LoggerFactory.getLogger(PracticeSessionService.class);

    private final PracticeSessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PracticeTimingService timingService;
    private final AuditService audit;
    private final RaceRepository raceRepository;

    public PracticeSessionService(PracticeSessionRepository sessionRepository,
                                  EventRepository eventRepository,
                                  UserRepository userRepository,
                                  PracticeTimingService timingService,
                                  AuditService audit,
                                  RaceRepository raceRepository) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.timingService = timingService;
        this.audit = audit;
        this.raceRepository = raceRepository;
    }

    @Transactional
    public PracticeSessionDto create(Actor actor, CreateRequest request) {
        PracticeSession session = new PracticeSession();
        session.setName(request.name());

        if (request.eventId() != null) {
            Event event = eventRepository.findById(request.eventId())
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + request.eventId()));
            session.setEventId(event.getId());
        }

        if (request.bestLapN() != null) {
            session.setBestLapN(request.bestLapN());
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

    public List<PracticeSessionDto> findRecent(int limit) {
        return sessionRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .map(this::toDto)
                .toList();
    }

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
        timingService.startSession(session);
        return toDto(session);
    }

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

    private PracticeSessionDto toDto(PracticeSession session) {
        return new PracticeSessionDto(
                session.getId(),
                session.getName(),
                session.getEventId(),
                session.getEventId() != null
                        ? eventRepository.findById(session.getEventId()).map(Event::getName).orElse(null)
                        : null,
                session.getStatus(),
                session.getBestLapN(),
                session.getStartedAt(),
                session.getStoppedAt()
        );
    }

    public record CreateRequest(String name, Long eventId, Integer bestLapN) {}
}
