package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.api.boards.dto.FinalsBoardDto;
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
import dev.monkeypatch.rctiming.query.racecontrol.FinalLegsDto;
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
 * thread; each lookup runs on its own read snapshot, and a page that races a finishing final shows it as unfinished
 * until the next refresh.
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
     * The finals of every class of the event that has any, in the order of the classes. A class with no finals yet is
     * left out. A final in legs is one entry: leg 1 supplies the line-up and status, the legs' combined result is
     * the order. A zombie round is not a final and is left out.
     */
    public List<FinalsBoardDto.ClassFinals> forEvent(Long eventId) {
        Map<Long, FinalLegsDto> legsByClass = finalLegsService.forEventPublic(eventId).stream()
                .collect(Collectors.toMap(FinalLegsDto::eventClassId, Function.identity()));
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
                boolean done = race.getStatus() == RaceStatus.FINISHED;
                finished += done ? 1 : 0;
                FinalLegsDto legs = "A".equals(race.getFinalLetter()) ? legsByClass.get(eventClass.getId()) : null;
                rows.add(new FinalsBoardDto.Final(race.getFinalLetter(), race.getStatus(),
                        done ? resultRows(race.getId()) : List.of(), legs));
            }
            String className = racingClassRepository.findById(eventClass.getRacingClassId())
                    .map(RacingClass::getName).orElse("Class " + eventClass.getRacingClassId());
            classes.add(new FinalsBoardDto.ClassFinals(eventClass.getId(), className, finished, finals.size(), rows));
        }
        return classes;
    }

    private List<ResultSnapshotDto.ResultRow> resultRows(long raceId) {
        try {
            return publicNames.rows(resultSnapshotQuery.load(raceId).positions());
        } catch (EntityNotFoundException e) {
            // Finished but not yet snapshotted
            return List.of();
        }
    }
}
