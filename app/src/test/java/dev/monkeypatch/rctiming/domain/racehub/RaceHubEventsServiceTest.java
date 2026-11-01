package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedClient;
import dev.monkeypatch.rctiming.domain.entryfeed.EntryFeedService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventService;
import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsEndpoint;
import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The "Find RaceHub events" picker: listing what RaceHub offers, marking events already taken, and taking one
 * (creating the event, pointing its entry feed at RaceHub with the club's key).
 */
class RaceHubEventsServiceTest {

    private static final String EVENTS_URL = "https://racehub.example/api/rctc/v1/events?state=ready";
    private static final String ENTRIES_URL = "https://racehub.example/api/rctc/v1/events/E-7/entries";
    private static final Actor ACTOR = Actor.official(1);

    private final ClubProfileService clubProfileService = mock(ClubProfileService.class);
    private final EntryFeedClient client = mock(EntryFeedClient.class);
    private final EntryFeedService entryFeedService = mock(EntryFeedService.class);
    private final EventService eventService = mock(EventService.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private RaceHubEventsService service(String token) {
        RaceHubResultsEndpoint endpoint = new RaceHubResultsEndpoint(
                new RaceHubResultsProperties(null, token), clubProfileService);
        return new RaceHubEventsService(clubProfileService, endpoint, client, entryFeedService, eventService,
                eventRepository, objectMapper, transactions);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void runTransactionsInline() {
        when(clubProfileService.getRaceHubEventsUrl()).thenReturn(Optional.of(EVENTS_URL));
        when(transactions.execute(any())).thenAnswer(call ->
                ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
        when(eventRepository.findByRacehubEventId(anyString())).thenReturn(Optional.empty());
    }

    private static String listing(String entriesUrl) {
        return """
                {"events": [{"id": "E-7", "name": "Club Champs", "event_local_date": "2026-11-14",
                             "venue": "Test Track", "entry_count": 42, "entries_url": "%s"}]}
                """.formatted(entriesUrl);
    }

    private void raceHubAnswers(String body) {
        when(client.fetch(eq(URI.create(EVENTS_URL)), eq("key"))).thenReturn(new EntryFeedClient.Fetched(body));
    }

    @Test
    void listingMarksAnEventAlreadyLinkedToAnRctcEvent() {
        raceHubAnswers(listing(ENTRIES_URL));
        Event linked = new Event();
        linked.setId(9L);
        when(eventRepository.findByRacehubEventId("E-7")).thenReturn(Optional.of(linked));

        RaceHubEventsService.Listing result = service("key").list();

        assertThat(result).isInstanceOf(RaceHubEventsService.Offered.class);
        assertThat(((RaceHubEventsService.Offered) result).events()).singleElement().satisfies(e -> {
            assertThat(e.raceHubEventId()).isEqualTo("E-7");
            assertThat(e.name()).isEqualTo("Club Champs");
            assertThat(e.entryCount()).isEqualTo(42);
            assertThat(e.linkedEventId()).isEqualTo(9L);
        });
    }

    @Test
    void listingSendsTheClubKeyAndNeedsAnAddress() {
        when(clubProfileService.getRaceHubEventsUrl()).thenReturn(Optional.empty());

        assertThat(service("key").list()).isEqualTo(
                new RaceHubEventsService.Failure("Set the RaceHub events address first."));
    }

    @Test
    void listingNeedsTheClubKey() {
        RaceHubEventsService.Listing result = service(null).list();

        assertThat(result).isInstanceOf(RaceHubEventsService.Failure.class);
        assertThat(((RaceHubEventsService.Failure) result).message()).contains("rctiming.racehub.token");
        verify(client, never()).fetch(any(), any());
    }

    @Test
    void aRefusedKeyAndAnUnreadableAnswerComeBackAsFailures() {
        when(client.fetch(any(), eq("key"))).thenReturn(new EntryFeedClient.AuthFailed("refused"));
        assertThat(service("key").list()).isEqualTo(new RaceHubEventsService.Failure("refused"));

        raceHubAnswers("not json");
        assertThat(service("key").list()).isInstanceOf(RaceHubEventsService.Failure.class);
    }

    @Test
    void takingAnEventCreatesItAndPointsItsFeedAtRaceHubWithAutoFetchOn() {
        raceHubAnswers(listing(ENTRIES_URL));
        Event created = new Event();
        created.setId(5L);
        when(eventService.create(ACTOR, "Club Champs", LocalDate.of(2026, 11, 14), null)).thenReturn(created);
        when(eventRepository.save(created)).thenReturn(created);

        Event taken = service("key").take(ACTOR, "E-7");

        assertThat(taken.getId()).isEqualTo(5L);
        assertThat(created.getRacehubEventId()).isEqualTo("E-7");
        verify(entryFeedService).save(ACTOR, 5L, ENTRIES_URL, "key", true);
    }

    @Test
    void takingAnEventAlreadyLinkedReusesItAndCreatesNothing() {
        raceHubAnswers(listing(ENTRIES_URL));
        Event existing = new Event();
        existing.setId(9L);
        when(eventRepository.findByRacehubEventId("E-7")).thenReturn(Optional.of(existing));

        assertThat(service("key").take(ACTOR, "E-7").getId()).isEqualTo(9L);

        verify(eventService, never()).create(any(), anyString(), any(), any());
        verify(entryFeedService).save(ACTOR, 9L, ENTRIES_URL, "key", true);
    }

    @Test
    void anEntriesAddressOnAnotherHostIsRefusedSoTheKeyIsNotSentThere() {
        raceHubAnswers(listing("https://elsewhere.example/entries"));

        assertThatThrownBy(() -> service("key").take(ACTOR, "E-7"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different host");

        verify(entryFeedService, never()).save(any(), anyLong(), anyString(), any(), anyBoolean());
        verify(eventService, never()).create(any(), anyString(), any(), any());
    }

    @Test
    void anEventRaceHubDoesNotOfferIsNotFound() {
        raceHubAnswers(listing(ENTRIES_URL));

        assertThatThrownBy(() -> service("key").take(ACTOR, "E-99")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void takingWhenRaceHubCannotBeAskedIsAConflict() {
        when(client.fetch(any(), any())).thenReturn(new EntryFeedClient.Failed("Couldn't reach the feed"));

        assertThatThrownBy(() -> service("key").take(ACTOR, "E-7")).isInstanceOf(StateConflictException.class)
                .hasMessageContaining("reach");
        verify(entryFeedService, never()).save(any(), anyLong(), anyString(), isNull(), anyBoolean());
    }
}
