package dev.monkeypatch.rctiming.screens;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One page of a saved playlist, as kept in {@code display_playlists.items}: an absolute http(s) URL, how long it stays
 * up in whole seconds (null for the playlist's default), and a label and id the screen echoes back in its status.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaylistItem(String url, Integer duration, String label, String id) {
}
