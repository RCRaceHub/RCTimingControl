package dev.monkeypatch.rctiming.domain.entryimport;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinalStateTest {

    private final EventClasses classes = new EventClasses(Map.of(), List.of(10L, 20L),
            Map.of("stockbuggy", List.of(10L)), Map.of(10L, "Stock Buggy", 20L, "Touring"), Map.of(), Map.of());

    @Test
    void aDriverTwiceInOneClassIsAnError() {
        FinalState state = new FinalState(classes);
        state.add("c:1", "Jane Doe", 10L, "111", null);
        state.add("c:1", "Jane Doe", 10L, "222", null);
        state.add("c:1", "Jane Doe", 20L, "333", null);

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        state.check(errors, warnings);

        assertThat(errors).containsExactly("Jane Doe would have 2 entries in Stock Buggy");
        assertThat(warnings).isEmpty();
    }

    @Test
    void aSharedTransponderIsOnlyAWarningAndAnEntryUsingItTwiceCountsOnce() {
        FinalState state = new FinalState(classes);
        state.add("c:1", "Jane Doe", 10L, "111", "111");
        state.add("c:2", "John Smith", 20L, "222", "111");
        state.add("c:3", "Solo Driver", 20L, "333", "333");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        state.check(errors, warnings);

        assertThat(errors).isEmpty();
        assertThat(warnings).containsExactly("Transponder 111 is used by more than one entry: Jane Doe, John Smith");
    }
}
