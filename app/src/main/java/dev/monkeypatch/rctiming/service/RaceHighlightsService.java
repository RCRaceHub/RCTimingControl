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
import java.util.Objects;
import java.util.Optional;

/**
 * Gathers the runs behind the overlay ticker's standouts (most places gained and lost, best three and five lap
 * average, flying lap, most consistent, most improved) for a heat and for its round. A heat on track is read from its live state; a finished one from its stored
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
        // One cache for the request, so the class's standings behind each heat's points are worked out once, and
        // one map of loaded results, so a heat that is the previous heat of many drivers is read once
        ResultPointsCache cache = new ResultPointsCache();
        Map<Long, Map<Long, List<Long>>> loaded = new HashMap<>();
        List<RaceHighlights.Run> heat = liveRuns(raceId, cache, loaded)
                .orElseGet(() -> finishedRuns(raceId, cache, loaded));
        List<RaceHighlights.Run> round = new ArrayList<>(heat);
        for (long finishedId : boardQuery.finishedRaceIdsInRound(raceId)) {
            round.addAll(finishedRuns(finishedId, cache, loaded));
        }
        return new RaceHighlightsDto(RaceHighlights.of(heat), RaceHighlights.of(round));
    }

    /** The runs of a heat on track, in running order; empty when it has no live state. */
    private Optional<List<RaceHighlights.Run>> liveRuns(long raceId, ResultPointsCache cache,
                                                        Map<Long, Map<Long, List<Long>>> loaded) {
        Optional<LiveRaceState> state = lapTimingService.peek(raceId);
        if (state.isEmpty()) {
            return Optional.empty();
        }
        List<LiveTimingRowDto> rows = state.get().calculatePositions();
        List<Long> entryIds = rows.stream().map(LiveTimingRowDto::entryId).toList();
        Map<Long, Long> competitors = boardQuery.competitorIds(entryIds);
        PublicNames.Snapshot names = publicNames.snapshot();
        Map<Long, List<Long>> lapTimes = new HashMap<>();
        for (LiveTimingRowDto row : rows) {
            lapTimes.put(row.entryId(), state.get().lapTimesSnapshot(row.entryId()));
        }
        Map<Long, Integer> previous = previousLaps(raceId, competitors, lapTimes, cache, loaded);
        List<RaceHighlights.Run> runs = new ArrayList<>();
        for (LiveTimingRowDto row : rows) {
            String name = names.of(competitors.get(row.entryId()), row.driverName(), row.carNumber());
            runs.add(new RaceHighlights.Run(name, row.startPosition(), row.position(),
                    lapTimes.get(row.entryId()), previous.get(row.entryId())));
        }
        return Optional.of(runs);
    }

    /** The runs of a finished heat from its stored result, in finishing order; empty when it has none yet. */
    private List<RaceHighlights.Run> finishedRuns(long raceId, ResultPointsCache cache,
                                                  Map<Long, Map<Long, List<Long>>> loaded) {
        ResultSnapshotDto snapshot;
        try {
            snapshot = resultSnapshotQuery.load(raceId, cache);
        } catch (EntityNotFoundException e) {
            // Finished but not yet snapshotted
            return List.of();
        }
        Map<Long, Integer> grid = boardQuery.gridPositions(raceId);
        Map<Long, List<Long>> lapTimes = lapTimesByEntry(snapshot);
        loaded.put(raceId, lapTimes);
        Map<Long, Long> competitors = boardQuery.competitorIds(lapTimes.keySet());
        Map<Long, Integer> previous = previousLaps(raceId, competitors, lapTimes, cache, loaded);
        List<RaceHighlights.Run> runs = new ArrayList<>();
        for (ResultSnapshotDto.ResultRow row : publicNames.rows(snapshot.positions())) {
            runs.add(new RaceHighlights.Run(row.driverName(), grid.get(row.entryId()), row.position(),
                    lapTimes.getOrDefault(row.entryId(), List.of()), previous.get(row.entryId())));
        }
        return runs;
    }

    /** Each entry's lap durations in lap order from a stored result; lap 0 is the grid, not a lap. */
    private static Map<Long, List<Long>> lapTimesByEntry(ResultSnapshotDto snapshot) {
        Map<Long, List<ResultSnapshotDto.PositionAtLap>> laps = new HashMap<>();
        for (ResultSnapshotDto.PositionAtLap lap : snapshot.lapHistory()) {
            if (lap.lapNumber() < 1) continue;
            laps.computeIfAbsent(lap.entryId(), k -> new ArrayList<>()).add(lap);
        }
        Map<Long, List<Long>> times = new HashMap<>();
        laps.forEach((entryId, list) -> times.put(entryId, list.stream()
                .sorted(Comparator.comparingInt(ResultSnapshotDto.PositionAtLap::lapNumber))
                .map(ResultSnapshotDto.PositionAtLap::lapTimeMs)
                .toList()));
        return times;
    }

    /**
     * For each entry with an earlier heat of the same round type in the event, the laps its driver had done in that
     * heat by the time the entry's own laps so far add up to. Comparing at the same race time keeps a heat that is
     * still running fair against a finished one. Entries with no earlier heat, or whose earlier result is not stored,
     * are left out.
     */
    private Map<Long, Integer> previousLaps(long raceId, Map<Long, Long> competitors,
                                            Map<Long, List<Long>> lapTimes, ResultPointsCache cache,
                                            Map<Long, Map<Long, List<Long>>> loaded) {
        Map<Long, BoardQuery.PreviousHeat> heats = boardQuery.previousHeats(raceId,
                competitors.values().stream().filter(Objects::nonNull).distinct().toList());
        Map<Long, Integer> laps = new HashMap<>();
        for (Map.Entry<Long, List<Long>> own : lapTimes.entrySet()) {
            Long competitor = competitors.get(own.getKey());
            BoardQuery.PreviousHeat previous = competitor == null ? null : heats.get(competitor);
            if (previous == null) continue;
            Map<Long, List<Long>> before = loaded.computeIfAbsent(previous.raceId(), id -> {
                try {
                    return lapTimesByEntry(resultSnapshotQuery.load(id, cache));
                } catch (EntityNotFoundException e) {
                    return Map.of();
                }
            });
            List<Long> beforeLaps = before.get(previous.entryId());
            if (beforeLaps == null) continue;
            long elapsed = own.getValue().stream().filter(lap -> lap != null && lap > 0).mapToLong(Long::longValue).sum();
            long time = 0;
            int count = 0;
            for (Long lap : beforeLaps) {
                if (lap == null || lap <= 0) continue;
                time += lap;
                if (time > elapsed) break;
                count++;
            }
            laps.put(own.getKey(), count);
        }
        return laps;
    }
}
