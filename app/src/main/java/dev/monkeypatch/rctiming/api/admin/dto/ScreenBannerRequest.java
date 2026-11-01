package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.ScreenBanner;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code POST /api/v1/admin/screens/banner} (#716): a strip along the bottom of the screens listed.
 * {@code seconds} left out means until cleared. The service checks the text and level again and answers 400 with the reason.
 */
public record ScreenBannerRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> screenIds, @Size(max = 2000) String text,
                                  @Size(max = 20) String level, Integer seconds) {

    /** The banner in the form the service takes. */
    public ScreenBanner banner() {
        return new ScreenBanner(text, level, seconds);
    }
}
