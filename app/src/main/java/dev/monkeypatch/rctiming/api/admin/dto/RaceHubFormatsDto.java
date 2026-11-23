package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.racehub.RaceHubFormatsService.ClubFormat;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubFormatsService.Listing;

import java.util.List;

/**
 * The club's RaceHub race formats for the "RaceHub classes" screen. {@code error} is set, and {@code formats} empty,
 * when RaceHub could not be asked.
 */
public record RaceHubFormatsDto(List<RaceHubFormatDto> formats, String error) {

    public static RaceHubFormatsDto from(Listing listing) {
        return new RaceHubFormatsDto(listing.formats().stream().map(RaceHubFormatDto::from).toList(), listing.error());
    }

    /** One format: RaceHub's fields and the RCTC format template it is linked to (null when not linked). */
    public record RaceHubFormatDto(String id, String name, boolean active, Long linkedTemplateId) {

        static RaceHubFormatDto from(ClubFormat f) {
            return new RaceHubFormatDto(f.id(), f.name(), f.active(), f.linkedTemplateId());
        }
    }
}
