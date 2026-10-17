package dev.monkeypatch.rctiming.domain.entryimport;

import dev.monkeypatch.rctiming.domain.Names;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository.EventClassRef;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassMapping;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassMappingRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the RaceHub and RC-Timing CSV imports share: placing rows in the event's classes and
 * checking the result. The class mappings live in RaceHub's table, which both imports use, keyed
 * by source.
 */
@Component
public class EntryImports {

    private final RaceHubClassMappingRepository mappingRepository;
    private final EventClassRepository eventClassRepository;
    private final RacingClassRepository racingClassRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;

    public EntryImports(RaceHubClassMappingRepository mappingRepository,
                        EventClassRepository eventClassRepository,
                        RacingClassRepository racingClassRepository,
                        EntryRepository entryRepository,
                        CompetitorRepository competitorRepository) {
        this.mappingRepository = mappingRepository;
        this.eventClassRepository = eventClassRepository;
        this.racingClassRepository = racingClassRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
    }

    /**
     * Loads the event's class mappings, class order and racing class names once, for an import to place its rows with.
     */
    public EventClasses eventClasses(Long eventId) {
        Map<String, Long> mapped = mappingRepository.findByEventIdOrderByRacehubEventClassId(eventId).stream()
                .collect(Collectors.toMap(RaceHubClassMapping::getRacehubEventClassId,
                        RaceHubClassMapping::getEventClassId));
        List<EventClassRef> eventClasses = eventClassRepository.findRefsByEventId(eventId);
        Map<Long, String> racingClassNames = racingClassRepository.findAllById(eventClasses.stream()
                        .map(EventClassRef::getRacingClassId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(RacingClass::getId, RacingClass::getName));
        Map<Long, String> names = new HashMap<>();
        // A class whose racing class was deleted has no name
        eventClasses.stream().filter(ec -> racingClassNames.containsKey(ec.getRacingClassId()))
                .forEach(ec -> names.put(ec.getId(), racingClassNames.get(ec.getRacingClassId())));
        Map<String, List<Long>> byName = eventClasses.stream()
                .filter(ec -> racingClassNames.containsKey(ec.getRacingClassId()))
                .collect(Collectors.groupingBy(ec -> Names.matchKey(racingClassNames.get(ec.getRacingClassId())),
                        Collectors.mapping(EventClassRef::getId, Collectors.toList())));
        return new EventClasses(mapped, eventClasses.stream().map(EventClassRef::getId).toList(), byName, names);
    }

    /**
     * The event's active entries that the import leaves alone, ready for the import to add what it
     * would leave active. {@code replaced} are the entries the import changes or withdraws.
     */
    public FinalState finalState(Long eventId, Set<Long> replaced, EventClasses classes) {
        FinalState state = new FinalState(classes);
        List<Entry> current = entryRepository.findByEventId(eventId);
        Map<Long, String> names = competitorNames(current);
        for (Entry e : current) {
            if (!replaced.contains(e.getId()) && e.getStatus() != EntryStatus.WITHDRAWN) {
                state.add("c:" + e.getCompetitorId(), names.getOrDefault(e.getCompetitorId(), "entry " + e.getId()),
                        e.getEventClassId(), e.getTransponderNumberSnapshot(), e.getSecondaryTransponderNumber());
            }
        }
        return state;
    }

    /** The display names of the entries' competitors, by competitor id. */
    public Map<Long, String> competitorNames(Collection<Entry> entries) {
        return competitorRepository.findAllById(entries.stream().map(Entry::getCompetitorId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Competitor::getId, Competitor::getDisplayName));
    }
}
