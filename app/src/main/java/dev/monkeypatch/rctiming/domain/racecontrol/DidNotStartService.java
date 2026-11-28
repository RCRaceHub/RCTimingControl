package dev.monkeypatch.rctiming.domain.racecontrol;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.resultsexport.FinishedRaceCorrected;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Marks a car as not having started (DNS) in a heat, final or practice, and takes the mark back. A decoder cannot
 * tell a car that stayed in the pits from one that is merely slow, so an official records it. The heat's result lists
 * a DNS car last with no laps (see {@code DnsOrdering}), and a low-points class scores it as a driver who did not
 * run: the class's entries plus one under the BRCA rules, otherwise last place.
 *
 * <p>This is for a car that never ran. A car that went missing after it had run keeps its laps and is flagged DNF,
 * which is {@code LiveRaceState}'s job, so a car that has completed a lap is refused here. Each action runs in one
 * request transaction with an audit row. The check for laps is made on the request thread while the timing thread
 * may still credit a passing, so the result ignores the laps of a marked car whatever it was credited. A restart
 * keeps the mark, since it is a fact about the grid and not about a run. On a finished race the stored result is worked out again and its results
 * are sent again, through {@link FinishedRaceCorrected}.
 */
@Service
@Transactional
public class DidNotStartService {

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final ResultSnapshotJson snapshotJson;
    private final LapTimingService lapTimingService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService audit;
    private final RaceAuditLabels labels;

    public DidNotStartService(RaceRepository raceRepository, RaceEntryRepository raceEntryRepository,
                              ResultSnapshotRepository resultSnapshotRepository, ResultSnapshotJson snapshotJson,
                              LapTimingService lapTimingService, ApplicationEventPublisher eventPublisher,
                              AuditService audit, RaceAuditLabels labels) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.snapshotJson = snapshotJson;
        this.lapTimingService = lapTimingService;
        this.eventPublisher = eventPublisher;
        this.audit = audit;
        this.labels = labels;
    }

    /**
     * Marks the car as not started.
     *
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if the race does not exist
     * @throws StateConflictException (409) if the entry is not on the race's grid, has completed a lap, or is
     *                                already marked
     */
    public void markDidNotStart(long raceId, long entryId, long userId) {
        Race race = raceRepository.getOrThrow(raceId);
        RaceEntry raceEntry = requireOnGrid(raceId, entryId);
        if (raceEntry.isDns()) {
            throw new StateConflictException("That car is already marked did not start");
        }
        if (lapsCompleted(race, entryId) > 0) {
            throw new StateConflictException("That car has completed laps, so it started");
        }
        raceEntry.setDns(true);
        raceEntryRepository.save(raceEntry);
        audit.entry(Actor.official(userId), "ENTRY_DID_NOT_START").entity("race_entry", raceEntry.getId())
                .race(raceId).event(labels.eventOf(raceId))
                .summary("Marked " + labels.driver(entryId) + " as did not start in " + labels.race(raceId))
                .after(Map.of("entryId", entryId)).record();
        resultChanged(race);
    }

    /**
     * Takes the mark back, for a car marked by mistake or one that turned up after all.
     *
     * @throws StateConflictException (409) if the entry is not on the grid or is not marked
     */
    public void undoDidNotStart(long raceId, long entryId, long userId) {
        Race race = raceRepository.getOrThrow(raceId);
        RaceEntry raceEntry = requireOnGrid(raceId, entryId);
        if (!raceEntry.isDns()) {
            throw new StateConflictException("That car is not marked did not start");
        }
        raceEntry.setDns(false);
        raceEntryRepository.save(raceEntry);
        audit.entry(Actor.official(userId), "ENTRY_DID_NOT_START_UNDONE").entity("race_entry", raceEntry.getId())
                .race(raceId).event(labels.eventOf(raceId))
                .summary("Took back did not start for " + labels.driver(entryId) + " in " + labels.race(raceId))
                .before(Map.of("entryId", entryId)).record();
        resultChanged(race);
    }

    /** Entry ids of the cars marked as not started in the race. 404 if the race does not exist. */
    @ReadTransaction
    public List<Long> list(long raceId) {
        raceRepository.requireExists(raceId);
        return raceEntryRepository.findByRaceIdOrderByGridPosition(raceId).stream()
                .filter(RaceEntry::isDns).map(RaceEntry::getEntryId).toList();
    }

    private RaceEntry requireOnGrid(long raceId, long entryId) {
        return raceEntryRepository.findByRaceIdAndEntryId(raceId, entryId)
                .orElseThrow(() -> new StateConflictException("That entry is not in this race"));
    }

    /**
     * Laps the car has completed as timed: live while the race is on, otherwise from the stored result as timed (a
     * race that has not started has none).
     */
    private int lapsCompleted(Race race, long entryId) {
        if (race.getStatus() == RaceStatus.RUNNING || race.getStatus() == RaceStatus.STOPPED) {
            return lapTimingService.peek(race.getId())
                    .map(state -> state.calculateTimedPositions().stream()
                            .filter(r -> r.entryId() == entryId).mapToInt(LiveTimingRowDto::lapsCompleted).max().orElse(0))
                    .orElse(0);
        }
        return resultSnapshotRepository.findByRaceId(race.getId())
                .map(snapshot -> snapshotJson.positions(race.getId(), snapshot.getTimedPositionsJson() != null
                                ? snapshot.getTimedPositionsJson() : snapshot.getPositionsJson()).stream()
                        .filter(r -> r.entryId() == entryId).mapToInt(ResultSnapshotDto.ResultRow::lapsCompleted)
                        .max().orElse(0))
                .orElse(0);
    }

    /** A finished race has its result and results export built again; any other reads the flag when it ends. */
    private void resultChanged(Race race) {
        if (race.getStatus() == RaceStatus.FINISHED) {
            eventPublisher.publishEvent(new FinishedRaceCorrected(race.getId()));
        }
    }
}
