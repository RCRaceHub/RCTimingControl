package dev.monkeypatch.rctiming.domain.entryimport;

import dev.monkeypatch.rctiming.domain.Names;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An event's classes as the entry imports place rows in them: the class mappings an official set
 * up, the classes in order and by racing class name. Loaded once per import by
 * {@link EntryImports}.
 */
public final class EventClasses {

    private final Map<String, Long> mapped;
    private final List<Long> inOrder;
    private final Map<String, List<Long>> byName;
    private final Map<Long, String> names;
    private final Map<String, Long> clubLinks;
    private final Map<Long, List<Long>> byRacingClass;

    /**
     * {@code names} holds only the classes whose racing class still exists. {@code clubLinks} are the club-wide
     * links from a class key to a racing class, and {@code byRacingClass} the event's classes for each racing class.
     */
    EventClasses(Map<String, Long> mapped, List<Long> inOrder, Map<String, List<Long>> byName,
                 Map<Long, String> names, Map<String, Long> clubLinks, Map<Long, List<Long>> byRacingClass) {
        this.clubLinks = Map.copyOf(clubLinks);
        this.byRacingClass = Map.copyOf(byRacingClass);
        this.mapped = Map.copyOf(mapped);
        this.inOrder = List.copyOf(inOrder);
        this.byName = Map.copyOf(byName);
        this.names = Map.copyOf(names);
    }

    /** The event class a stored mapping puts {@code mappingKey} in. */
    public Optional<Long> mapped(String mappingKey) {
        return Optional.ofNullable(mapped.get(mappingKey));
    }

    /**
     * The one event class of the racing class the club linked {@code mappingKey} to; none when the club made no
     * link, the racing class is not in this event, or it is in the event more than once (an official chooses).
     */
    public Optional<Long> linked(String mappingKey) {
        Long racingClassId = clubLinks.get(mappingKey);
        List<Long> matches = racingClassId == null ? List.of() : byRacingClass.getOrDefault(racingClassId, List.of());
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    /** The racing class the club linked {@code mappingKey} to, whether or not it is in this event yet. */
    public Optional<Long> linkedRacingClass(String mappingKey) {
        return Optional.ofNullable(clubLinks.get(mappingKey));
    }

    /** The one event class whose racing class has this name ({@link Names#matchKey}); none when several do. */
    public Optional<Long> byName(String className) {
        List<Long> matches = byName.getOrDefault(Names.matchKey(className), List.of());
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    /** The event's class at a 1-based position, in the order the event lists them. */
    public Optional<Long> atPosition(int position) {
        return position >= 1 && position <= inOrder.size() ? Optional.of(inOrder.get(position - 1)) : Optional.empty();
    }

    /** The class's racing class name, for messages. */
    public String name(Long eventClassId) {
        if (eventClassId == null) {
            return null;
        }
        return names.getOrDefault(eventClassId, "event class " + eventClassId);
    }
}
