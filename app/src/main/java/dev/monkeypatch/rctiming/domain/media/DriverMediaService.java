package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.NameVisibility;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.infrastructure.storage.ObjectStorageService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The optional pictures and sponsors the boards can show for a driver (#520): a headshot and the driver's own
 * sponsors per competitor, and a photo of the car and its colours per entry. Called by {@code AdminDriverMediaController} (each change is
 * one write transaction with an audit row), by the anonymise service (which erases a competitor's media) and by the
 * anonymous boards endpoint (reads).
 *
 * <p>A headshot is personal data. It is kept only when an official adds it, it is deleted from storage, not just
 * unlinked, when it is replaced, removed or the competitor is anonymised, and {@link #publicForRace} never returns a
 * competitor's pictures or sponsors while their name is not shown in full (#265). Files are named by a random id, so
 * their addresses cannot be guessed, but anyone who has an address can open it. PNG or JPEG up to 2 MB only, no SVG
 * (which could carry a script) and no WebP. Each upload is decoded and written out again, which proves it is an image
 * and drops any metadata such as the GPS position a phone adds. A replaced file is deleted only once the change has
 * committed, and an upload whose change rolls back is deleted again, so a failure never leaves a record pointing at a
 * missing file. Audit rows name the competitor or entry by id, never by name.
 */
@Service
@Transactional
public class DriverMediaService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg");
    private static final long IMAGE_MAX_BYTES = 2 * 1024 * 1024L;
    /** Where pictures are kept in object storage; only files under it are ever deleted by this class. */
    private static final String PHOTO_FOLDER = "photos";
    private static final int SPONSORS_MAX = 5;
    private static final int SPONSOR_NAME_MAX = 100;
    private static final int CAR_COLOURS_MAX = 4;
    private static final Pattern COLOUR = Pattern.compile("#[0-9a-f]{6}");

    /** What the admin screen shows for a competitor. */
    public record CompetitorMediaView(String headshotUrl, List<String> sponsors) {}

    /** What a public screen may show for one entry of a race; each field is null or empty when there is nothing to show. */
    public record PublicMedia(Long entryId, String headshotUrl, String carPhotoUrl, List<String> sponsors, List<String> carColours) {}

    private final DriverMediaRepository media;
    private final EntryPhotoRepository photos;
    private final EntryCarColoursRepository carColours;
    private final CompetitorRepository competitors;
    private final EntryRepository entries;
    private final RaceEntryRepository raceEntries;
    private final ObjectStorageService storage;
    private final AuditService audit;

    public DriverMediaService(DriverMediaRepository media, EntryPhotoRepository photos,
                              EntryCarColoursRepository carColours,
                              CompetitorRepository competitors, EntryRepository entries,
                              RaceEntryRepository raceEntries, ObjectStorageService storage, AuditService audit) {
        this.media = media;
        this.photos = photos;
        this.carColours = carColours;
        this.competitors = competitors;
        this.entries = entries;
        this.raceEntries = raceEntries;
        this.storage = storage;
        this.audit = audit;
    }

    /**
     * A competitor's headshot and sponsors.
     *
     * @throws EntityNotFoundException (404) if there is no such competitor
     */
    @ReadTransaction
    public CompetitorMediaView forCompetitor(long competitorId) {
        competitors.requireExists(competitorId);
        return media.findByCompetitorId(competitorId)
                .map(m -> new CompetitorMediaView(m.getHeadshotUrl(), splitSponsors(m.getSponsors())))
                .orElse(new CompetitorMediaView(null, List.of()));
    }

    /**
     * An entry's car photo address, or null.
     *
     * @throws EntityNotFoundException (404) if there is no such entry
     */
    @ReadTransaction
    public String carPhoto(long entryId) {
        entries.requireExists(entryId);
        return photos.findByEntryId(entryId).map(EntryPhoto::getCarPhotoUrl).orElse(null);
    }

    /**
     * An entry's car colours as {@code #rrggbb} values, empty when none are set.
     *
     * @throws EntityNotFoundException (404) if there is no such entry
     */
    @ReadTransaction
    public List<String> carColours(long entryId) {
        entries.requireExists(entryId);
        return carColours.findByEntryId(entryId).map(c -> splitColours(c.getColours())).orElse(List.of());
    }

    /**
     * Replaces the entry's car colours; an empty list clears them. Colours are not personal data, so a change is
     * audited without any name.
     *
     * @param colours up to four {@code #rrggbb} values (any case; stored lower case); blanks and repeats are dropped
     * @return the colours as stored
     * @throws IllegalArgumentException (400) if there are too many or one is not {@code #rrggbb}
     * @throws EntityNotFoundException (404) if there is no such entry
     */
    public List<String> setCarColours(Actor actor, long entryId, List<String> colours) {
        Entry entry = entries.getOrThrow(entryId);
        List<String> clean = colours == null ? List.of() : colours.stream().filter(c -> c != null)
                .map(c -> c.trim().toLowerCase()).filter(c -> !c.isEmpty()).distinct().toList();
        if (clean.size() > CAR_COLOURS_MAX || !clean.stream().allMatch(c -> COLOUR.matcher(c).matches())) {
            throw new IllegalArgumentException("A car can have up to " + CAR_COLOURS_MAX + " colours, each as #rrggbb");
        }
        if (clean.isEmpty()) {
            carColours.findByEntryId(entryId).ifPresent(c -> carColours.deleteById(c.getId()));
        } else {
            EntryCarColours row = carColours.findByEntryId(entryId).orElseGet(() -> {
                EntryCarColours created = new EntryCarColours();
                created.setEntryId(entryId);
                return created;
            });
            row.setColours(String.join(",", clean));
            carColours.save(row);
        }
        audit.entry(actor, "ENTRY_CAR_COLOURS_CHANGED").entity("entry", entryId).event(entry.getEventId())
                .summary("Changed the car colours of entry #" + entryId).record();
        return clean;
    }

    /**
     * Replaces the competitor's sponsors.
     *
     * @param names up to five names of up to 100 characters; blanks are dropped
     * @throws IllegalArgumentException (400) if there are too many or one is too long
     * @throws EntityNotFoundException (404) if there is no such competitor
     */
    public CompetitorMediaView setSponsors(Actor actor, long competitorId, List<String> names) {
        competitors.requireExists(competitorId);
        List<String> clean = names.stream().filter(n -> n != null).map(String::trim).filter(n -> !n.isEmpty()).toList();
        if (clean.size() > SPONSORS_MAX || clean.stream().anyMatch(n -> n.length() > SPONSOR_NAME_MAX || n.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException(
                    "A driver can have up to " + SPONSORS_MAX + " sponsors of up to " + SPONSOR_NAME_MAX + " characters");
        }
        DriverMedia row = mediaRow(competitorId);
        row.setSponsors(String.join("\n", clean));
        media.save(row);
        audit.entry(actor, "DRIVER_SPONSORS_CHANGED").entity("competitor", competitorId)
                .summary("Changed the driver sponsors of competitor #" + competitorId).record();
        return new CompetitorMediaView(row.getHeadshotUrl(), clean);
    }

    /**
     * Stores the uploaded image as the competitor's headshot, deleting the previous one.
     *
     * @throws IllegalArgumentException (400) if the file is missing, not PNG or JPEG, over 2 MB or unreadable
     * @throws EntityNotFoundException (404) if there is no such competitor
     */
    public CompetitorMediaView uploadHeadshot(Actor actor, long competitorId, MultipartFile file) {
        competitors.requireExists(competitorId);
        String url = store(file);
        DriverMedia row = mediaRow(competitorId);
        deleteAfterCommit(row.getHeadshotUrl());
        row.setHeadshotUrl(url);
        media.save(row);
        audit.entry(actor, "DRIVER_HEADSHOT_CHANGED").entity("competitor", competitorId)
                .summary("Replaced the headshot of competitor #" + competitorId).record();
        return new CompetitorMediaView(url, splitSponsors(row.getSponsors()));
    }

    /**
     * Removes the competitor's headshot, from storage too once the change commits.
     *
     * @throws EntityNotFoundException (404) if there is no such competitor
     */
    public CompetitorMediaView deleteHeadshot(Actor actor, long competitorId) {
        competitors.requireExists(competitorId);
        DriverMedia row = mediaRow(competitorId);
        deleteAfterCommit(row.getHeadshotUrl());
        row.setHeadshotUrl(null);
        media.save(row);
        audit.entry(actor, "DRIVER_HEADSHOT_REMOVED").entity("competitor", competitorId)
                .summary("Removed the headshot of competitor #" + competitorId).record();
        return new CompetitorMediaView(null, splitSponsors(row.getSponsors()));
    }

    /**
     * Stores the uploaded image as the entry's car photo, deleting the previous one.
     *
     * @return the photo's address
     * @throws IllegalArgumentException (400) for a missing, wrong type, oversized or unreadable file
     * @throws EntityNotFoundException (404) if there is no such entry
     */
    public String uploadCarPhoto(Actor actor, long entryId, MultipartFile file) {
        Entry entry = entries.getOrThrow(entryId);
        String url = store(file);
        EntryPhoto photo = photos.findByEntryId(entryId).orElseGet(() -> {
            EntryPhoto created = new EntryPhoto();
            created.setEntryId(entryId);
            return created;
        });
        deleteAfterCommit(photo.getCarPhotoUrl());
        photo.setCarPhotoUrl(url);
        photos.save(photo);
        audit.entry(actor, "ENTRY_CAR_PHOTO_CHANGED").entity("entry", entryId).event(entry.getEventId())
                .summary("Replaced the car photo of entry #" + entryId).record();
        return url;
    }

    /**
     * Removes the entry's car photo, from storage too once the change commits.
     *
     * @throws EntityNotFoundException (404) if there is no such entry
     */
    public void deleteCarPhoto(Actor actor, long entryId) {
        Entry entry = entries.getOrThrow(entryId);
        photos.findByEntryId(entryId).ifPresent(photo -> {
            deleteAfterCommit(photo.getCarPhotoUrl());
            photos.deleteById(photo.getId());
            audit.entry(actor, "ENTRY_CAR_PHOTO_REMOVED").entity("entry", entryId).event(entry.getEventId())
                    .summary("Removed the car photo of entry #" + entryId).record();
        });
    }

    /**
     * Deletes everything held for a competitor: the headshot file, their sponsors and the car photos of all their
     * entries. Called by the anonymise service in its transaction; it records no audit row of its own, since the
     * anonymise row covers it.
     */
    public void eraseForCompetitor(long competitorId) {
        media.findByCompetitorId(competitorId).ifPresent(row -> {
            deleteAfterCommit(row.getHeadshotUrl());
            media.deleteById(row.getId());
        });
        List<Long> entryIds = entries.findByCompetitorId(competitorId).stream().map(Entry::getId).toList();
        // Car colours are not personal data and go with their entry, so they are left alone
        photos.findByEntryIds(entryIds).forEach(photo -> {
            deleteAfterCommit(photo.getCarPhotoUrl());
            photos.deleteById(photo.getId());
        });
    }

    /**
     * What a public screen may show for each car in a race. A competitor whose name is not shown in full gets nothing
     * at all (#265), so a hidden or initials-only driver cannot be recognised from their picture or sponsors. Cars with
     * nothing to show are left out.
     *
     * @return by entry id, in the race's grid order
     */
    @ReadTransaction
    public Map<Long, PublicMedia> publicForRace(long raceId) {
        List<Long> entryIds = raceEntries.findByRaceIdOrderByGridPosition(raceId).stream().map(RaceEntry::getEntryId).toList();
        Map<Long, Long> competitorOf = new HashMap<>();
        entries.findAllById(entryIds).forEach(e -> competitorOf.put(e.getId(), e.getCompetitorId()));
        Map<Long, NameVisibility> restricted = competitors.findNameVisibilityNotShow();
        Map<Long, DriverMedia> mediaOf = media.findByCompetitorIds(competitorOf.values()).stream()
                .collect(Collectors.toMap(DriverMedia::getCompetitorId, m -> m));
        Map<Long, String> carOf = photos.findByEntryIds(entryIds).stream()
                .collect(Collectors.toMap(EntryPhoto::getEntryId, EntryPhoto::getCarPhotoUrl));
        Map<Long, List<String>> coloursOf = carColours.findByEntryIds(entryIds).stream()
                .collect(Collectors.toMap(EntryCarColours::getEntryId, c -> splitColours(c.getColours())));
        Map<Long, PublicMedia> out = new LinkedHashMap<>();
        for (Long entryId : entryIds) {
            Long competitorId = competitorOf.get(entryId);
            List<String> colours = coloursOf.getOrDefault(entryId, List.of());
            if (competitorId == null) {
                continue;
            }
            if (restricted.containsKey(competitorId)) {
                // Colours are not personal data, so a hidden driver's car still shows them
                if (!colours.isEmpty()) {
                    out.put(entryId, new PublicMedia(entryId, null, null, List.of(), colours));
                }
                continue;
            }
            DriverMedia m = mediaOf.get(competitorId);
            String headshot = m == null ? null : m.getHeadshotUrl();
            List<String> sponsors = m == null ? List.of() : splitSponsors(m.getSponsors());
            String car = carOf.get(entryId);
            if (headshot != null || car != null || !sponsors.isEmpty() || !colours.isEmpty()) {
                out.put(entryId, new PublicMedia(entryId, headshot, car, sponsors, colours));
            }
        }
        return out;
    }

    private DriverMedia mediaRow(long competitorId) {
        return media.findByCompetitorId(competitorId).orElseGet(() -> {
            DriverMedia created = new DriverMedia();
            created.setCompetitorId(competitorId);
            return created;
        });
    }

    /** Deletes a stored picture once the surrounding transaction has committed (at once when there is none). */
    private void deleteAfterCommit(String url) {
        if (url == null || !url.contains("/" + PHOTO_FOLDER + "/")) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.delete(url);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(url);
            }
        });
    }

    private String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file part is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported content type: " + file.getContentType() + ". Allowed: " + IMAGE_TYPES);
        }
        if (file.getSize() > IMAGE_MAX_BYTES) {
            throw new IllegalArgumentException("File too large: max " + IMAGE_MAX_BYTES + " bytes");
        }
        byte[] bytes = reencode(file, contentType);
        String extension = contentType.equals("image/png") ? "png" : "jpg";
        String url = storage.upload(PHOTO_FOLDER + "/" + UUID.randomUUID() + "." + extension, bytes);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // A change that rolls back must not leave this file behind with nothing pointing at it
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        storage.delete(url);
                    }
                }
            });
        }
        return url;
    }

    /** Decodes the upload and writes it out again, so only pixels are kept; fails for anything that is not an image. */
    private static byte[] reencode(MultipartFile file, String contentType) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (image == null) {
                throw new IllegalArgumentException("The file is not a readable " + contentType + " image");
            }
            boolean png = contentType.equals("image/png");
            BufferedImage out = image;
            if (!png && image.getType() != BufferedImage.TYPE_INT_RGB) {
                // JPEG has no transparency
                out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                out.getGraphics().drawImage(image, 0, 0, java.awt.Color.WHITE, null);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(out, png ? "png" : "jpg", bytes)) {
                throw new IllegalArgumentException("The file is not a readable " + contentType + " image");
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage(), e);
        }
    }

    private static List<String> splitColours(String stored) {
        return stored == null || stored.isBlank() ? List.of() : Arrays.asList(stored.split(","));
    }

    private static List<String> splitSponsors(String stored) {
        return stored == null || stored.isBlank()
                ? List.of() : Arrays.stream(stored.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
