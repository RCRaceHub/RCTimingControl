package dev.monkeypatch.rctiming.domain.competitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/** What the announcer says for a name (#119, #120). Names in, speech text out. */
class SpeechNameTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" is spoken as \"{1}\"")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            // Already fine: left exactly as typed
            "Alex Rowe                      | Alex Rowe",
            "Siobhan Keane                  | Siobhan Keane",
            "Ada King-Lovelace              | Ada King-Lovelace",
            "Pat O'Brien                    | Pat O'Brien",
            "McDonald                       | McDonald",
            "J. Smith                       | J. Smith",
            "AJ Smith                       | AJ Smith",
            "DJ Rowe                        | DJ Rowe",
            // Spacing
            "Alex     Rowe                  | Alex Rowe",
            // All capitals are spoken in normal capitalisation
            "ALEX ROWE                      | Alex Rowe",
            "AL LEE                         | Al Lee",
            "PAT O'BRIEN                    | Pat O'Brien",
            "ADA KING-LOVELACE              | Ada King-Lovelace",
            "SIOBHAN KEANE                  | Siobhan Keane",
            // One capitalised word in a mixed-case name; short ones such as initials stay
            "Alex ROWE                      | Alex Rowe",
            "Alex ROWE JR                   | Alex Rowe JR",
            // Bracketed nickname or club tag: speak only the name
            "Alex Rowe (Wyvern)             | Alex Rowe",
            "Alex Rowe [Wyvern RC]          | Alex Rowe",
            "Alex (Lightning) Rowe          | Alex Rowe",
            "ALEX ROWE (WYVERN)             | Alex Rowe",
            // Characters that are not spoken well
            "Alex Rowe 🏎️                  | Alex Rowe",
            "Alex *Rowe*                    | Alex Rowe",
            "Alex_Rowe                      | Alex Rowe",
            "Alex / Rowe                    | Alex Rowe",
            "Alex - Rowe                    | Alex Rowe",
            "#12 Alex Rowe                  | 12 Alex Rowe",
            // Accents are kept, and capitals with accents are folded
            "René Müller                    | René Müller",
            "RENÉ MÜLLER                    | René Müller",
            "ÉLODIE                         | Élodie",
    })
    void tidiesANameForSpeech(String name, String spoken) {
        assertThat(SpeechName.tidy(name)).isEqualTo(spoken);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" is left as given")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "(Wyvern)       | (Wyvern)",
            "***            | ***",
            "🏎️            | 🏎️",
    })
    void neverLeavesNothingToSay(String name, String spoken) {
        assertThat(SpeechName.tidy(name)).isEqualTo(spoken);
    }

    @Test
    void anyKindOfSpaceIsOnePlainSpace() {
        assertThat(SpeechName.tidy("Alex\u2003\u2003Rowe")).isEqualTo("Alex Rowe");
        assertThat(SpeechName.tidy("Alex\u00a0Rowe")).isEqualTo("Alex Rowe");
        assertThat(SpeechName.tidy("Alex\tRowe\n")).isEqualTo("Alex Rowe");
        assertThat(SpeechName.tidy("ALEX\u2003ROWE")).isEqualTo("Alex Rowe");
    }

    @Test
    void trimsTheEnds() {
        assertThat(SpeechName.tidy("  Alex   Rowe  ")).isEqualTo("Alex Rowe");
        assertThat(SpeechName.tidy("  ...  ")).isEqualTo("...");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void aMissingNameIsEmpty(String name) {
        assertThat(SpeechName.tidy(name)).isEmpty();
    }

    @Test
    void aSpokenNameIsUsedExactlyAsTyped() {
        assertThat(SpeechName.of("  SHIV-awn  KEEN  ", "Siobhan Keane")).isEqualTo("SHIV-awn  KEEN");
        assertThat(SpeechName.of("Al (ex)", "Alex")).isEqualTo("Al (ex)");
    }

    @Test
    void withoutASpokenNameTheDisplayNameIsTidied() {
        assertThat(SpeechName.of(null, "ALEX ROWE (Wyvern)")).isEqualTo("Alex Rowe");
        assertThat(SpeechName.of("   ", "ALEX  ROWE")).isEqualTo("Alex Rowe");
    }

    @Test
    void doesNotChangeTheDisplayName() {
        Competitor c = new Competitor();
        c.setDisplayName("ALEX ROWE (Wyvern)");

        assertThat(c.speechName()).isEqualTo("Alex Rowe");
        assertThat(c.getDisplayName()).isEqualTo("ALEX ROWE (Wyvern)");
    }

    @Test
    void firstNameIsTheWordBeforeTheFirstSpace() {
        assertThat(SpeechName.firstName("Alex Rowe")).isEqualTo("Alex");
        assertThat(SpeechName.firstName("  Mary Jane Watson ")).isEqualTo("Mary");
        assertThat(SpeechName.firstName("Zoe")).isEqualTo("Zoe");
        assertThat(SpeechName.firstName(null)).isEmpty();
    }
}
