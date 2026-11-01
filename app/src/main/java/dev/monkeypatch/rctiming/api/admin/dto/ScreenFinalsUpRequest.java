package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.ScreenMessage;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code POST /api/v1/admin/screens/finals-up} (#716): the finals playlist to push (left out to announce
 * only) and the announcement to put up after it. The announcement is always an {@code announce}; {@code seconds} left
 * out means until cleared.
 */
public record ScreenFinalsUpRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> screenIds, Long playlistId,
                                    @Size(max = 1000) String title, @Size(max = 2000) String text,
                                    @Size(max = 20) String level, Integer seconds) {

    /** The announcement in the form the service takes. */
    public ScreenMessage message() {
        return new ScreenMessage(title, text, "announce", level, seconds);
    }
}
