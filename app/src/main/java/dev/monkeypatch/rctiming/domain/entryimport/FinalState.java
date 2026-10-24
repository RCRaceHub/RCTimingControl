package dev.monkeypatch.rctiming.domain.entryimport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * An event's active entries as they would be after an import, from every source. Starts with the
 * entries the import leaves alone ({@link EntryImports#finalState}); the import adds the ones it
 * would leave active. A driver entered twice in one class blocks the import (the database allows
 * one active entry each); a transponder used by more than one active entry is only a warning, as
 * the referee resolves it on the day.
 */
public final class FinalState {

    private record Active(String driverKey, String name, Long eventClassId, List<String> numbers) {}

    private final EventClasses classes;
    private final List<Active> active = new ArrayList<>();

    FinalState(EventClasses classes) {
        this.classes = classes;
    }

    /**
     * Adds an entry that would be active.
     *
     * @param driverKey the same for every entry of one driver: {@code "c:" + competitorId} for a known competitor
     * @param extra     the entry's third and fourth transponders, which an import never sets but an existing entry
     *                  may hold (#482)
     */
    public void add(String driverKey, String name, Long eventClassId, String primary, String secondary,
                    String... extra) {
        List<String> numbers = new ArrayList<>(Arrays.asList(primary, secondary));
        numbers.addAll(Arrays.asList(extra));
        active.add(new Active(driverKey, name, eventClassId, numbers));
    }

    /** Adds what is wrong with the result to {@code errors} and {@code warnings}. */
    public void check(List<String> errors, List<String> warnings) {
        active.stream().filter(a -> a.eventClassId() != null)
                .collect(Collectors.groupingBy(a -> a.driverKey() + "/" + a.eventClassId(),
                        LinkedHashMap::new, Collectors.toList()))
                .values().stream().filter(l -> l.size() > 1)
                .forEach(l -> errors.add(l.get(0).name() + " would have " + l.size() + " entries in "
                        + classes.name(l.get(0).eventClassId())));

        Map<String, Set<String>> byTransponder = new TreeMap<>();
        Map<String, Integer> uses = new HashMap<>();
        for (Active a : active) {
            for (String number : new LinkedHashSet<>(a.numbers())) {
                if (number != null && !number.isBlank()) {
                    byTransponder.computeIfAbsent(number, k -> new LinkedHashSet<>()).add(a.name());
                    uses.merge(number, 1, Integer::sum);
                }
            }
        }
        byTransponder.forEach((number, users) -> {
            if (uses.get(number) > 1) {
                warnings.add("Transponder " + number + " is used by more than one entry: " + String.join(", ", users));
            }
        });
    }
}
