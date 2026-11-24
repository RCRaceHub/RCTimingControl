package dev.monkeypatch.rctiming.api.admin.dto;

import java.util.List;

/** The answer for car colours: {@code #rrggbb} values in order, empty when none are set. */
public record CarColoursResponse(List<String> colours) {}
