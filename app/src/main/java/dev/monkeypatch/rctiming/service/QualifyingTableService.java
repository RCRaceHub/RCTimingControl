package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.query.racecontrol.QualifyingTableDto;
import dev.monkeypatch.rctiming.service.QualifyingStandingsService.QualifyingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds each class's overall qualifying table for race control from {@link QualifyingStandingsService}, so
 * officials see the order the finals are (or will be) seeded from, with driver names.
 */
@Service
@Transactional(readOnly = true)
public class QualifyingTableService {

    private final EventClassRepository eventClassRepository;
    private final RacingClassRepository racingClassRepository;
    private final RaceRepository raceRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final QualifyingStandingsService qualifyingStandingsService;

    public QualifyingTableService(EventClassRepository eventClassRepository,
                                  RacingClassRepository racingClassRepository,
                                  RaceRepository raceRepository,
                                  EntryRepository entryRepository,
                                  CompetitorRepository competitorRepository,
                                  QualifyingStandingsService qualifyingStandingsService) {
        this.eventClassRepository = eventClassRepository;
        this.racingClassRepository = racingClassRepository;
        this.raceRepository = raceRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.qualifyingStandingsService = qualifyingStandingsService;
    }

    /** One table per class of the event that has qualifying heats. */
    public List<QualifyingTableDto> forEvent(Long eventId) {
        List<QualifyingTableDto> tables = new ArrayList<>();
        for (EventClass eventClass : eventClassRepository.findByEventId(eventId)) {
            List<Race> heats = raceRepository.findByEventClassIdAndRoundType(eventClass.getId(), RoundType.QUALIFIER);
            if (heats.isEmpty()) {
                continue;
            }
            List<Race> finals = raceRepository.findByEventClassIdAndRoundType(eventClass.getId(), RoundType.FINAL);
            String className = racingClassRepository.findById(eventClass.getRacingClassId())
                    .map(RacingClass::getName).orElse("Class " + eventClass.getRacingClassId());
            List<QualifyingTableDto.Row> rows = new ArrayList<>();
            int position = 1;
            for (QualifyingResult result : qualifyingStandingsService.rankedResultsFor(eventClass.getId())) {
                rows.add(new QualifyingTableDto.Row(position++, result.entryId(), driverName(result.entryId()),
                        result.lapsCompleted(), result.hasBestLap() ? result.bestLapMs() : null,
                        result.points() > 0 ? result.points() : null));
            }
            tables.add(new QualifyingTableDto(eventClass.getId(), className,
                    (int) heats.stream().filter(h -> h.getStatus() == RaceStatus.FINISHED).count(), heats.size(),
                    finals.stream().anyMatch(f -> f.getStatus() != RaceStatus.PENDING), rows));
        }
        return tables;
    }

    private String driverName(Long entryId) {
        return entryRepository.findById(entryId)
                .flatMap(e -> competitorRepository.findById(e.getCompetitorId()))
                .map(Competitor::getDisplayName)
                .orElse("Entry " + entryId);
    }
}
