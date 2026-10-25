package dev.monkeypatch.rctiming.domain.sponsor;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.infrastructure.storage.ObjectStorageService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * The sponsors of an event (#518): the list officials keep and the overlay's sponsor bar reads. Called by {@code
 * AdminEventSponsorController} (each change is one write transaction with an audit row) and by the anonymous boards
 * endpoint (reads). Logos are PNG, JPEG, WebP or SVG up to 2 MB, kept in object storage like the club logo; a replaced
 * or deleted sponsor's old file stays in storage. A sponsor is a business, so nothing here is personal data.
 */
@Service
@Transactional
public class EventSponsorService {

    private static final Set<String> LOGO_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");
    private static final long LOGO_MAX_BYTES = 2 * 1024 * 1024L;
    private static final int NAME_MAX = 255;

    private final EventSponsorRepository repository;
    private final EventRepository eventRepository;
    private final ObjectStorageService storage;
    private final AuditService audit;

    public EventSponsorService(EventSponsorRepository repository, EventRepository eventRepository,
                               ObjectStorageService storage, AuditService audit) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.storage = storage;
        this.audit = audit;
    }

    /**
     * The event's sponsors in display order.
     *
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if there is no such event
     */
    @ReadTransaction
    public List<EventSponsor> list(long eventId) {
        eventRepository.requireExists(eventId);
        return repository.findByEventId(eventId);
    }

    /**
     * Adds a sponsor at the end of the event's list.
     *
     * @throws IllegalArgumentException (400) if the name is blank or too long
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if there is no such event
     */
    public EventSponsor create(Actor actor, long eventId, String name) {
        eventRepository.requireExists(eventId);
        EventSponsor sponsor = new EventSponsor();
        sponsor.setEventId(eventId);
        sponsor.setName(cleanName(name));
        sponsor.setSortOrder(repository.findByEventId(eventId).stream()
                .mapToInt(EventSponsor::getSortOrder).max().orElse(-1) + 1);
        EventSponsor saved = repository.save(sponsor);
        audit.entry(actor, "EVENT_SPONSOR_ADDED").entity("event_sponsor", saved.getId()).event(eventId)
                .summary("Added the sponsor " + saved.getName()).record();
        return saved;
    }

    /** Renames a sponsor. @throws IllegalArgumentException (400) for a bad name; EntityNotFoundException (404) */
    public EventSponsor rename(Actor actor, long eventId, long sponsorId, String name) {
        EventSponsor sponsor = ofEvent(eventId, sponsorId);
        String before = sponsor.getName();
        sponsor.setName(cleanName(name));
        repository.save(sponsor);
        audit.entry(actor, "EVENT_SPONSOR_RENAMED").entity("event_sponsor", sponsorId).event(eventId)
                .summary("Renamed the sponsor " + before + " to " + sponsor.getName()).record();
        return sponsor;
    }

    /**
     * Sets the order of the event's sponsors: {@code orderedIds} lists them first to last, and any left out keep
     * their relative order after them.
     *
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if an id is not one of this event's sponsors
     */
    public List<EventSponsor> reorder(Actor actor, long eventId, List<Long> orderedIds) {
        List<EventSponsor> all = repository.findByEventId(eventId);
        for (Long id : orderedIds) {
            ofEvent(eventId, id);
        }
        int position = 0;
        for (Long id : orderedIds) {
            for (EventSponsor sponsor : all) {
                if (sponsor.getId().equals(id)) {
                    sponsor.setSortOrder(position++);
                }
            }
        }
        for (EventSponsor sponsor : all) {
            if (!orderedIds.contains(sponsor.getId())) {
                sponsor.setSortOrder(position++);
            }
        }
        repository.saveAll(all);
        audit.entry(actor, "EVENT_SPONSORS_REORDERED").entity("event", eventId).event(eventId)
                .summary("Changed the order of the event's sponsors").record();
        return repository.findByEventId(eventId);
    }

    /** Removes a sponsor. @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) */
    public void delete(Actor actor, long eventId, long sponsorId) {
        EventSponsor sponsor = ofEvent(eventId, sponsorId);
        repository.deleteById(sponsorId);
        audit.entry(actor, "EVENT_SPONSOR_REMOVED").entity("event_sponsor", sponsorId).event(eventId)
                .summary("Removed the sponsor " + sponsor.getName()).record();
    }

    /**
     * Stores the uploaded image as the sponsor's logo.
     *
     * @throws IllegalArgumentException (400) if the file is missing, not an allowed image type, over 2 MB or unreadable
     * @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if the sponsor is not on this event
     */
    public EventSponsor uploadLogo(Actor actor, long eventId, long sponsorId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file part is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!LOGO_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported content type: " + file.getContentType() + ". Allowed: " + LOGO_TYPES);
        }
        if (file.getSize() > LOGO_MAX_BYTES) {
            throw new IllegalArgumentException("File too large: max " + LOGO_MAX_BYTES + " bytes");
        }
        EventSponsor sponsor = ofEvent(eventId, sponsorId);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage(), e);
        }
        String extension = switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "svg";
        };
        sponsor.setLogoUrl(storage.upload("sponsors/" + sponsorId + "-" + System.currentTimeMillis() + "." + extension, bytes));
        repository.save(sponsor);
        audit.entry(actor, "EVENT_SPONSOR_LOGO_CHANGED").entity("event_sponsor", sponsorId).event(eventId)
                .summary("Replaced the logo of the sponsor " + sponsor.getName()).record();
        return sponsor;
    }

    private EventSponsor ofEvent(long eventId, long sponsorId) {
        EventSponsor sponsor = repository.getOrThrow(sponsorId);
        if (!sponsor.getEventId().equals(eventId)) {
            // Same answer as a missing sponsor, so the id of another event's sponsor is not confirmed
            throw new EntityNotFoundException("Event sponsor not found: " + sponsorId);
        }
        return sponsor;
    }

    private static String cleanName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("A sponsor needs a name of up to " + NAME_MAX + " characters");
        }
        return trimmed;
    }
}
