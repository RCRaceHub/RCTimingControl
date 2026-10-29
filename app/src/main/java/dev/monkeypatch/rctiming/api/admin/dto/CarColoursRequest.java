package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** The body of a car colours change: up to four {@code #rrggbb} values, or none to clear them. */
public record CarColoursRequest(@Size(max = 4) List<String> colours) {}
