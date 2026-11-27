package dev.monkeypatch.rctiming.domain.racehub;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shape RCTC expects of RaceHub's list of events open for sign-up: booking fields are read when present, absent
 * ones are null, and fields RCTC does not know are ignored. A fixture RaceHub can be tested against.
 */
class RaceHubOpenEventListingTest {

    private RaceHubEventListing listing() throws IOException {
        try (var in = getClass().getResourceAsStream("/racehub/events-open-v1.json")) {
            return JsonMapper.builder().build().readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    RaceHubEventListing.class);
        }
    }

    @Test
    void anOpenEventCarriesItsBookingWindowAndNoReleaseTime() throws IOException {
        RaceHubEventListing.ListedEvent open = listing().events().get(0);

        assertThat(open.bookingState()).isEqualTo("open");
        assertThat(open.bookingCloseAt()).isEqualTo("2027-03-01T18:00:00+00:00");
        assertThat(open.releasedAt()).isNull();
        assertThat(open.entriesUrl()).endsWith("/events/21/entries");
    }

    @Test
    void aNotYetOpenEventMayHaveNoCloseDateOrVenue() throws IOException {
        RaceHubEventListing.ListedEvent notOpen = listing().events().get(1);

        assertThat(notOpen.bookingState()).isEqualTo("not_open");
        assertThat(notOpen.bookingCloseAt()).isNull();
        assertThat(notOpen.venue()).isNull();
    }

    @Test
    void aClosedButReleasedEventAndUnknownFieldsAreRead() throws IOException {
        RaceHubEventListing.ListedEvent closed = listing().events().get(2);

        assertThat(closed.bookingState()).isEqualTo("closed");
        assertThat(closed.releasedAt()).isNotNull();
        assertThat(closed.entryCount()).isEqualTo(42);
    }
}
