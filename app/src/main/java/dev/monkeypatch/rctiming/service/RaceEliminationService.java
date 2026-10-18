package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.ShikabaneConfig;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceElimination;
import dev.monkeypatch.rctiming.domain.race.RaceEliminationRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.resultsexport.FinishedRaceCorrected;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Eliminations and zombie revivals in a Shikabane race. A decoder cannot tell a dead car from a slow one, so race
 * control marks each death and each revival by hand; the order of the deaths sets the heat result (see
 * {@link ShikabaneOrdering}). Each action is saved with an audit row in the same transaction, on a request thread.
 *
 * <p>Allowed while the race is running, stopped or finished, since a death is often noticed late. On a finished race
 * the stored result is worked out again and its results are sent again, through {@link FinishedRaceCorrected}.
 */
@Service
@Transactional
public class RaceEliminationService {

    private static final Set<RaceStatus> MARKABLE = Set.of(RaceStatus.RUNNING, RaceStatus.STOPPED, RaceStatus.FINISHED);

    private final RaceEliminationRepository eliminationRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EventClassRepository eventClassRepository;
    private final RaceFormatService raceFormatService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService audit;
    private final RaceAuditLabels labels;

    public RaceEliminationService(RaceEliminationRepository eliminationRepository,
                                  RaceRepository raceRepository,
                                  RaceEntryRepository raceEntryRepository,
                                  EventClassRepository eventClassRepository,
                                  RaceFormatService raceFormatService,
                                  ApplicationEventPublisher eventPublisher,
                                  AuditService audit,
                                  RaceAuditLabels labels) {
        this.eliminationRepository = eliminationRepository;
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.eventClassRepository = eventClassRepository;
        this.raceFormatService = raceFormatService;
        this.eventPublisher = eventPublisher;
        this.audit = audit;
        this.labels = labels;
    }

    /**
     * Marks a car dead. It takes the next place in the elimination order.
     *
     * @throws StateConflictException (409) if the race is not a Shikabane race, is not running, stopped or finished,
     *                                the entry is not on its grid, or the car is already dead
     */
    public RaceElimination eliminate(long raceId, long entryId, long userId) {
        Race race = requireMarkable(raceId, entryId);
        if (eliminationRepository.findActive(raceId, entryId).isPresent()) {
            throw new StateConflictException("That car is already marked eliminated");
        }
        RaceElimination elimination = new RaceElimination();
        elimination.setRaceId(raceId);
        elimination.setEntryId(entryId);
        elimination.setEliminatedAt(Instant.now());
        elimination.setEliminatedBy(userId);
        RaceElimination saved = eliminationRepository.save(elimination);
        audit.entry(Actor.official(userId), "ENTRY_ELIMINATED").entity("race_elimination", saved.getId())
                .race(raceId).event(labels.eventOf(raceId))
                .summary("Marked " + labels.driver(entryId) + " eliminated in " + labels.race(raceId))
                .after(Map.of("entryId", entryId)).record();
        resultChanged(race);
        return saved;
    }

    /**
     * Revives a dead car (a zombie): another car flipped it back onto its wheels. The revival is kept for the bonus
     * point, and if the car dies again it goes to the back of the elimination order.
     *
     * @throws StateConflictException (409) as for {@link #eliminate}, or if the car is not marked eliminated
     */
    public RaceElimination revive(long raceId, long entryId, long userId) {
        Race race = requireMarkable(raceId, entryId);
        RaceElimination elimination = eliminationRepository.findActive(raceId, entryId)
                .orElseThrow(() -> new StateConflictException("That car is not marked eliminated"));
        elimination.setRevivedAt(Instant.now());
        elimination.setRevivedBy(userId);
        RaceElimination saved = eliminationRepository.save(elimination);
        audit.entry(Actor.official(userId), "ENTRY_REVIVED").entity("race_elimination", saved.getId())
                .race(raceId).event(labels.eventOf(raceId))
                .summary("Revived " + labels.driver(entryId) + " as a zombie in " + labels.race(raceId))
                .after(Map.of("entryId", entryId)).record();
        resultChanged(race);
        return saved;
    }

    /**
     * Takes back a mark made by mistake: the car's current elimination is deleted, with no revival recorded.
     *
     * @throws StateConflictException (409) as for {@link #revive}
     */
    public void undoElimination(long raceId, long entryId, long userId) {
        Race race = requireMarkable(raceId, entryId);
        RaceElimination elimination = eliminationRepository.findActive(raceId, entryId)
                .orElseThrow(() -> new StateConflictException("That car is not marked eliminated"));
        eliminationRepository.delete(elimination);
        audit.entry(Actor.official(userId), "ENTRY_ELIMINATION_UNDONE").entity("race_elimination", elimination.getId())
                .race(raceId).event(labels.eventOf(raceId))
                .summary("Took back the elimination of " + labels.driver(entryId) + " in " + labels.race(raceId))
                .before(Map.of("entryId", entryId)).record();
        resultChanged(race);
    }

    /** The race's rows, eliminations and revivals, oldest first. */
    @Transactional(readOnly = true)
    public List<RaceElimination> list(long raceId) {
        raceRepository.requireExists(raceId);
        return eliminationRepository.findByRaceId(raceId);
    }

    /** Entry ids of the cars dead now, first to die first. Used when a heat's result is worked out. */
    @Transactional(readOnly = true)
    public List<Long> deadInOrder(long raceId) {
        return eliminationRepository.findByRaceId(raceId).stream()
                .filter(RaceElimination::isActive)
                .map(RaceElimination::getEntryId)
                .toList();
    }

    private Race requireMarkable(long raceId, long entryId) {
        Race race = raceRepository.getOrThrow(raceId);
        EventClass eventClass = eventClassRepository.findById(race.getEventClassId()).orElse(null);
        if (eventClass == null || eventClass.getConfigSnapshot() == null
                || !(raceFormatService.getEffectiveConfig(eventClass) instanceof ShikabaneConfig)) {
            throw new StateConflictException("Eliminations are only recorded in a Shikabane race");
        }
        if (!MARKABLE.contains(race.getStatus())) {
            throw new StateConflictException("Eliminations are recorded once the race has started");
        }
        if (raceEntryRepository.findByRaceIdAndEntryId(raceId, entryId).isEmpty()) {
            throw new StateConflictException("That entry is not in this race");
        }
        return race;
    }

    /** A finished race has its result and results export built again; a running one reads the rows when it ends. */
    private void resultChanged(Race race) {
        if (race.getStatus() == RaceStatus.FINISHED) {
            eventPublisher.publishEvent(new FinishedRaceCorrected(race.getId()));
        }
    }
}
