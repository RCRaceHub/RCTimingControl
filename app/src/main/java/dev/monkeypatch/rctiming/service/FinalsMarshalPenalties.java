package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.race.MarshalPenalty;
import dev.monkeypatch.rctiming.domain.race.MarshalPenaltyKind;
import dev.monkeypatch.rctiming.domain.race.MarshalPenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The marshal penalties that cost a lap in the finals. Each takes one lap off the driver's result in the first
 * final they race in their class, so a class run as "best 2 of 3" finals is not charged three times. The lap is
 * taken off when the final's result is worked out, not in live timing, and a penalty given after that final has
 * finished is added by working its result out again.
 */
@Service
public class FinalsMarshalPenalties {

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final MarshalPenaltyRepository marshalPenaltyRepository;
    private final EventClassRepository eventClassRepository;

    public FinalsMarshalPenalties(RaceRepository raceRepository,
                                  RaceEntryRepository raceEntryRepository,
                                  MarshalPenaltyRepository marshalPenaltyRepository,
                                  EventClassRepository eventClassRepository) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.marshalPenaltyRepository = marshalPenaltyRepository;
        this.eventClassRepository = eventClassRepository;
    }

    /** The laps to take off each driver in this race, by entry id: empty unless the race is a final. */
    public Map<Long, Integer> lapsOffIn(Race race) {
        List<Race> finals = finalsOf(race.getEventClassId());
        if (finals.stream().noneMatch(f -> f.getId().equals(race.getId()))) {
            return Map.of();
        }
        EventClass eventClass = eventClassRepository.getOrThrow(race.getEventClassId());
        Map<Long, Integer> owed = new HashMap<>();
        for (MarshalPenalty penalty : marshalPenaltyRepository
                .findByEventIdAndKind(eventClass.getEventId(), MarshalPenaltyKind.FINALS_LAP)) {
            owed.merge(penalty.getEntryId(), 1, Integer::sum);
        }
        if (owed.isEmpty()) {
            return Map.of();
        }
        Set<Long> racedEarlier = new HashSet<>();
        for (Race earlier : finals) {
            if (earlier.getId().equals(race.getId())) {
                break;
            }
            racedEarlier.addAll(entriesOn(earlier));
        }
        Set<Long> onGrid = entriesOn(race);
        Map<Long, Integer> lapsOff = new HashMap<>();
        owed.forEach((entryId, laps) -> {
            if (onGrid.contains(entryId) && !racedEarlier.contains(entryId)) {
                lapsOff.put(entryId, laps);
            }
        });
        return lapsOff;
    }

    /** The first final the entry races in its class, if it has been placed in one. */
    public Optional<Race> firstFinalOf(long entryId, long eventClassId) {
        return finalsOf(eventClassId).stream().filter(f -> entriesOn(f).contains(entryId)).findFirst();
    }

    private List<Race> finalsOf(Long eventClassId) {
        return raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL).stream()
                .sorted(Comparator.comparing(Race::getId)).toList();
    }

    private Set<Long> entriesOn(Race race) {
        Set<Long> ids = new HashSet<>();
        for (RaceEntry onGrid : raceEntryRepository.findByRaceIdOrderByGridPosition(race.getId())) {
            ids.add(onGrid.getEntryId());
        }
        return ids;
    }
}
