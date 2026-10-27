package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.boards.FinalsBoardDto;
import dev.monkeypatch.rctiming.query.racecontrol.FinalLegsDto;
import dev.monkeypatch.rctiming.query.racecontrol.ResultPointsCache;
import dev.monkeypatch.rctiming.query.racecontrol.ResultSnapshotQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the public finals page (the A, B and C mains of each class) from the finals' stored results. It only reads:
 * a final that has not finished is listed with its status and no rows. Called from the board controller on a request
 * thread; one call is one read snapshot, so the legs, statuses and results it joins always agree.
 */
@Service
public class FinalsBoardService {

    private static final String ZOMBIE_ROUND = "Z";

    private final EventClassRepository eventClassRepository;
    private final RacingClassRepository racingClassRepository;
    private final RaceRepository raceRepository;
    private final ResultSnapshotQuery resultSnapshotQuery;
    private final FinalLegsService finalLegsService;
    private final PublicNames publicNames;

    public FinalsBoardService(EventClassRepository eventClassRepository,
                              RacingClassRepository racingClassRepository,
                              RaceRepository raceRepository,
                              ResultSnapshotQuery resultSnapshotQuery,
                              FinalLegsService finalLegsService,
                              PublicNames publicNames) {
        this.eventClassRepository = eventClassRepository;
        this.racingClassRepository = racingClassRepository;
        this.raceRepository = raceRepository;
        this.resultSnapshotQuery = resultSnapshotQuery;
        this.finalLegsService = finalLegsService;
        this.publicNames = publicNames;
    }

    /**
     * The finals of every class of the event that has any, in the order of the classes, with names as the public may
     * see them (#265). A class with no finals yet is left out. A final in legs is one entry: leg 1 supplies the
     * line-up, the legs' combined result is the order, and it counts as finished only when every leg has. A zombie
     * round is not a final and is left out. One read snapshot.
     */
    @ReadTransaction
    public List<FinalsBoardDto.ClassFinals> forEventPublic(Long eventId) {
        Map<String, FinalLegsDto> legsByFinal = finalLegsService.forEventPublic(eventId).stream()
                .collect(Collectors.toMap(l -> legsKey(l.eventClassId(), l.finalLetter()), Function.identity(), (first, second) -> first));
        PublicNames.Snapshot names = publicNames.snapshot();
        // The event's championship points are worked out once for all its finals
        ResultPointsCache pointsCache = new ResultPointsCache();
        List<FinalsBoardDto.ClassFinals> classes = new ArrayList<>();
        for (EventClass eventClass : eventClassRepository.findByEventId(eventId)) {
            List<Race> finals = raceRepository.findByEventClassIdAndRoundType(eventClass.getId(), RoundType.FINAL)
                    .stream()
                    .filter(r -> r.getFinalLetter() != null && !ZOMBIE_ROUND.equals(r.getFinalLetter()))
                    .filter(r -> r.getHeatNumber() == 1)
                    .sorted(Comparator.comparing(Race::getFinalLetter).thenComparing(Race::getId))
                    .toList();
            if (finals.isEmpty()) {
                continue;
            }
            List<FinalsBoardDto.Final> rows = new ArrayList<>();
            int finished = 0;
            for (Race race : finals) {
                FinalLegsDto legs = legsByFinal.get(legsKey(eventClass.getId(), race.getFinalLetter()));
                RaceStatus status = race.getStatus();
                if (legs != null && !legs.complete() && status == RaceStatus.FINISHED) {
                    // Leg 1 is over but the final is not
                    status = RaceStatus.RUNNING;
                }
                boolean abandoned = legs == null && race.getAbandonedAt() != null;
                boolean done = status == RaceStatus.FINISHED;
                finished += done ? 1 : 0;
                rows.add(new FinalsBoardDto.Final(race.getFinalLetter(), status, abandoned,
                        done && !abandoned ? resultRows(race.getId(), names, pointsCache) : List.of(), legs));
            }
            String className = racingClassRepository.findById(eventClass.getRacingClassId())
                    .map(RacingClass::getName).orElse("Class " + eventClass.getRacingClassId());
            classes.add(new FinalsBoardDto.ClassFinals(eventClass.getId(), className, finished, finals.size(), rows));
        }
        return classes;
    }

    private static String legsKey(Long eventClassId, String finalLetter) {
        return eventClassId + ":" + finalLetter;
    }

    private List<ResultSnapshotDto.ResultRow> resultRows(long raceId, PublicNames.Snapshot names,
                                                         ResultPointsCache pointsCache) {
        try {
            return resultSnapshotQuery.load(raceId, pointsCache).positions().stream()
                    .map(r -> new ResultSnapshotDto.ResultRow(r.position(), r.entryId(), r.competitorId(),
                            names.of(r.competitorId(), r.driverName(), r.carNumber()), r.carNumber(),
                            r.lapsCompleted(), r.totalTimeMs(), r.bestLapMs(), r.gapToLeaderMs(), r.points(),
                            r.championshipPoints(), r.dnf()))
                    .toList();
        } catch (EntityNotFoundException e) {
            // Finished but not yet snapshotted
            return List.of();
        }
    }
}
