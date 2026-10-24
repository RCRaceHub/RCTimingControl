package dev.monkeypatch.rctiming.domain.competitor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryTagsTest {

    @Test
    void parseReadsTheStoredListAndIgnoresBlanksAndRepeats() {
        assertThat(CategoryTags.parse(" Junior, vet ,,JUNIOR,F-Grade")).containsExactly("Junior", "vet", "F-Grade");
        assertThat(CategoryTags.parse(null)).isEmpty();
        assertThat(CategoryTags.parse("  ")).isEmpty();
    }

    @Test
    void normaliseTidiesTypedTagsAndKeepsTheFirstSpelling() {
        assertThat(CategoryTags.normalise(List.of("  Junior ", "junior", "", "F  Grade")))
                .containsExactly("Junior", "F Grade");
    }

    @Test
    void normaliseRefusesTooManyLongOrCommaTags() {
        assertThatThrownBy(() -> CategoryTags.normalise(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CategoryTags.normalise(List.of("x".repeat(31))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CategoryTags.normalise(List.of("Junior, Vet")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatGivesNullForNoTags() {
        assertThat(CategoryTags.format(List.of())).isNull();
        assertThat(CategoryTags.format(List.of("Junior", "Vet"))).isEqualTo("Junior,Vet");
    }
}
