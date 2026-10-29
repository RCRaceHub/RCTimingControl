package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/** The body of {@code POST /race-control/race/{raceId}/featured-driver}: the entry to show a lower third for (#533). */
public record FeaturedDriverRequest(
        @NotNull Long entryId
) {
}
