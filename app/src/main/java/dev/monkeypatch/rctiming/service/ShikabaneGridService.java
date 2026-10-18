package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.ShikabaneConfig;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Draws the starting grid of the next Shikabane heat when a heat finishes. Heat 1 is a random draw (made by the round
 * generator), heat 2 starts in the reverse of heat 1's result so the winner is last, and every later heat starts in
 * cumulative-points order, fewest points at the front. Keeps the same drivers per heat number, as the generator does.
 * Called by the race state machine after the result is stored, in the finish's transaction, and again when a
 * finished heat is corrected. A heat that has already been called is left as it is.
 */
@Service
@Transactional
public class ShikabaneGridService {

    private static final Logger log = LoggerFactory.getLogger(ShikabaneGridService.class);

    private final RoundRepository roundRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EventClassRepository eventClassRepository;
    private final RaceFormatService raceFormatService;
    private final ShikabaneHeatScores heatScores;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final ResultSnapshotJson snapshotJson;

    public ShikabaneGridService(RoundRepository roundRepository,
                                RaceRepository raceRepository,
                                RaceEntryRepository raceEntryRepository,
                                EventClassRepository eventClassRepository,
                                RaceFormatService raceFormatService,
                                ShikabaneHeatScores heatScores,
                                ResultSnapshotRepository resultSnapshotRepository,
                                ResultSnapshotJson snapshotJson) {
        this.roundRepository = roundRepository;
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.eventClassRepository = eventClassRepository;
        this.raceFormatService = raceFormatService;
        this.heatScores = heatScores;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.snapshotJson = snapshotJson;
    }

    /**
     * Sets the grid of the same heat in the next qualifying round, if the finished race is a qualifying heat of a
     * Shikabane class and there is a next round. Does nothing otherwise.
     */
    public void applyAfterFinish(Race finished) {
        try {
            draw(finished);
        } catch (RuntimeException e) {
            // Not allowed to fail the finish: the grid can still be set by hand
            log.warn("Could not draw the next Shikabane grid after race {} finished", finished.getId(), e);
        }
    }

    private void draw(Race finished) {
        Round round = roundRepository.findById(finished.getRoundId()).orElse(null);
        if (round == null || round.getType() != RoundType.QUALIFIER) {
            return;
        }
        Optional<ShikabaneConfig> config = raceFormatService
                .shikabaneConfig(eventClassRepository.findById(finished.getEventClassId()).orElse(null));
        if (config.isEmpty()) {
            return;
        }
        Round next = roundRepository.findByEventIdOrderBySequenceInEvent(round.getEventId()).stream()
                .filter(r -> r.getType() == RoundType.QUALIFIER && r.getSequenceInEvent() > round.getSequenceInEvent())
                .findFirst().orElse(null);
        if (next == null) {
            return;
        }
        Race nextHeat = raceRepository.findByRoundIdOrderBySequenceInRound(next.getId()).stream()
                .filter(r -> r.getEventClassId().equals(finished.getEventClassId())
                        && r.getHeatNumber() == finished.getHeatNumber())
                .findFirst().orElse(null);
        // A heat that has been called or run keeps its grid
        if (nextHeat == null || nextHeat.getStatus() != RaceStatus.PENDING) {
            return;
        }
        List<Long> order = next.getRoundNumber() == 2
                ? reverseOfResult(finished)
                : cumulativeOrder(round, finished, config.get());
        setGrid(nextHeat, order);
    }

    /** The finished heat's entries, last place first. */
    private List<Long> reverseOfResult(Race finished) {
        List<Long> placed = new ArrayList<>(resultSnapshotRepository.findByRaceId(finished.getId())
                .map(s -> snapshotJson.positions(finished.getId(), s.getPositionsJson())).orElse(List.of())
                .stream().map(ResultSnapshotDto.ResultRow::entryId).toList());
        if (placed.isEmpty()) {
            return placed;
        }
        Collections.reverse(placed);
        // A car with no row was behind everyone who has one, so it starts at the front
        List<Long> order = new ArrayList<>();
        raceEntryRepository.findByRaceIdOrderByGridPosition(finished.getId()).stream()
                .map(RaceEntry::getEntryId).filter(id -> !placed.contains(id)).forEach(order::add);
        order.addAll(placed);
        return order;
    }

    /** Entries of the heat by points over this and the earlier qualifying heats of it, fewest first, then most laps. */
    private List<Long> cumulativeOrder(Round round, Race finished, ShikabaneConfig config) {
        Map<Long, Integer> points = new HashMap<>();
        Map<Long, Integer> laps = new HashMap<>();
        for (Round r : roundRepository.findByEventIdOrderBySequenceInEvent(round.getEventId())) {
            if (r.getType() != RoundType.QUALIFIER || r.getSequenceInEvent() > round.getSequenceInEvent()) {
                continue;
            }
            raceRepository.findByRoundIdOrderBySequenceInRound(r.getId()).stream()
                    .filter(h -> h.getEventClassId().equals(finished.getEventClassId())
                            && h.getHeatNumber() == finished.getHeatNumber())
                    .forEach(h -> heatScores.scores(h, config).forEach(s -> {
                        points.merge(s.entryId(), s.points(), Integer::sum);
                        laps.merge(s.entryId(), s.laps(), Integer::sum);
                    }));
        }
        return points.keySet().stream()
                .sorted(Comparator.<Long>comparingInt(points::get)
                        .thenComparing(Comparator.<Long>comparingInt(laps::get).reversed())
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /**
     * Grid positions 1, 2, ... for the heat's entries in the given order; entries it does not name follow, in the
     * order they were. Ids in the order that are not in the heat are skipped.
     */
    private void setGrid(Race heat, List<Long> order) {
        List<RaceEntry> entries = new ArrayList<>(raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId()));
        Map<Long, Integer> wanted = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            wanted.put(order.get(i), i);
        }
        // The sort is stable, so the entries the order does not name keep their current relative order
        entries.sort(Comparator.comparingInt(e -> wanted.getOrDefault(e.getEntryId(), Integer.MAX_VALUE)));
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setGridPosition(i + 1);
            raceEntryRepository.save(entries.get(i));
        }
    }
}
