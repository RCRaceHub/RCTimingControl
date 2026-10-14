package dev.monkeypatch.rctiming.livefeed;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads what the live feed needs to know about a race, fresh each time (#28). */
@Component
public class LiveFeedRaceLookup {

    private final RaceRepository raceRepository;
    private final RoundRepository roundRepository;
    private final EventRepository eventRepository;
    private final EventClassRepository eventClassRepository;
    private final RacingClassRepository racingClassRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final RaceFormatService raceFormatService;

    public LiveFeedRaceLookup(RaceRepository raceRepository, RoundRepository roundRepository,
                              EventRepository eventRepository, EventClassRepository eventClassRepository,
                              RacingClassRepository racingClassRepository, RaceEntryRepository raceEntryRepository,
                              EntryRepository entryRepository, CompetitorRepository competitorRepository,
                              RaceFormatService raceFormatService) {
        this.raceRepository = raceRepository;
        this.roundRepository = roundRepository;
        this.eventRepository = eventRepository;
        this.eventClassRepository = eventClassRepository;
        this.racingClassRepository = racingClassRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.raceFormatService = raceFormatService;
    }

    /**
     * A race as the feed describes it.
     *
     * @param feedOn     whether its event has the live feed turned on
     * @param durationMs its length from the class's format, or null
     * @param grid       the cars in the race, in grid order
     */
    public record RaceInfo(long raceId, String status, boolean feedOn, LiveFeedV1.Event event, String className,
                           String roundType, int roundNumber, int heatNumber, String finalLetter, Long durationMs,
                           List<GridCar> grid) {

        Integer carNumber(long entryId) {
            return grid.stream().filter(car -> car.entryId() == entryId).findFirst()
                    .map(GridCar::carNumber).orElse(null);
        }
    }

    /** A car in the race: its entry, the driver's display name and its car number, if it has one. */
    public record GridCar(long entryId, String displayName, Integer carNumber) {
    }

    @Transactional(readOnly = true)
    public Optional<RaceInfo> find(long raceId) {
        Race race = raceRepository.findById(raceId).orElse(null);
        if (race == null) {
            return Optional.empty();
        }
        Round round = roundRepository.findById(race.getRoundId()).orElse(null);
        Event event = round == null ? null : eventRepository.findById(round.getEventId()).orElse(null);
        if (event == null) {
            return Optional.empty();
        }
        EventClass eventClass = race.getEventClassId() == null ? null
                : eventClassRepository.findById(race.getEventClassId()).orElse(null);
        String className = eventClass == null || eventClass.getRacingClassId() == null ? null
                : racingClassRepository.findById(eventClass.getRacingClassId()).map(c -> c.getName()).orElse(null);
        List<GridCar> grid = new ArrayList<>();
        PublicNames.Snapshot names = PublicNames.snapshotFrom(competitorRepository);
        for (RaceEntry raceEntry : raceEntryRepository.findByRaceIdOrderByGridPosition(raceId)) {
            // Display names only, and as the public may see them (#265)
            String name = entryRepository.findById(raceEntry.getEntryId())
                    .map(Entry::getCompetitorId)
                    .flatMap(competitorRepository::findById)
                    .map(competitor -> names.of(competitor.getId(), competitor.getDisplayName(),
                            raceEntry.getCarNumber()))
                    .orElse(null);
            grid.add(new GridCar(raceEntry.getEntryId(), name, raceEntry.getCarNumber()));
        }
        return Optional.of(new RaceInfo(
                raceId,
                race.getStatus().name(),
                event.isLiveFeedEnabled(),
                new LiveFeedV1.Event(event.getId(), event.getName(), String.valueOf(event.getEventDate())),
                className,
                round.getType().name(),
                round.getRoundNumber(),
                race.getHeatNumber(),
                race.getFinalLetter(),
                raceFormatService.raceDurationMs(eventClass, round.getType()),
                grid));
    }
}
