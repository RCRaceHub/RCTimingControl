package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.query.boards.BoardQuery;
import dev.monkeypatch.rctiming.query.boards.RaceHighlights;
import dev.monkeypatch.rctiming.query.boards.RaceHighlightsDto;
import dev.monkeypatch.rctiming.query.racecontrol.ResultPointsCache;
import dev.monkeypatch.rctiming.query.racecontrol.ResultSnapshotQuery;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gathers the runs behind the overlay ticker's standouts (most places gained and lost, best three and five lap
 * average) for a heat and for its round. A heat on track is read from its live state; a finished one from its stored
 * result and grid. Names are applied as the public may see them, since the overlay is anonymous. Called per poll from
 * {@code BoardController} on a Tomcat thread; it changes nothing, each query is its own read snapshot, and the live
 * state is copied under its own lock.
 */
@Service
public class RaceHighlightsService {

    private final BoardQuery boardQuery;
    private final LapTimingService lapTimingService;
    private final ResultSnapshotQuery resultSnapshotQuery;
    private final PublicNames publicNames;

    public RaceHighlightsService(BoardQuery boardQuery, LapTimingService lapTimingService,
                                 ResultSnapshotQuery resultSnapshotQuery, PublicNames publicNames) {
        this.boardQuery = boardQuery;
        this.lapTimingService = lapTimingService;
        this.resultSnapshotQuery = resultSnapshotQuery;
        this.publicNames = publicNames;
    }

    /**
     * The standouts of a heat and of its round.
     *
     * @param raceId a race; its round is every other finished heat of the same round and class plus this heat
     * @return the heat's marks and the round's; a mark is null when nobody earned it
     */
    public RaceHighlightsDto forRace(long raceId) {
        // One cache for the request, so the class's standings behind each heat's points are worked out once
        ResultPointsCache cache = new ResultPointsCache();
        List<RaceHighlights.Run> heat = liveRuns(raceId).orElseGet(() -> finishedRuns(raceId, cache));
        List<RaceHighlights.Run> round = new ArrayList<>(heat);
        for (long finishedId : boardQuery.finishedRaceIdsInRound(raceId)) {
            round.addAll(finishedRuns(finishedId, cache));
        }
        return new RaceHighlightsDto(RaceHighlights.of(heat), RaceHighlights.of(round));
    }

    /** The runs of a heat on track, in running order; empty when it has no live state. */
    private Optional<List<RaceHighlights.Run>> liveRuns(long raceId) {
        Optional<LiveRaceState> state = lapTimingService.peek(raceId);
        if (state.isEmpty()) {
            return Optional.empty();
        }
        List<LiveTimingRowDto> rows = state.get().calculatePositions();
        List<Long> entryIds = rows.stream().map(LiveTimingRowDto::entryId).toList();
        Map<Long, Long> competitors = boardQuery.competitorIds(entryIds);
        PublicNames.Snapshot names = publicNames.snapshot();
        List<RaceHighlights.Run> runs = new ArrayList<>();
        for (LiveTimingRowDto row : rows) {
            String name = names.of(competitors.get(row.entryId()), row.driverName(), row.carNumber());
            runs.add(new RaceHighlights.Run(name, row.startPosition(), row.position(),
                    state.get().lapTimesSnapshot(row.entryId())));
        }
        return Optional.of(runs);
    }

    /** The runs of a finished heat from its stored result, in finishing order; empty when it has none yet. */
    private List<RaceHighlights.Run> finishedRuns(long raceId, ResultPointsCache cache) {
        ResultSnapshotDto snapshot;
        try {
            snapshot = resultSnapshotQuery.load(raceId, cache);
        } catch (EntityNotFoundException e) {
            // Finished but not yet snapshotted
            return List.of();
        }
        Map<Long, Integer> grid = boardQuery.gridPositions(raceId);
        Map<Long, List<ResultSnapshotDto.PositionAtLap>> laps = new HashMap<>();
        for (ResultSnapshotDto.PositionAtLap lap : snapshot.lapHistory()) {
            laps.computeIfAbsent(lap.entryId(), k -> new ArrayList<>()).add(lap);
        }
        List<RaceHighlights.Run> runs = new ArrayList<>();
        for (ResultSnapshotDto.ResultRow row : publicNames.rows(snapshot.positions())) {
            List<Long> lapTimes = laps.getOrDefault(row.entryId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(ResultSnapshotDto.PositionAtLap::lapNumber))
                    .map(ResultSnapshotDto.PositionAtLap::lapTimeMs)
                    .toList();
            runs.add(new RaceHighlights.Run(row.driverName(), grid.get(row.entryId()), row.position(), lapTimes));
        }
        return runs;
    }
}
