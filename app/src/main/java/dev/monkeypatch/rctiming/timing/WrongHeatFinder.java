package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.timing.dto.WrongHeatDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tells a transponder that belongs to a driver who is in another heat of the event from one nobody knows. Asked by
 * {@link LapTimingService} the first time a number is seen that matches no entry of the running race, on the timing
 * thread; a lookup per number, never per passing.
 */
@Component
public class WrongHeatFinder {

    private final RaceAuditLabels labels;
    private final EntryRepository entryRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RaceRepository raceRepository;
    private final CompetitorRepository competitorRepository;

    public WrongHeatFinder(RaceAuditLabels labels, EntryRepository entryRepository,
                           RaceEntryRepository raceEntryRepository, RaceRepository raceRepository,
                           CompetitorRepository competitorRepository) {
        this.labels = labels;
        this.entryRepository = entryRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.raceRepository = raceRepository;
        this.competitorRepository = competitorRepository;
    }

    /**
     * The driver the number belongs to when it is held by exactly one competitor's entries in the race's event and
     * none of them is in the race. Empty when nobody holds it (an unknown number), when an entry in the race holds
     * it (the race itself resolves that), or when different competitors hold it, which a referee has to settle.
     * Withdrawn entries are ignored. When the competitor has entries in several classes, the one with a heat in
     * this round is named.
     */
    public Optional<WrongHeatDto> find(long raceId, String transponderNumber) {
        Long eventId = labels.eventOf(raceId);
        Race race = raceRepository.findById(raceId).orElse(null);
        if (eventId == null || race == null) {
            return Optional.empty();
        }
        List<Entry> holders = entryRepository.findByEventId(eventId).stream()
                .filter(e -> e.getStatus() != EntryStatus.WITHDRAWN && e.hasTransponder(transponderNumber))
                .toList();
        Set<Long> competitors = holders.stream().map(Entry::getCompetitorId).collect(Collectors.toSet());
        if (holders.isEmpty() || competitors.size() != 1) {
            return Optional.empty();
        }
        Set<Long> inRace = raceEntryRepository.findByRaceIdOrderByGridPosition(raceId).stream()
                .map(RaceEntry::getEntryId).collect(Collectors.toSet());
        if (holders.stream().anyMatch(e -> inRace.contains(e.getId()))) {
            return Optional.empty();
        }
        List<Race> roundRaces = raceRepository.findByRoundIdOrderBySequenceInRound(race.getRoundId());
        Entry owner = holders.get(0);
        Race home = null;
        RaceEntry homeSlot = null;
        for (Entry holder : holders) {
            for (Race candidate : roundRaces) {
                Optional<RaceEntry> slot = raceEntryRepository.findByRaceIdAndEntryId(candidate.getId(), holder.getId());
                if (slot.isPresent()) {
                    owner = holder;
                    home = candidate;
                    homeSlot = slot.get();
                }
            }
        }
        Integer carNumber = homeSlot == null ? null : homeSlot.getCarNumber();
        String name = competitorRepository.findById(owner.getCompetitorId())
                .map(c -> PublicNames.snapshotFrom(competitorRepository)
                        .of(c.getId(), c.getDisplayName(), carNumber))
                .orElse("Entry " + owner.getId());
        return Optional.of(new WrongHeatDto(transponderNumber, Objects.requireNonNull(owner.getId()), name,
                carNumber, home == null ? null : heatLabel(home), 0, WrongHeatDto.Status.OPEN));
    }

    /** {@code Heat 2}, or {@code B final} for a final. */
    private static String heatLabel(Race race) {
        if (race.getFinalLetter() == null) {
            return "Heat " + race.getHeatNumber();
        }
        return "Z".equals(race.getFinalLetter()) ? "Zombie round" : race.getFinalLetter() + " final";
    }
}
