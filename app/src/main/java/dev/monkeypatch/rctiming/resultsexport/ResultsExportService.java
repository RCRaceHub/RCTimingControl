package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.resultsexport.ResultsExportQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Builds results exports and queues them for RaceHub (#27) when the club publishes there (#319). Only events
 * whose entries came from a RaceHub import are queued, since RaceHub needs its own event id to place them; any
 * event can be downloaded.
 */
@Service
public class ResultsExportService {

    private static final Logger log = LoggerFactory.getLogger(ResultsExportService.class);

    private final EventRepository eventRepository;
    private final RaceRepository raceRepository;
    private final RoundRepository roundRepository;
    private final ResultsOutboxRepository outboxRepository;
    private final ResultsExportQuery exportQuery;
    private final ObjectMapper objectMapper;

    private final AuditService audit;
    private final ResultsPublishSettings publishSettings;
    private final Clock clock;

    @Autowired
    public ResultsExportService(EventRepository eventRepository, RaceRepository raceRepository,
                                RoundRepository roundRepository, ResultsOutboxRepository outboxRepository,
                                ResultsExportQuery exportQuery, ObjectMapper objectMapper, AuditService audit,
                                ResultsPublishSettings publishSettings) {
        this(eventRepository, raceRepository, roundRepository, outboxRepository, exportQuery, objectMapper, audit,
             publishSettings, Clock.systemUTC());
    }

    ResultsExportService(EventRepository eventRepository, RaceRepository raceRepository,
                         RoundRepository roundRepository, ResultsOutboxRepository outboxRepository,
                         ResultsExportQuery exportQuery, ObjectMapper objectMapper, AuditService audit,
                         ResultsPublishSettings publishSettings, Clock clock) {
        this.eventRepository = eventRepository;
        this.raceRepository = raceRepository;
        this.roundRepository = roundRepository;
        this.outboxRepository = outboxRepository;
        this.exportQuery = exportQuery;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.publishSettings = publishSettings;
        this.clock = clock;
    }

    /**
     * Marks the event a race belongs to as needing an export, in the caller's transaction, so the request is
     * saved with the finish or correction that made it. {@link #queuePending} builds the export afterwards.
     *
     * <p>{@code noRollbackFor} is what keeps a failure here from rolling back race control's change. It joins
     * the caller's transaction, so without it an exception thrown out of this method would mark that
     * transaction rollback-only and the finish or correction would be lost over a missed export.
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public void requestForRace(long raceId, ExportReason reason) {
        raceRepository.findById(raceId)
                .flatMap(race -> roundRepository.findById(race.getRoundId()))
                .ifPresentOrElse(round -> request(round.getEventId(), reason),
                        () -> log.warn("No event found for race {}; no results export requested", raceId));
    }

    /**
     * Marks an event as needing an export, in the caller's transaction. Events not from RaceHub are skipped,
     * and so is everything when the club's publishing choice (#319) leaves RaceHub out.
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public void request(long eventId, ExportReason reason) {
        if (!publishSettings.effective().includesRaceHub()) {
            log.debug("Results are not published to RaceHub; export of event {} not requested", eventId);
            return;
        }
        eventRepository.findById(eventId)
                .filter(event -> event.getRacehubEventId() != null)
                .ifPresent(event -> {
                    event.setResultsExportPending(reason.name());
                    eventRepository.save(event);
                });
    }

    /** Events with an export requested but not queued yet. */
    @ReadTransaction
    public List<Long> pendingEventIds() {
        return eventRepository.findIdsWithResultsExportPending();
    }

    /**
     * Queues the export an event is waiting for, and clears its request in the same transaction. If building
     * the export fails, the request stays and is tried again.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ResultsOutboxItem> queuePending(long eventId) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
        String pending = event.getResultsExportPending();
        if (pending == null) {
            return Optional.empty();
        }
        event.setResultsExportPending(null);
        eventRepository.save(event);
        return queue(event, ExportReason.valueOf(pending));
    }

    /**
     * Builds the event's next export and queues it, replacing any of its exports that have not been sent yet:
     * each export carries the whole event, so only the newest needs to go.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ResultsOutboxItem> enqueue(long eventId, ExportReason reason) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
        return queue(event, reason);
    }

    private Optional<ResultsOutboxItem> queue(Event event, ExportReason reason) {
        long eventId = event.getId();
        if (event.getRacehubEventId() == null) {
            log.debug("Event {} has no RaceHub event; results export not queued", eventId);
            return Optional.empty();
        }
        Instant now = clock.instant();
        long revision = event.nextResultsExportRevision();
        eventRepository.save(event);

        outboxRepository.supersedeWaiting(eventId);

        ResultsOutboxItem item = new ResultsOutboxItem();
        item.setEventId(eventId);
        item.setRevision(revision);
        item.setReason(reason);
        item.setPayload(toJson(exportQuery.build(eventId, revision, now)));
        item.setStatus(OutboxStatus.QUEUED);
        item.setNextAttemptAt(now);
        ResultsOutboxItem saved = outboxRepository.save(item);
        log.info("Queued results export revision {} of event {} ({})", revision, eventId, reason);
        return Optional.of(saved);
    }

    /** The event's results as they stand now, for download. Carries the last revision handed out; queues nothing. */
    @ReadTransaction
    public ResultsExportV1 current(long eventId) {
        Event event = eventRepository.getOrThrow(eventId);
        return exportQuery.build(eventId, event.getResultsExportRevision(), clock.instant());
    }

    /** Sends a queued or failed export on the sender's next pass instead of waiting for its retry time. */
    @Transactional
    public ResultsOutboxItem retryNow(Actor actor, long itemId) {
        ResultsOutboxItem item = outboxRepository.getOrThrow(itemId);
        if (outboxRepository.makeDue(itemId, clock.instant()) == 0) {
            return item;
        }
        audit.entry(actor, "RESULTS_EXPORT_RETRIED").entity("results_export", itemId).event(item.getEventId())
                .summary("Asked for results export " + itemId + " (revision " + item.getRevision()
                        + ") to be sent again now")
                .before(item.getStatus()).after("QUEUED").record();
        return outboxRepository.findById(itemId).orElseThrow();
    }

    private String toJson(ResultsExportV1 export) {
        try {
            return objectMapper.writeValueAsString(export);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not write the results export", e);
        }
    }
}
