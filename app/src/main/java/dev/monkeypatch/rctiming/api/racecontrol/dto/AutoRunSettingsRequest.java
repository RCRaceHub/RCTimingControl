package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code PUT /settings}.
 *
 * @param gapSeconds       the time between a race finishing and the next starting, 0 to 3600
 * @param holdBeforeFinals whether auto run stops after the last qualifier
 * @param autoStart        whether auto run switches itself on when a race is started by hand
 */
public record AutoRunSettingsRequest(@NotNull @Min(0) @Max(3600) Integer gapSeconds,
                                     @NotNull Boolean holdBeforeFinals,
                                     @NotNull Boolean autoStart) {}
