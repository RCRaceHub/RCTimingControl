package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.TestJson;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract with RaceHub: files written by RaceHub itself (not by hand) must be read by RCTC's own types the way
 * RaceHub means them. If RaceHub changes what it sends, these fail until the fixtures are regenerated and the
 * reading is fixed.
 */
class RaceHubGeneratedFixtureTest {

    private final ObjectMapper objectMapper = TestJson.mapper();

    @Test
    void readsRaceHubsEntryExport() throws IOException {
        RaceHubEntryExport export = objectMapper.readValue(
                text("entries-v1-racehub-generated.json"), RaceHubEntryExport.class);

        assertThat(export.schemaVersion()).isEqualTo(1);
        assertThat(export.source()).isEqualTo("RACEHUB");
        assertThat(export.event().id()).isEqualTo("1");
        assertThat(export.event().name()).isEqualTo("Club Champs Round 3");
        assertThat(export.event().eventLocalDate()).isEqualTo("2026-12-06");
        assertThat(export.event().timezone()).isEqualTo("Europe/London");
        assertThat(export.revision()).isEqualTo(6);
        assertThat(export.entries()).hasSize(3);

        RaceHubEntryExport.ExportEntry arrived = export.entries().getFirst();
        assertThat(arrived.entryId()).isEqualTo("1");
        assertThat(arrived.entryVersion()).isEqualTo(3);
        assertThat(arrived.entryStatus()).isEqualTo("CONFIRMED");
        assertThat(arrived.raceDayStatus()).isEqualTo("ARRIVED");
        assertThat(arrived.driverDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(arrived.brcaNumber()).isEqualTo("12345");
        assertThat(arrived.primaryTransponder()).isEqualTo("1234567");
        assertThat(arrived.secondaryTransponder()).isEqualTo("7654321");
        assertThat(arrived.rcClassName()).isEqualTo("Fictional Buggy contract");

        assertThat(export.entries().get(1).clubCarRequested()).isTrue();
        assertThat(export.entries().get(2).entryStatus()).isEqualTo("WITHDRAWN");
    }

    @Test
    void readsRaceHubsEventListing() throws IOException {
        RaceHubEventListing listing = objectMapper.readValue(
                text("events-listing-racehub-generated.json"), RaceHubEventListing.class);

        assertThat(listing.events()).hasSize(1);
        RaceHubEventListing.ListedEvent event = listing.events().getFirst();
        assertThat(event.id()).isEqualTo("1");
        assertThat(event.name()).isEqualTo("Club Champs Round 3");
        assertThat(event.eventLocalDate()).isEqualTo("2026-12-06");
        assertThat(event.venue()).isEqualTo("Fictional Venue contract");
        assertThat(event.entryCount()).isEqualTo(2);
        assertThat(URI.create(event.entriesUrl()).getPath()).isEqualTo("/api/rctc/v1/events/1/entries");
    }

    /** The id RaceHub lists an event under is the id its entry export carries, since RCTC links on it. */
    @Test
    void theListedIdIsTheExportsEventId() throws IOException {
        RaceHubEventListing listing = objectMapper.readValue(
                text("events-listing-racehub-generated.json"), RaceHubEventListing.class);
        RaceHubEntryExport export = objectMapper.readValue(
                text("entries-v1-racehub-generated.json"), RaceHubEntryExport.class);

        assertThat(listing.events().getFirst().id()).isEqualTo(export.event().id());
    }

    private static String text(String name) throws IOException {
        return new ClassPathResource("racehub/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
