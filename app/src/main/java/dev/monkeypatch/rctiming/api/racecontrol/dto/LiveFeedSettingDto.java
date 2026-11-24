package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotNull;

/**
 * An event's live feed switch: the body of {@code PUT /events/{eventId}/live-feed} and its answer.
 *
 * @param enabled whether the event's races are sent to the relay while they run
 */
public record LiveFeedSettingDto(@NotNull Boolean enabled) {
}
