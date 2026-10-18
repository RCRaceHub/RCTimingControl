package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.format.ShikabaneConfig;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceElimination;
import dev.monkeypatch.rctiming.domain.race.RaceEliminationRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RacePointPenalty;
import dev.monkeypatch.rctiming.domain.race.RacePointPenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The penalty points each car scored in one finished Shikabane heat. A car's points are its place in the stored
 * result (1 for the last car alive, up to the number of cars for the first to die), less the zombie bonus for each
 * revival it was given and plus any point penalties such as a false start. Lowest wins. Read by low-points
 * qualifying and when the next heat's grid is drawn. Runs inside the caller's transaction or read snapshot.
 */
@Component
public class ShikabaneHeatScores {

    /** One car's heat points and the laps it completed in the heat. */
    public record Score(long entryId, int points, int laps) {}

    private final RaceEntryRepository raceEntryRepository;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final ResultSnapshotJson snapshotJson;
    private final RaceEliminationRepository eliminationRepository;
    private final RacePointPenaltyRepository pointPenaltyRepository;

    public ShikabaneHeatScores(RaceEntryRepository raceEntryRepository,
                               ResultSnapshotRepository resultSnapshotRepository,
                               ResultSnapshotJson snapshotJson,
                               RaceEliminationRepository eliminationRepository,
                               RacePointPenaltyRepository pointPenaltyRepository) {
        this.raceEntryRepository = raceEntryRepository;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.snapshotJson = snapshotJson;
        this.eliminationRepository = eliminationRepository;
        this.pointPenaltyRepository = pointPenaltyRepository;
    }

    /**
     * Every car on the heat's grid, with its points. A car with no row in the result (it never crossed the line and
     * was not marked dead) is placed after the last one that has a row. An empty list for a heat with no stored
     * result: one that is abandoned or not finished.
     */
    public List<Score> scores(Race heat, ShikabaneConfig config) {
        List<RaceEntry> grid = raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId());
        Map<Long, Integer> place = new LinkedHashMap<>();
        Map<Long, Integer> laps = new HashMap<>();
        if (heat.getAbandonedAt() == null) {
            resultSnapshotRepository.findByRaceId(heat.getId()).ifPresent(snapshot -> {
                for (ResultSnapshotDto.ResultRow row : snapshotJson.positions(heat.getId(),
                        snapshot.getPositionsJson())) {
                    place.put(row.entryId(), row.position());
                    laps.put(row.entryId(), row.lapsCompleted());
                }
            });
        }
        if (place.isEmpty()) {
            // Abandoned, or no result stored: the heat has no scores and its cars take the round's missing-heat points
            return List.of();
        }
        int unplaced = place.size() + 1;

        Map<Long, Integer> adjustment = new HashMap<>();
        for (RaceElimination e : eliminationRepository.findByRaceId(heat.getId())) {
            if (!e.isActive()) {
                adjustment.merge(e.getEntryId(), -config.zombieBonusPoints(), Integer::sum);
            }
        }
        for (RacePointPenalty p : pointPenaltyRepository.findByRaceId(heat.getId())) {
            adjustment.merge(p.getEntryId(), p.getPoints(), Integer::sum);
        }

        Map<Long, Score> scored = new LinkedHashMap<>();
        for (RaceEntry onGrid : grid) {
            long id = onGrid.getEntryId();
            scored.put(id, new Score(id, place.getOrDefault(id, unplaced) + adjustment.getOrDefault(id, 0),
                    laps.getOrDefault(id, 0)));
        }
        // A car in the result that is not on the grid (a marshal credited laps to it) still scores
        place.forEach((id, position) -> scored.putIfAbsent(id,
                new Score(id, position + adjustment.getOrDefault(id, 0), laps.getOrDefault(id, 0))));
        return List.copyOf(scored.values());
    }
}
