package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.media.PhotoImportService;
import dev.monkeypatch.rctiming.domain.media.PhotoImportService.Status;
import dev.monkeypatch.rctiming.domain.media.PhotoLink;

import java.util.List;

/**
 * What fetching the pictures of an entry import did (#699): one {@link Item} per link in the order sent, and the
 * counts. {@code storedUrl} on an item is the picture's address here, for the page to cut out the background of.
 */
public record PhotoImportResponse(int stored, int skipped, int failed, List<Item> items) {

    /** One link and its outcome; the address that was fetched is not echoed back. */
    public record Item(PhotoLink.Kind kind, Long competitorId, Long entryId, Status status, String message,
                       String storedUrl) {
    }

    public static PhotoImportResponse from(List<PhotoImportService.Outcome> outcomes) {
        List<Item> items = outcomes.stream()
                .map(o -> new Item(o.link().kind(), o.link().competitorId(), o.link().entryId(), o.status(),
                        o.message(), o.storedUrl()))
                .toList();
        return new PhotoImportResponse(count(outcomes, Status.STORED), count(outcomes, Status.SKIPPED),
                count(outcomes, Status.FAILED), items);
    }

    private static int count(List<PhotoImportService.Outcome> outcomes, Status status) {
        return (int) outcomes.stream().filter(o -> o.status() == status).count();
    }
}
