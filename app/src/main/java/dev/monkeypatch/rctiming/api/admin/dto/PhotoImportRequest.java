package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.media.PhotoLink;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The pictures to fetch after an entry import (#699): the {@code photoLinks} the import answered with, sent back
 * unchanged. {@code replaceExisting} replaces a picture already held; without it that link is skipped.
 */
public record PhotoImportRequest(@NotNull List<@NotNull @Valid Link> links, boolean replaceExisting) {

    /** One link: {@code kind} is {@code HEADSHOT} (with {@code competitorId}) or {@code CAR_PHOTO} (with {@code entryId}). */
    public record Link(@NotNull PhotoLink.Kind kind, Long competitorId, Long entryId, String url) {
        public PhotoLink toLink() {
            return new PhotoLink(kind, competitorId, entryId, url);
        }
    }
}
