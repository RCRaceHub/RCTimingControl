package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.ScreenMessage;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code POST /api/v1/admin/screens/message} (#716): a full-screen message for the screens listed.
 * {@code style} is {@code hold} or {@code announce}, {@code level} {@code info}, {@code warning} or {@code urgent};
 * {@code seconds} left out means until cleared. The lengths and values are checked again by the service, which answers 400 with the reason.
 */
public record ScreenMessageRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> screenIds, @Size(max = 1000) String title,
                                   @Size(max = 2000) String text, @Size(max = 20) String style,
                                   @Size(max = 20) String level, Integer seconds) {

    /** The message in the form the service takes. */
    public ScreenMessage message() {
        return new ScreenMessage(title, text, style, level, seconds);
    }
}
