package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.config.SecureEndpoint;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * The "Find RaceHub events" picker: asks RaceHub which of the club's events are ready for timing, and lets an
 * official take one. RCTC runs on a venue laptop that RaceHub can't reach, so it asks (pull) and RaceHub never
 * calls it.
 *
 * <p>Asking uses the club's key from the settings file ({@code rctiming.racehub.token}, the one results are sent
 * with) and the events address saved in the admin panel. Taking an event creates it in RCTC when it is not there
 * yet (matched by RaceHub's event id) and points its entry feed at the address RaceHub gave, with auto-fetch on, so
 * the entries arrive like any other feed. Nothing is imported until the feed's own rules apply: RaceHub's entries
 * are previewed or applied by {@link EntryFeedService}, and no event is ever created without an official taking it.
 *
 * <p>The network call is made outside any database transaction and never throws to the caller: a failure comes back
 * as a {@link Failure} with a message an official can read.
 */
@Service
public class RaceHubEventsService {

    private final ClubProfileService clubProfileService;
    private final RaceHubResultsEndpoint endpoint;
    private final EntryFeedClient client;
    private final EntryFeedService entryFeedService;
    private final EventService eventService;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public RaceHubEventsService(ClubProfileService clubProfileService, RaceHubResultsEndpoint endpoint,
                                EntryFeedClient client, EntryFeedService entryFeedService,
                                EventService eventService, EventRepository eventRepository,
                                ObjectMapper objectMapper, TransactionTemplate transactions) {
        this.clubProfileService = clubProfileService;
        this.endpoint = endpoint;
        this.client = client;
        this.entryFeedService = entryFeedService;
        this.eventService = eventService;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
    }

    /** The picker's settings: the saved events address and whether the club's key is set. Never includes the key. */
    public record Settings(String eventsUrl, boolean tokenSet) {
    }

    /** One event RaceHub offers, with the RCTC event it is already linked to, if any. */
    public record OfferedEvent(String raceHubEventId, String name, String eventDate, String venue,
                               Integer entryCount, Long linkedEventId) {
    }

    /** The outcome of asking RaceHub: the events on offer, or why they could not be listed. */
    public sealed interface Listing {
    }

    public record Offered(List<OfferedEvent> events) implements Listing {
    }

    public record Failure(String message) implements Listing, Asked {
    }

    public Settings settings() {
        return new Settings(clubProfileService.getRaceHubEventsUrl().orElse(null), endpoint.token() != null);
    }

    /**
     * Saves the address RaceHub lists ready events at; blank clears it.
     *
     * @throws IllegalArgumentException if it is not https (or http to this machine), or carries a user name (HTTP 400)
     */
    public void saveEventsUrl(Actor actor, String url) {
        if (url != null && !url.isBlank()) {
            RaceHubResultsEndpoint.parse(url);
        }
        clubProfileService.updateRaceHubEventsUrl(actor, url);
    }

    /** What asking RaceHub produced: its events as listed, or why not ({@link Failure}). */
    private sealed interface Asked {
    }

    private record AskedEvents(List<RaceHubEventListing.ListedEvent> events) implements Asked {
    }

    /**
     * Asks RaceHub for the events ready for timing. Blocks for up to the feed client's timeouts (5 seconds to
     * connect, 20 to read), so call it outside a transaction.
     */
    public Listing list() {
        return switch (ask()) {
            case Failure failure -> failure;
            case AskedEvents asked -> new Offered(asked.events().stream()
                    .map(e -> new OfferedEvent(e.id(), e.name(), e.eventLocalDate(), e.venue(), e.entryCount(),
                            eventRepository.findByRacehubEventId(e.id()).map(Event::getId).orElse(null)))
                    .toList());
        };
    }

    private Asked ask() {
        Optional<String> saved = clubProfileService.getRaceHubEventsUrl();
        if (saved.isEmpty()) {
            return new Failure("Set the RaceHub events address first.");
        }
        String token = endpoint.token();
        if (token == null) {
            return new Failure("The club's key for RaceHub is not set (rctiming.racehub.token in the settings file).");
        }
        URI url;
        try {
            url = RaceHubResultsEndpoint.parse(saved.get());
        } catch (IllegalArgumentException e) {
            return new Failure(e.getMessage());
        }
        return switch (client.fetch(url, token)) {
            case EntryFeedClient.Fetched fetched -> parse(fetched.body());
            case EntryFeedClient.AuthFailed failed -> new Failure(failed.message());
            case EntryFeedClient.Failed failed -> new Failure(failed.message());
        };
    }

    private Asked parse(String body) {
        RaceHubEventListing listing;
        try {
            listing = objectMapper.readValue(body, RaceHubEventListing.class);
        } catch (JacksonException e) {
            return new Failure("RaceHub's answer is not a list of events.");
        }
        List<RaceHubEventListing.ListedEvent> listed = listing.events() == null ? List.of() : listing.events();
        return new AskedEvents(listed.stream().filter(e -> e.id() != null && !e.id().isBlank()).toList());
    }

    /**
     * Takes one of the events RaceHub offers: creates the RCTC event if it is not there yet, then sets its entry
     * feed to the address RaceHub gave, with the club's key and auto-fetch on. Asks RaceHub again first, so only an
     * event it still offers can be taken and the address is never one the browser supplied. The club's key goes
     * only to the host the events address names, so an entries address on another host is refused.
     *
     * @return the RCTC event, new or existing
     * @throws EntityNotFoundException  if RaceHub does not offer that event any more (HTTP 404)
     * @throws StateConflictException   if RaceHub cannot be asked, with the reason (HTTP 409)
     * @throws IllegalArgumentException if the entries address is not usable or is on another host (HTTP 400)
     */
    public Event take(Actor actor, String raceHubEventId) {
        List<RaceHubEventListing.ListedEvent> events = switch (ask()) {
            case Failure failure -> throw new StateConflictException(failure.message());
            case AskedEvents asked -> asked.events();
        };
        RaceHubEventListing.ListedEvent chosen = events.stream().filter(e -> e.id().equals(raceHubEventId))
                .findFirst().orElseThrow(() -> new EntityNotFoundException(
                        "RaceHub does not list the event " + raceHubEventId + " as ready for timing"));
        URI entriesUrl = entriesUrl(chosen);
        return transactions.execute(status -> {
            Event event = eventRepository.findByRacehubEventId(chosen.id())
                    .orElseGet(() -> createEvent(actor, chosen));
            entryFeedService.save(actor, event.getId(), entriesUrl.toString(), endpoint.token(), true);
            return event;
        });
    }

    private Event createEvent(Actor actor, RaceHubEventListing.ListedEvent chosen) {
        LocalDate date;
        try {
            date = LocalDate.parse(chosen.eventLocalDate());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("RaceHub gave no usable date for " + chosen.name());
        }
        Event created = eventService.create(actor, chosen.name() == null || chosen.name().isBlank()
                ? "RaceHub event " + chosen.id() : chosen.name(), date, null);
        // Remember which RaceHub event this is, so taking it again finds this one and the first import agrees
        created.setRacehubEventId(chosen.id());
        return eventRepository.save(created);
    }

    private URI entriesUrl(RaceHubEventListing.ListedEvent chosen) {
        URI eventsUrl = RaceHubResultsEndpoint.parse(clubProfileService.getRaceHubEventsUrl().orElseThrow());
        URI entries;
        try {
            entries = URI.create(chosen.entriesUrl() == null ? "" : chosen.entriesUrl().strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("RaceHub gave an entries address that is not a full address");
        }
        if (entries.getHost() == null || entries.getUserInfo() != null) {
            throw new IllegalArgumentException("RaceHub gave an entries address that is not a full address");
        }
        SecureEndpoint.url(entries, "The entries address", "https", "http");
        boolean sameHost = entries.getScheme().equalsIgnoreCase(eventsUrl.getScheme())
                && entries.getHost().equalsIgnoreCase(eventsUrl.getHost())
                && entries.getPort() == eventsUrl.getPort();
        if (!sameHost) {
            throw new IllegalArgumentException("The entries address is on a different host from the events "
                    + "address, so the club's key is not sent to it");
        }
        return entries;
    }
}
