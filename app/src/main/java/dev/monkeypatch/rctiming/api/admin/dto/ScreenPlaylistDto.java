package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.PlaylistItem;
import dev.monkeypatch.rctiming.screens.ScreenPlaylist;

import java.util.List;

/** A saved screen playlist (#708). */
public record ScreenPlaylistDto(Long id, String name, int defaultDuration, List<PlaylistItem> items) {

    public static ScreenPlaylistDto from(ScreenPlaylist playlist, List<PlaylistItem> items) {
        return new ScreenPlaylistDto(playlist.getId(), playlist.getName(), playlist.getDefaultDuration(), items);
    }
}
