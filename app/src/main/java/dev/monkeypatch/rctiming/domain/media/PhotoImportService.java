package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fetches the headshots and car photos an entry import named (#699) and stores them as an upload is stored. An import
 * only reports the links ({@link PhotoLink}); the page then asks for them here, so no download ever holds the import's
 * write transaction. Called by {@code PhotoImportController}, ADMIN only.
 *
 * <p>This class has no transaction of its own: each picture is downloaded with none open and then stored by
 * {@link DriverMediaService} in its own short write transaction, so one failure never undoes the pictures already
 * stored. A picture is skipped, not fetched, when
 * <ul>
 *   <li>the target is not in the event (a link replayed from another event is refused, not guessed at);</li>
 *   <li>the competitor's name is not shown in full (#265), the same rule the boards apply to pictures;</li>
 *   <li>one is already there and the caller did not ask to replace it, so an official's own upload is kept.</li>
 * </ul>
 * Audit rows name the competitor or entry by id and never carry the address.
 */
@Service
public class PhotoImportService {

    /** The most links one call takes. */
    public static final int MAX_LINKS = 1000;

    private static final int IMAGE_MAX_BYTES = (int) DriverMediaService.IMAGE_MAX_BYTES;

    private final EventRepository events;
    private final EntryRepository entries;
    private final CompetitorRepository competitors;
    private final DriverMediaRepository media;
    private final EntryPhotoRepository photos;
    private final DriverMediaService mediaService;
    private final PhotoFetcher fetcher;

    public PhotoImportService(EventRepository events, EntryRepository entries, CompetitorRepository competitors,
                              DriverMediaRepository media, EntryPhotoRepository photos,
                              DriverMediaService mediaService, PhotoFetcher fetcher) {
        this.events = events;
        this.entries = entries;
        this.competitors = competitors;
        this.media = media;
        this.photos = photos;
        this.mediaService = mediaService;
        this.fetcher = fetcher;
    }

    /** What happened to one link. {@code storedUrl} is the picture's address here, set only when {@code STORED}. */
    public record Outcome(PhotoLink link, Status status, String message, String storedUrl) {
    }

    public enum Status {
        /** Fetched and saved. */
        STORED,
        /** Left alone on purpose; the message says why. */
        SKIPPED,
        /** The address could not be fetched or was not a usable picture; the message says why. */
        FAILED
    }

    /**
     * Fetches and stores each link, one at a time, in the order given.
     *
     * @param replaceExisting replace a picture already held; false keeps it and skips the link
     * @return one outcome per link, in order
     * @throws IllegalArgumentException (400) if there are more than {@link #MAX_LINKS} links
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if there is no such event
     */
    public List<Outcome> fetchAll(Actor actor, long eventId, List<PhotoLink> links, boolean replaceExisting) {
        if (links.size() > MAX_LINKS) {
            throw new IllegalArgumentException("At most " + MAX_LINKS + " pictures can be fetched at once");
        }
        Scope scope = scope(eventId);
        List<Outcome> out = new ArrayList<>();
        for (PhotoLink link : links) {
            out.add(fetchOne(actor, link, scope, replaceExisting));
        }
        return out;
    }

    /** What belongs to the event and what may not be shown, read once for the whole call. */
    private record Scope(Set<Long> entryIds, Set<Long> competitorIds, Set<Long> hidden, Map<Long, Long> competitorOfEntry) {
    }

    private Scope scope(long eventId) {
        events.getOrThrow(eventId);
        List<Entry> all = entries.findByEventId(eventId);
        return new Scope(
                all.stream().map(Entry::getId).collect(Collectors.toSet()),
                all.stream().map(Entry::getCompetitorId).collect(Collectors.toSet()),
                competitors.findNameVisibilityNotShow().keySet(),
                all.stream().collect(Collectors.toMap(Entry::getId, Entry::getCompetitorId)));
    }

    private Outcome fetchOne(Actor actor, PhotoLink link, Scope scope, boolean replace) {
        boolean headshot = link.kind() == PhotoLink.Kind.HEADSHOT;
        Long competitorId = headshot ? link.competitorId() : scope.competitorOfEntry().get(link.entryId());
        boolean inEvent = headshot ? scope.competitorIds().contains(link.competitorId())
                : scope.entryIds().contains(link.entryId());
        if (link.url() == null || PhotoLink.problem(link.url()) != null || !inEvent || competitorId == null) {
            return new Outcome(link, Status.FAILED, "Not a usable link for this event", null);
        }
        if (scope.hidden().contains(competitorId)) {
            return new Outcome(link, Status.SKIPPED, "The driver's name is not shown in full", null);
        }
        if (!replace && (headshot
                ? media.findByCompetitorId(link.competitorId()).map(DriverMedia::getHeadshotUrl).isPresent()
                : photos.findByEntryId(link.entryId()).isPresent())) {
            return new Outcome(link, Status.SKIPPED, "A picture is already held", null);
        }
        try {
            byte[] bytes = fetcher.fetch(link.url(), IMAGE_MAX_BYTES);
            String stored = headshot
                    ? mediaService.importHeadshot(actor, link.competitorId(), bytes).headshotUrl()
                    : mediaService.importCarPhoto(actor, link.entryId(), bytes);
            return new Outcome(link, Status.STORED, null, stored);
        } catch (PhotoFetcher.FetchException e) {
            return new Outcome(link, Status.FAILED, e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            return new Outcome(link, Status.FAILED, "The file is not a usable PNG or JPEG picture", null);
        } catch (RuntimeException e) {
            // A malformed image or a storage fault on one link must not abort the links after it
            return new Outcome(link, Status.FAILED, "The picture could not be saved", null);
        }
    }
}
