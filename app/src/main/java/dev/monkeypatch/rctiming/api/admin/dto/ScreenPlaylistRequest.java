package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.PlaylistItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The body of {@code POST} and {@code PUT /api/v1/admin/screen-playlists} (#708): a name, the default seconds a page
 * stays up, and the pages in order. The pages are checked when it is saved.
 */
public record ScreenPlaylistRequest(@NotBlank @Size(max = 255) String name, int defaultDuration,
                                    @NotNull List<PlaylistItem> items) {
}
