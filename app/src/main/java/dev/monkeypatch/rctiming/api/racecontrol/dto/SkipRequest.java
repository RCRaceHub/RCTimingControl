package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /skip-to}.
 *
 * @param seconds how long from now the next race should start, 0 to 600
 */
public record SkipRequest(@NotNull @Min(0) @Max(600) Integer seconds) {}
