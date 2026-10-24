package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.boards.LiveQualifyingDto;
import dev.monkeypatch.rctiming.service.QualifyingStandingsService.Overlay;
import dev.monkeypatch.rctiming.service.QualifyingStandingsService.ProjectedResults;
import dev.monkeypatch.rctiming.service.QualifyingStandingsService.QualifyingResult;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the rows of the live qualifying table (#480) for one qualifying heat: the class's low-points order with
 * the heat counted, and where each driver stood without it, so a board can show who is moving. It only reads, and
 * is called from the anonymous board endpoint, which hands it the heat's live positions (the live timing state
 * lives in the {@code timing} package, which this class does not depend on).
 */
@Service
public class LiveQualifyingService {

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final QualifyingStandingsService qualifyingStandingsService;
    private final PublicNames publicNames;

    public LiveQualifyingService(RaceRepository raceRepository,
                                 RaceEntryRepository raceEntryRepository,
                                 EntryRepository entryRepository,
                                 CompetitorRepository competitorRepository,
                                 QualifyingStandingsService qualifyingStandingsService,
                                 PublicNames publicNames) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.qualifyingStandingsService = qualifyingStandingsService;
        this.publicNames = publicNames;
    }

    /**
     * The table for a qualifying heat. {@code liveRows} are the heat's live positions while it runs, or null once
     * it has finished, when its stored result is counted. Empty when the heat is not a qualifying heat or its class
     * is not ranked by low points. Drivers whose only heat is still to come are not in a first round's table, so
     * they show as waiting only from the second round on. Names are as the public may see them (#265).
     */
    @ReadTransaction
    public Optional<List<LiveQualifyingDto.Row>> forHeat(long raceId, List<LiveTimingRowDto> liveRows) {
        Race race = raceRepository.findById(raceId).orElse(null);
        if (race == null) {
            return Optional.empty();
        }
        Long eventClassId = race.getEventClassId();
        boolean qualifier = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.QUALIFIER)
                .stream().anyMatch(h -> h.getId().equals(raceId));
        if (!qualifier) {
            return Optional.empty();
        }
        // A heat that has started but has no live positions yet (before its first passing, or after a restart) is
        // left out rather than scored as every driver on no laps
        Overlay counted = liveRows == null ? Overlay.stored(raceId)
                : liveRows.isEmpty() ? Overlay.without(raceId) : Overlay.live(raceId, liveRows);
        ProjectedResults after = qualifyingStandingsService.projectedResultsFor(eventClassId, counted);
        if (after.results().isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Integer> before = new HashMap<>();
        List<QualifyingResult> baseline = qualifyingStandingsService
                .projectedResultsFor(eventClassId, Overlay.without(raceId)).results();
        for (int i = 0; i < baseline.size(); i++) {
            before.put(baseline.get(i).entryId(), i + 1);
        }
        Set<Long> inHeat = new HashSet<>();
        raceEntryRepository.findByRaceIdOrderByGridPosition(raceId).forEach(e -> inHeat.add(e.getEntryId()));
        Map<Long, String> carNumbers = carNumbers(eventClassId);
        PublicNames.Snapshot names = publicNames.snapshot();

        List<LiveQualifyingDto.Row> rows = new ArrayList<>();
        for (int i = 0; i < after.results().size(); i++) {
            QualifyingResult result = after.results().get(i);
            Long entryId = result.entryId();
            rows.add(new LiveQualifyingDto.Row(i + 1, before.get(entryId), entryId, driverName(entryId, carNumbers.get(entryId), names),
                    carNumbers.get(entryId), result.points(), result.lapsCompleted(),
                    result.hasBestLap() ? result.bestLapMs() : null, inHeat.contains(entryId),
                    after.waiting().contains(entryId)));
        }
        return Optional.of(rows);
    }

    /** Each entry's car number across the class's qualifying heats. */
    private Map<Long, String> carNumbers(Long eventClassId) {
        Map<Long, String> carNumbers = new HashMap<>();
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.QUALIFIER)) {
            for (RaceEntry raceEntry : raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())) {
                if (raceEntry.getCarNumber() != null) {
                    carNumbers.put(raceEntry.getEntryId(), raceEntry.getCarNumber().toString());
                }
            }
        }
        return carNumbers;
    }

    private String driverName(Long entryId, String carNumber, PublicNames.Snapshot names) {
        return entryRepository.findById(entryId)
                .flatMap(e -> competitorRepository.findById(e.getCompetitorId()))
                .map(c -> names.of(c.getId(), c.getDisplayName(), carNumber))
                .orElse(carNumber == null ? "Driver" : "Car " + carNumber);
    }
}
