package dev.monkeypatch.rctiming.domain.competitor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicNamesTest {

    @Test
    void showLeavesTheNameAsItIs() {
        assertThat(PublicNames.format(NameVisibility.SHOW, "Ada Lovelace", 7)).isEqualTo("Ada Lovelace");
        assertThat(PublicNames.format(null, "Ada Lovelace", 7)).isEqualTo("Ada Lovelace");
    }

    @Test
    void initialsTakeTheFirstLetterOfEachWord() {
        assertThat(PublicNames.format(NameVisibility.INITIALS, "Ada King-Lovelace", 7)).isEqualTo("A. K.");
        assertThat(PublicNames.format(NameVisibility.INITIALS, "  ada   byron lovelace ", null))
                .isEqualTo("A. B. L.");
        assertThat(PublicNames.format(NameVisibility.INITIALS, "Ada Lovelace (Ads) [Fenland]", null))
                .isEqualTo("A. L.");
        assertThat(PublicNames.format(NameVisibility.INITIALS, "Élodie Ñandú", null)).isEqualTo("É. Ñ.");
    }

    @Test
    void initialsOfAnythingWithoutLettersFallBackToTheHiddenName() {
        assertThat(PublicNames.format(NameVisibility.INITIALS, "(?)", null)).isEqualTo("Driver");
    }

    @Test
    void hiddenUsesTheCarNumberWhenThereIsOne() {
        assertThat(PublicNames.format(NameVisibility.HIDE, "Ada Lovelace", "12")).isEqualTo("Driver 12");
        assertThat(PublicNames.format(NameVisibility.HIDE, "Ada Lovelace", null)).isEqualTo("Driver");
        assertThat(PublicNames.format(NameVisibility.HIDE, "Ada Lovelace", " ")).isEqualTo("Driver");
    }

    @Test
    void noNameStaysNoName() {
        assertThat(PublicNames.format(NameVisibility.HIDE, null, 3)).isNull();
    }
}
