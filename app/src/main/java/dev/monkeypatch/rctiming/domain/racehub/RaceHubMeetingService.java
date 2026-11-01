package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.ExternalSources;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.EventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

/**
 * Makes the RCTC event (the "meeting") for an Entry Export file, so an official does not have to create one before
 * importing. The event takes the file's name and date. For a RaceHub file it is found again by RaceHub's event id
 * (the same match the "Find RaceHub events" picker uses) and linked to it; a file from another booking system makes
 * an unlinked event, because its ids mean nothing to RaceHub. Nothing is imported here: the official then imports
 * into the event as usual, with the same preview.
 *
 * <p>Called from {@code RaceHubMeetingController} on a Tomcat thread by an ADMIN; one write transaction.
 */
@Service
public class RaceHubMeetingService {

    private final EventRepository eventRepository;
    private final EventService eventService;

    public RaceHubMeetingService(EventRepository eventRepository, EventService eventService) {
        this.eventRepository = eventRepository;
        this.eventService = eventService;
    }

    /** The event for a file, and whether this call created it. */
    public record Meeting(Event event, boolean created) {
    }

    /**
     * Finds the event a RaceHub file belongs to, or creates it in DRAFT from the file's name and date.
     *
     * @throws IllegalArgumentException if the file is not a supported Entry Export or has no usable event date
     *         (HTTP 400)
     */
    @Transactional
    public Meeting findOrCreate(Actor actor, RaceHubEntryExport export) {
        if (export == null || !Objects.equals(export.schemaVersion(), RaceHubImportService.SUPPORTED_SCHEMA_VERSION)
                || export.event() == null) {
            throw new IllegalArgumentException("This is not a supported Entry Export file (schema_version 1 with an event)");
        }
        boolean fromRaceHub = ExternalSources.RACEHUB.equals(RaceHubImportService.sourceOf(export));
        String racehubEventId = fromRaceHub ? blankToNull(export.event().id()) : null;
        if (racehubEventId != null) {
            Optional<Event> existing = eventRepository.findByRacehubEventId(racehubEventId);
            if (existing.isPresent()) {
                return new Meeting(existing.get(), false);
            }
        }
        LocalDate date;
        try {
            date = LocalDate.parse(export.event().eventLocalDate());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("The file gives no usable event date");
        }
        String name = blankToNull(export.event().name());
        Event created = eventService.create(actor, name == null ? "Imported event" : name, date, null);
        if (racehubEventId != null) {
            // Remember which RaceHub event this is, so the import and the picker both find this one again
            created.setRacehubEventId(racehubEventId);
            created = eventRepository.save(created);
        }
        return new Meeting(created, true);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
