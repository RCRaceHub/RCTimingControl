package dev.monkeypatch.rctiming.api.sponsor.dto;

import java.util.List;

/** The body of a reorder: the sponsor ids, first shown first. */
public record SponsorOrderRequest(List<Long> ids) {}
