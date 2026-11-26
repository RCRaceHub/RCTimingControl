package dev.monkeypatch.rctiming.domain.entryfeed;

import dev.monkeypatch.rctiming.config.LoopbackHosts;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventStatus;
import dev.monkeypatch.rctiming.domain.media.PhotoImportService;
import dev.monkeypatch.rctiming.domain.media.PhotoLink;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubEntryExport;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportResult;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportService;
import dev.monkeypatch.rctiming.security.TokenCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Pulls an event's entries from a URL (#42). RCTC runs on a venue laptop that booking systems usually can't
 * reach, so it fetches their Entry Export v1 file instead of being sent it, and imports it with
 * {@link RaceHubImportService}.
 *
 * <p>"Fetch now" never imports by itself: it holds the file for an official, who previews and confirms it. The
 * automatic fetch imports a new revision when it applies cleanly, and holds it for an official when something
 * (a class with no club link, an invalid row) would block it. A revision already applied is a no-op.
 *
 * <p>The network call never runs inside a database transaction, and no failure is thrown to the caller: it is
 * recorded on the feed and shown on the event. Recording an outcome reads the feed again and saves it in one
 * transaction ({@link #update}), so it cannot overwrite a change an official made during the fetch.
 *
 * <p>When the automatic fetch applies a revision it also fetches the headshots and car photos the revision names
 * (#811), straight away, because a booking system's picture links may expire within hours. Only missing pictures are
 * filled, so an official's own upload is never replaced; a failed picture is noted on the feed and never undoes
 * the entries.
 */
@Service
public class EntryFeedService {

    private static final Logger log = LoggerFactory.getLogger(EntryFeedService.class);
    private static final int MAX_ERROR_LENGTH = 500;
    /** Ends the note left on a feed when some of an applied revision's pictures were not fetched. */
    private static final String PICTURES_NOT_FETCHED = "couldn't be fetched";
    private static final Actor FEED_ACTOR = Actor.system("entry-feed");
    /** Pictures fetched per call to {@link PhotoImportService}, so the time budget is checked between chunks. */
    private static final int PICTURE_CHUNK = 10;
    /** The longest one revision's pictures may hold a scheduler thread; the rest are reported, not fetched. */
    private static final Duration PICTURE_BUDGET = Duration.ofMinutes(2);

    private final EntryFeedRepository feedRepository;
    private final EventRepository eventRepository;
    private final EntryFeedClient client;
    private final RaceHubImportService importService;
    private final TokenCipher tokenCipher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final RoundRepository roundRepository;
    private final PhotoImportService photoImport;

    public EntryFeedService(EntryFeedRepository feedRepository, EventRepository eventRepository,
                            EntryFeedClient client, RaceHubImportService importService, TokenCipher tokenCipher,
                            ObjectMapper objectMapper, TransactionTemplate transactions,
                            AuditService audit, RoundRepository roundRepository,
                            PhotoImportService photoImport) {
        this.feedRepository = feedRepository;
        this.eventRepository = eventRepository;
        this.client = client;
        this.importService = importService;
        this.tokenCipher = tokenCipher;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.audit = audit;
        this.roundRepository = roundRepository;
        this.photoImport = photoImport;
    }

    /** The event's feed, or empty when none is set up. Throws a 404 if there is no such event. */
    public Optional<EntryFeed> find(long eventId) {
        requireEvent(eventId);
        return feedRepository.findByEventId(eventId);
    }

    /**
     * Saves the event's feed settings.
     *
     * @param token the access token: null keeps the saved one, blank removes it
     */
    @Transactional
    public EntryFeed save(Actor actor, long eventId, String url, String token, boolean autoFetch) {
        Event event = requireEvent(eventId);
        String checkedUrl = checkUrl(url).toString();
        Optional<EntryFeed> existing = feedRepository.findByEventId(eventId);
        Map<String, Object> before = existing.map(EntryFeedService::settingsOf).orElse(null);
        EntryFeed feed = existing.orElseGet(() -> {
            EntryFeed created = new EntryFeed();
            created.setEventId(eventId);
            return created;
        });
        if (!checkedUrl.equals(feed.getUrl())) {
            // Nothing fetched from another address belongs to this feed any more
            feed.setHeldDocument(null);
            feed.setHeldRevision(null);
            feed.setAppliedRevision(null);
            feed.setLastFetchAt(null);
            feed.setLastStatus(null);
            feed.setLastError(null);
        }
        feed.setUrl(checkedUrl);
        if (token != null) {
            String trimmed = token.trim();
            feed.setTokenEncrypted(trimmed.isEmpty() ? null : tokenCipher.encrypt(trimmed));
            feed.setTokenHint(trimmed.length() < 8 ? null : trimmed.substring(trimmed.length() - 4));
        }
        feed.setAutoFetch(autoFetch);
        EntryFeed saved = feedRepository.save(feed);
        // The token itself is never recorded, only whether one is saved and whether this save changed it
        Map<String, Object> after = settingsOf(saved);
        after.put("tokenChanged", token != null);
        audit.entry(actor, before == null ? "ENTRY_FEED_CREATED" : "ENTRY_FEED_UPDATED")
                .entity("entry_feed", saved.getId()).event(eventId)
                .summary("Saved the entry feed settings of " + event.getName())
                .before(before).after(after).record();
        return saved;
    }

    /**
     * Gives every feed whose address passes {@code addressMatches} the same new token, for a rotated RaceHub key.
     * Each feed keeps its address, settings and held file; only the encrypted token and its hint change. Runs in the
     * caller's request thread, in one transaction, and records nothing itself: the caller audits the rotation.
     *
     * @param newToken the plain token, or null to remove it
     * @return how many feeds changed
     */
    @Transactional
    public int replaceTokenWhere(Predicate<URI> addressMatches, String newToken) {
        int changed = 0;
        for (EntryFeed feed : feedRepository.findAll()) {
            URI address;
            try {
                address = URI.create(feed.getUrl());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!addressMatches.test(address)) {
                continue;
            }
            feed.setTokenEncrypted(newToken == null ? null : tokenCipher.encrypt(newToken));
            feed.setTokenHint(newToken == null || newToken.length() < 8 ? null : newToken.substring(newToken.length() - 4));
            // The old key's verdict no longer applies; the next fetch decides
            feed.setLastStatus(null);
            feed.setLastError(null);
            feedRepository.save(feed);
            changed++;
        }
        return changed;
    }

    /**
     * Removes the event's feed and any file it holds, and records {@code ENTRY_FEED_DELETED}. Nothing already imported
     * is touched.
     */
    @Transactional
    public void delete(Actor actor, long eventId) {
        Event event = requireEvent(eventId);
        Optional<EntryFeed> existing = feedRepository.findByEventId(eventId);
        feedRepository.deleteByEventId(eventId);
        existing.ifPresent(feed -> audit.entry(actor, "ENTRY_FEED_DELETED").entity("entry_feed", feed.getId())
                .event(eventId).summary("Removed the entry feed of " + event.getName())
                .before(settingsOf(feed)).record());
    }

    /** The feed's settings for an audit row: never the token, only whether one is saved. */
    private static Map<String, Object> settingsOf(EntryFeed feed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", feed.getUrl());
        m.put("autoFetch", feed.isAutoFetch());
        m.put("tokenSaved", feed.getTokenEncrypted() != null);
        return m;
    }

    /**
     * "Fetch now": downloads the file and, when it is a new revision, holds it for an official to preview and
     * confirm. Nothing is imported.
     */
    public EntryFeed fetchNow(long eventId) {
        EntryFeed feed = requireFeed(eventId);
        Download download = download(feed);
        if (download.problem != null) {
            return removedWhileFetching(recordFailure(feed, download.problem));
        }
        RaceHubEntryExport export = download.export;
        if (Objects.equals(export.revision(), feed.getAppliedRevision())) {
            return removedWhileFetching(recordUnchanged(feed));
        }
        try {
            // Checks the file can be imported at all (schema version, source) before holding it
            importService.preview(eventId, export);
        } catch (IllegalArgumentException e) {
            return removedWhileFetching(recordFailure(feed, new Problem(EntryFeedStatus.FAILED, e.getMessage())));
        }
        return removedWhileFetching(
                hold(feed, export, "Fetched revision " + export.revision() + ". Check it and confirm the import."));
    }

    /** The import the held file would make, without saving anything. */
    public RaceHubImportResult previewHeld(long eventId) {
        EntryFeed feed = requireFeed(eventId);
        return importService.preview(eventId, heldExport(feed));
    }

    /** Imports the held file. When something blocks it, nothing is saved and the file stays held. */
    public RaceHubImportResult applyHeld(Actor actor, long eventId) {
        EntryFeed feed = requireFeed(eventId);
        RaceHubEntryExport export = heldExport(feed);
        RaceHubImportResult result = importService.importEntries(actor, eventId, export, false);
        if (result.applied()) {
            recordApplied(feed, export.revision());
        }
        return result;
    }

    /**
     * "Update from RaceHub": does what the automatic poll does, now, for one event. A clean new revision is
     * imported, one that needs a decision is held for an official, and an unchanged one is reported as such. It
     * ignores the polling schedule and works whether or not auto-fetch is on. Runs on the request thread; blocks for
     * the feed client's timeouts, so it holds no transaction while it waits.
     *
     * @return the feed with the outcome of this fetch in {@code lastStatus} and {@code lastMessage}
     * @throws EntityNotFoundException if the event has no feed (HTTP 404)
     * @throws StateConflictException  if the event is completed, whose entries a late revision must not change
     *                                 (HTTP 409); an official can still fetch or import by hand
     */
    public EntryFeed updateNow(long eventId) {
        EntryFeed feed = requireFeed(eventId);
        if (eventRepository.findById(eventId).filter(e -> e.getStatus() == EventStatus.COMPLETED).isPresent()) {
            throw new StateConflictException("This event is completed, so RaceHub changes no longer update it. "
                    + "Fetch or import by hand if you need to.");
        }
        autoFetch(feed);
        return requireFeed(eventId);
    }

    /**
     * The automatic fetch: imports a new revision when it applies cleanly, and holds it for an official when
     * something would block it. Does nothing for a completed event: its results have been exported and its
     * championships count them, so a late revision from the booking system must not change its entries (an
     * official can still fetch or import by hand). Runs on the scheduler thread. Never throws.
     */
    public void autoFetch(EntryFeed feed) {
        try {
            if (eventRepository.findById(feed.getEventId()).filter(e -> e.getStatus() == EventStatus.COMPLETED)
                    .isPresent()) {
                return;
            }
            Download download = download(feed, true);
            if (download.problem != null) {
                recordFailure(feed, download.problem);
                return;
            }
            if (download.notModified) {
                recordUnchanged(feed);
                return;
            }
            RaceHubEntryExport export = download.export;
            if (Objects.equals(export.revision(), feed.getAppliedRevision())) {
                recordUnchanged(feed);
                return;
            }
            RaceHubImportResult result;
            try {
                Optional<String> afterHeats = changesAfterHeatsExist(feed.getEventId(), export);
                if (afterHeats.isPresent()) {
                    hold(feed, export, "Revision " + export.revision() + " needs an official: " + afterHeats.get());
                    return;
                }
                result = importService.importEntries(FEED_ACTOR, feed.getEventId(), export, false);
            } catch (IllegalArgumentException e) {
                recordFailure(feed, new Problem(EntryFeedStatus.FAILED, e.getMessage()));
                return;
            }
            if (result.applied()) {
                recordApplied(feed, export.revision(), changeSummary(result));
                log.info("Entry feed for event {} applied revision {}", feed.getEventId(), export.revision());
                fetchPictures(feed, export.revision(), result.photoLinks());
            } else {
                hold(feed, export, "Revision " + export.revision() + " needs an official: " + blockReason(result));
            }
        } catch (RuntimeException e) {
            log.warn("Entry feed for event {} failed", feed.getEventId(), e);
            recordFailure(feed, new Problem(EntryFeedStatus.FAILED,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * Downloads the pictures an applied revision named, with no transaction open (each is stored in its own short one).
     * Pictures already held are kept. It runs on the shared scheduler pool, so it works in chunks of
     * {@link #PICTURE_CHUNK} and stops after {@link #PICTURE_BUDGET}: a slow host can hold a thread for that long and
     * no longer, and whatever was not reached is counted in the note on the feed. The note stays until the next
     * revision (see {@link #recordUnchanged}); an official can import the file by hand to try again. A driver whose
     * name is not shown in full is skipped by design and is not counted as a failure.
     */
    private void fetchPictures(EntryFeed feed, Long revision, List<PhotoLink> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + PICTURE_BUDGET.toNanos();
        int stored = 0;
        int failed = 0;
        int notReached = 0;
        try {
            for (int from = 0; from < links.size(); from += PICTURE_CHUNK) {
                List<PhotoLink> chunk = links.subList(from, Math.min(links.size(), from + PICTURE_CHUNK));
                if (System.nanoTime() > deadline) {
                    notReached += chunk.size();
                    continue;
                }
                List<PhotoImportService.Outcome> outcomes =
                        photoImport.fetchAll(FEED_ACTOR, feed.getEventId(), chunk, false);
                for (PhotoImportService.Outcome outcome : outcomes) {
                    switch (outcome.status()) {
                        case STORED -> stored++;
                        case FAILED -> failed++;
                        default -> { }
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Entry feed pictures for event {} failed", feed.getEventId(), e);
            failed++;
        }
        log.info("Entry feed for event {} revision {}: {} pictures saved, {} failed, {} not reached",
                feed.getEventId(), revision, stored, failed, notReached);
        int missed = failed + notReached;
        if (missed > 0) {
            String what = (missed == 1 ? "1 picture " : missed + " pictures ") + PICTURES_NOT_FETCHED;
            String note = "Revision " + revision + " applied, but " + what + ". Import the file by hand to try again.";
            // Only while this revision is still the one applied and nothing is waiting for an official
            update(feed, f -> {
                if (Objects.equals(f.getAppliedRevision(), revision) && f.getHeldDocument() == null) {
                    f.setLastError(note);
                }
            });
        }
    }

    private record Problem(EntryFeedStatus status, String message) {
    }

    /** A fetch's outcome: the file, a problem, or neither when the feed said nothing is newer ({@code notModified}). */
    private record Download(RaceHubEntryExport export, Problem problem, boolean notModified) {

        Download(RaceHubEntryExport export, Problem problem) {
            this(export, problem, false);
        }
    }

    /** The newest revision this feed already holds, applied or waiting, to ask RaceHub only for something newer. */
    private static Long knownRevision(EntryFeed feed) {
        Long applied = feed.getAppliedRevision();
        Long held = feed.getHeldRevision();
        if (applied == null) {
            return held;
        }
        return held == null ? applied : Math.max(applied, held);
    }

    private Download download(EntryFeed feed) {
        return download(feed, false);
    }

    /**
     * Downloads the feed's file. With {@code conditional} the newest revision already held is sent as
     * {@code since_revision}, so an unchanged feed answers 304 and nothing is downloaded; only the automatic poll
     * and "Update from RaceHub" ask this way, while "Fetch now" always gets the whole file.
     */
    private Download download(EntryFeed feed, boolean conditional) {
        String token;
        try {
            token = feed.getTokenEncrypted() == null ? null : tokenCipher.decrypt(feed.getTokenEncrypted());
        } catch (TokenCipher.UnreadableTokenException e) {
            return new Download(null, new Problem(EntryFeedStatus.AUTH_FAILED, e.getMessage()));
        }
        EntryFeedClient.Result result = client.fetch(URI.create(feed.getUrl()), token,
                conditional ? knownRevision(feed) : null);
        return switch (result) {
            case EntryFeedClient.AuthFailed failed ->
                    new Download(null, new Problem(EntryFeedStatus.AUTH_FAILED, failed.message()));
            case EntryFeedClient.Failed failed -> new Download(null, new Problem(EntryFeedStatus.FAILED, failed.message()));
            case EntryFeedClient.Fetched fetched -> parse(fetched.body());
            case EntryFeedClient.NotModified notModified -> new Download(null, null, true);
        };
    }

    private Download parse(String body) {
        RaceHubEntryExport export;
        try {
            export = objectMapper.readValue(body, RaceHubEntryExport.class);
        } catch (JacksonException e) {
            return new Download(null, new Problem(EntryFeedStatus.FAILED, "The feed didn't answer with an entry file"));
        }
        if (export == null || export.revision() == null) {
            return new Download(null, new Problem(EntryFeedStatus.FAILED, "The feed's file has no revision"));
        }
        return new Download(export, null);
    }

    private Optional<EntryFeed> hold(EntryFeed feed, RaceHubEntryExport export, String message) {
        String document;
        try {
            // Only the fields the import reads are kept, so nothing personal the file carried is stored
            document = objectMapper.writeValueAsString(export);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not store the fetched file", e);
        }
        return update(feed, f -> {
            // The scheduler may have applied this revision while it was being fetched by hand (the feed as read by
            // update() is current), so it is no longer waiting for anyone
            if (Objects.equals(export.revision(), f.getAppliedRevision())) {
                if (f.getHeldDocument() == null) {
                    f.setLastStatus(EntryFeedStatus.UNCHANGED);
                    f.setLastError(null);
                }
                return;
            }
            f.setHeldDocument(document);
            f.setHeldRevision(export.revision());
            f.setLastStatus(EntryFeedStatus.WAITING);
            f.setLastError(message);
        });
    }

    private void recordApplied(EntryFeed feed, Long revision) {
        recordApplied(feed, revision, null);
    }

    /** {@code summary} says what the import changed ("2 new, 1 withdrawn"), shown beside the status; may be null. */
    private void recordApplied(EntryFeed feed, Long revision, String summary) {
        update(feed, f -> {
            f.setAppliedRevision(revision);
            f.setHeldDocument(null);
            f.setHeldRevision(null);
            f.setLastStatus(EntryFeedStatus.APPLIED);
            f.setLastError(summary);
        });
    }

    /**
     * Once the run order has been built, new entries and changed ones wait for an official instead of arriving by
     * themselves, so nobody is slipped into or moved between heats unseen. Withdrawals still apply straight away:
     * the car just drops out. Before the run order exists everything that imports cleanly is applied.
     *
     * @return why the revision must be held, or empty when it can be applied now
     */
    private Optional<String> changesAfterHeatsExist(long eventId, RaceHubEntryExport export) {
        if (!roundRepository.existsByEventId(eventId)) {
            return Optional.empty();
        }
        RaceHubImportResult preview = importService.preview(eventId, export);
        if (preview.blocked()) {
            return Optional.empty();
        }
        RaceHubImportResult.Summary summary = preview.summary();
        int arriving = summary.created() + summary.updated();
        if (arriving == 0) {
            return Optional.empty();
        }
        return Optional.of(arriving + (arriving == 1 ? " entry is" : " entries are")
                + " new or changed and the run order is already built, so check where "
                + (arriving == 1 ? "it" : "they") + " should go");
    }

    /** What an applied import changed in words, or null when it changed nothing worth saying. */
    private static String changeSummary(RaceHubImportResult result) {
        RaceHubImportResult.Summary s = result.summary();
        if (s == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (s.created() > 0) {
            parts.add(s.created() + " new");
        }
        if (s.updated() > 0) {
            parts.add(s.updated() + " changed");
        }
        if (s.withdrawn() > 0) {
            parts.add(s.withdrawn() + " withdrawn");
        }
        return parts.isEmpty() ? null : String.join(", ", parts) + " (revision " + result.revision() + ")";
    }

    private Optional<EntryFeed> recordUnchanged(EntryFeed feed) {
        return update(feed, f -> {
            // A file waiting for an official stays waiting; there's just nothing newer. A note about pictures that
            // could not be fetched stays too, or the next poll would wipe it before anyone read it.
            boolean pictureNote = f.getLastStatus() == EntryFeedStatus.APPLIED && f.getLastError() != null
                    && f.getLastError().contains(PICTURES_NOT_FETCHED);
            if (f.getHeldDocument() == null && !pictureNote) {
                f.setLastStatus(EntryFeedStatus.UNCHANGED);
                f.setLastError(null);
            }
        });
    }

    private Optional<EntryFeed> recordFailure(EntryFeed feed, Problem problem) {
        String message = problem.message() == null ? "The fetch failed" : problem.message();
        return update(feed, f -> {
            f.setLastStatus(problem.status());
            f.setLastError(message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message);
        });
    }

    /**
     * Applies a change to the feed as it is now, since an official may have changed its settings during the
     * fetch. The read and the save are one transaction, so a change made in between is never overwritten.
     * A feed removed meanwhile stays removed (empty result), and one moved to another URL keeps no outcome
     * from the old one (it is returned as it is).
     */
    private Optional<EntryFeed> update(EntryFeed fetched, java.util.function.Consumer<EntryFeed> change) {
        return transactions.execute(status -> {
            Optional<EntryFeed> current = feedRepository.findByEventId(fetched.getEventId());
            if (current.isEmpty()) {
                return Optional.empty();
            }
            EntryFeed feed = current.get();
            if (!Objects.equals(feed.getUrl(), fetched.getUrl())) {
                // The URL changed during the fetch, so the outcome belongs to the old address
                return Optional.of(feed);
            }
            Instant now = Instant.now();
            change.accept(feed);
            feed.setLastFetchAt(now);
            return Optional.of(feedRepository.save(feed));
        });
    }

    /** "Fetch now" answers with the feed's new state; a feed deleted during the fetch is a 404, not a null. */
    private EntryFeed removedWhileFetching(Optional<EntryFeed> recorded) {
        return recorded.orElseThrow(() -> new EntityNotFoundException("The entry feed was removed while it was fetched"));
    }

    private RaceHubEntryExport heldExport(EntryFeed feed) {
        if (feed.getHeldDocument() == null) {
            throw new EntityNotFoundException("No fetched file is waiting for this event");
        }
        try {
            return objectMapper.readValue(feed.getHeldDocument(), RaceHubEntryExport.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("The held file can't be read", e);
        }
    }

    private static String blockReason(RaceHubImportResult result) {
        if (!result.unmappedClasses().isEmpty()) {
            int count = result.unmappedClasses().size();
            return count + (count == 1 ? " class needs" : " classes need") + " linking";
        }
        return result.errors().isEmpty() ? "the import is blocked" : result.errors().get(0);
    }

    private Event requireEvent(long eventId) {
        return eventRepository.getOrThrow(eventId);
    }

    private EntryFeed requireFeed(long eventId) {
        requireEvent(eventId);
        return feedRepository.findByEventId(eventId)
                .orElseThrow(() -> new EntityNotFoundException("This event has no entry feed"));
    }

    /** The token goes with every request, so only https is allowed, or plain http to this machine for testing. */
    static URI checkUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("The feed URL is required");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("The feed URL isn't a valid address");
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null) {
            throw new IllegalArgumentException("The feed URL needs to be a full address, such as https://…");
        }
        boolean https = "https".equalsIgnoreCase(scheme);
        boolean localHttp = "http".equalsIgnoreCase(scheme) && LoopbackHosts.isLoopback(uri.getHost());
        if (!https && !localHttp) {
            throw new IllegalArgumentException("The feed URL must start with https://, so the token isn't sent "
                    + "in the clear (plain http works only to this computer)");
        }
        return uri;
    }
}
