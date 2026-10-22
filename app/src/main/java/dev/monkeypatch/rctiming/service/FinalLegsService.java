package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.query.racecontrol.FinalLegsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out the combined result of an A final run in legs (#427) from the results stored when each leg finished, so
 * the order cannot be made up by whoever asks for it. Only classes whose A final has two or more legs are listed.
 * The legs' own results are untouched; this only reads them.
 */
@Service
public class FinalLegsService {

    private static final String TOP_FINAL = "A";

    private final EventClassRepository eventClassRepository;
    private final RacingClassRepository racingClassRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final ResultSnapshotJson snapshotJson;
    private final RaceFormatService raceFormatService;

    public FinalLegsService(EventClassRepository eventClassRepository,
                            RacingClassRepository racingClassRepository,
                            RaceRepository raceRepository,
                            RaceEntryRepository raceEntryRepository,
                            ResultSnapshotRepository resultSnapshotRepository,
                            ResultSnapshotJson snapshotJson,
                            RaceFormatService raceFormatService) {
        this.eventClassRepository = eventClassRepository;
        this.racingClassRepository = racingClassRepository;
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.snapshotJson = snapshotJson;
        this.raceFormatService = raceFormatService;
    }

    /**
     * One combined result per class of the event whose A final has legs, in the order of the classes. An abandoned
     * leg has no result and is not counted as finished.
     */
    @Transactional(readOnly = true)
    public List<FinalLegsDto> forEvent(Long eventId) {
        List<FinalLegsDto> results = new ArrayList<>();
        for (EventClass eventClass : eventClassRepository.findByEventId(eventId)) {
            List<Race> legs = raceRepository.findByEventClassIdAndRoundType(eventClass.getId(), RoundType.FINAL)
                    .stream().filter(r -> TOP_FINAL.equals(r.getFinalLetter()))
                    .sorted(Comparator.comparingInt(Race::getHeatNumber)).toList();
            if (legs.size() < 2) {
                continue;
            }
            results.add(combine(eventClass, legs));
        }
        return results;
    }

    private FinalLegsDto combine(EventClass eventClass, List<Race> legs) {
        // Everyone on the first leg's grid is in the final, so a driver who missed a later leg still scores for it
        Set<Long> drivers = new LinkedHashSet<>();
        raceEntryRepository.findByRaceIdOrderByGridPosition(legs.get(0).getId())
                .stream().map(RaceEntry::getEntryId).forEach(drivers::add);
        Map<Long, String> names = new HashMap<>();
        List<Map<Long, FinalLegScoring.Run>> runs = new ArrayList<>();
        int finished = 0;
        for (Race leg : legs) {
            if (leg.getStatus() != RaceStatus.FINISHED) {
                continue;
            }
            finished++;
            // An abandoned leg is over but has no result: it is left out of the scoring, as for the championship
            if (leg.getAbandonedAt() != null) {
                continue;
            }
            Map<Long, FinalLegScoring.Run> legRuns = new LinkedHashMap<>();
            resultSnapshotRepository.findByRaceId(leg.getId()).ifPresent(snapshot -> {
                for (ResultSnapshotDto.ResultRow row : snapshotJson.positions(leg.getId(), snapshot.getPositionsJson())) {
                    legRuns.put(row.entryId(), new FinalLegScoring.Run(row.lapsCompleted(), row.totalTimeMs()));
                    names.putIfAbsent(row.entryId(), row.driverName());
                    drivers.add(row.entryId());
                }
            });
            runs.add(legRuns);
        }
        int legsCounted = raceFormatService.legsCounted(eventClass);
        List<FinalLegScoring.Standing> standings = FinalLegScoring.rank(runs, drivers, legsCounted);
        List<FinalLegsDto.Row> rows = new ArrayList<>();
        for (int i = 0; i < standings.size(); i++) {
            FinalLegScoring.Standing s = standings.get(i);
            rows.add(new FinalLegsDto.Row(i + 1, s.entryId(), names.getOrDefault(s.entryId(), "Entry " + s.entryId()),
                    s.points(), s.laps(), s.timeMs(), s.legPoints(), s.counted()));
        }
        String className = racingClassRepository.findById(eventClass.getRacingClassId())
                .map(RacingClass::getName).orElse("Class " + eventClass.getRacingClassId());
        return new FinalLegsDto(eventClass.getId(), className, legs.size(), finished, legsCounted,
                finished == legs.size(), rows);
    }
}
