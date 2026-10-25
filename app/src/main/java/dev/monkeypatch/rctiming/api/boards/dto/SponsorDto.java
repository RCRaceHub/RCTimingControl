package dev.monkeypatch.rctiming.api.boards.dto;

import dev.monkeypatch.rctiming.domain.sponsor.EventSponsor;

/** A sponsor as the boards and the admin screen see it: its id, name and logo address (null without a logo). */
public record SponsorDto(Long id, String name, String logoUrl) {

    public static SponsorDto from(EventSponsor sponsor) {
        return new SponsorDto(sponsor.getId(), sponsor.getName(), sponsor.getLogoUrl());
    }
}
