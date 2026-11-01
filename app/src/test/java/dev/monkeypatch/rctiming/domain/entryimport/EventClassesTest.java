package dev.monkeypatch.rctiming.domain.entryimport;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventClassesTest {

    /** Class 30's racing class was deleted, so it has no name. */
    private final EventClasses classes = new EventClasses(Map.of("RACEHUB-1", 10L), List.of(10L, 20L, 30L),
            Map.of("stockbuggy", List.of(10L), "touring", List.of(20L, 21L)),
            Map.of(10L, "Stock Buggy", 20L, "Touring"),
            Map.of("RACEHUB-3", 5L, "RACEHUB-4", 6L, "RACEHUB-5", 7L),
            Map.of(5L, List.of(10L), 6L, List.of(20L, 21L)));

    @Test
    void aClubLinkPlacesInTheOneEventClassOfThatRacingClass() {
        assertThat(classes.linked("RACEHUB-3")).contains(10L);
        assertThat(classes.linked("RACEHUB-4")).as("racing class is in the event twice").isEmpty();
        assertThat(classes.linked("RACEHUB-5")).as("racing class not in the event").isEmpty();
        assertThat(classes.linked("RACEHUB-9")).as("no link").isEmpty();
    }

    @Test
    void placesByMappingNameOrPosition() {
        assertThat(classes.mapped("RACEHUB-1")).contains(10L);
        assertThat(classes.mapped("RACEHUB-2")).isEmpty();
        assertThat(classes.byName(" Stock  Buggy ")).contains(10L);
        assertThat(classes.byName("Touring")).as("two classes share the name").isEmpty();
        assertThat(classes.atPosition(2)).contains(20L);
        assertThat(classes.atPosition(4)).isEmpty();
        assertThat(classes.name(30L)).as("no racing class").isEqualTo("event class 30");
        assertThat(classes.name(null)).isNull();
    }
}
