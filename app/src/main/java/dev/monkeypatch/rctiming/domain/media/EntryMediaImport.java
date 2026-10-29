package dev.monkeypatch.rctiming.domain.media;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * What the RaceHub and CSV entry imports share about pictures (#699): checking the headshot address, car photo address
 * and car colours a row names, saving the colours, and listing the links to fetch afterwards. Colours are applied in the
 * import's own transaction because they are only text; pictures are never fetched there (see
 * {@link PhotoImportService}).
 *
 * <p>A picture problem never blocks an import: {@link #check} words it as a warning and {@link #apply} leaves the
 * unusable part out. A row that names nothing changes nothing, so an import never clears a picture or colours an
 * official added by hand; colours the file leaves out are left alone, whereas colours it gives replace the entry's.
 */
@Component
public class EntryMediaImport {

    private final DriverMediaService media;

    public EntryMediaImport(DriverMediaService media) {
        this.media = media;
    }

    /**
     * The problems with the media a row names, each starting with {@code label} (such as {@code entry e7} or
     * {@code Line 4}); empty when it is fine or names nothing.
     */
    public List<String> check(String label, String headshotUrl, String carPhotoUrl, List<String> carColours) {
        List<String> problems = new ArrayList<>();
        String headshot = PhotoLink.problem(headshotUrl);
        if (headshot != null) {
            problems.add(label + ": the headshot address " + headshot);
        }
        String car = PhotoLink.problem(carPhotoUrl);
        if (car != null) {
            problems.add(label + ": the car photo address " + car);
        }
        String colours = carColours == null ? null : DriverMediaService.colourProblem(carColours);
        if (colours != null) {
            problems.add(label + ": " + colours);
        }
        return problems;
    }

    /**
     * Saves the colours the row gave (when they differ from the entry's) and returns the pictures to fetch. Runs in
     * the import's transaction, after the competitor and entry are saved.
     *
     * @param carColours null when the file has no colours for the row
     */
    public List<PhotoLink> apply(Actor actor, long competitorId, long entryId,
                                 String headshotUrl, String carPhotoUrl, List<String> carColours) {
        if (carColours != null && DriverMediaService.colourProblem(carColours) == null
                && !DriverMediaService.cleanColours(carColours).equals(media.carColours(entryId))) {
            media.setCarColours(actor, entryId, carColours);
        }
        List<PhotoLink> links = new ArrayList<>();
        if (headshotUrl != null && !headshotUrl.isBlank() && PhotoLink.problem(headshotUrl) == null) {
            links.add(new PhotoLink(PhotoLink.Kind.HEADSHOT, competitorId, null, headshotUrl.trim()));
        }
        if (carPhotoUrl != null && !carPhotoUrl.isBlank() && PhotoLink.problem(carPhotoUrl) == null) {
            links.add(new PhotoLink(PhotoLink.Kind.CAR_PHOTO, null, entryId, carPhotoUrl.trim()));
        }
        return links;
    }
}
