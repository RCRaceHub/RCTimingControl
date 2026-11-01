package dev.monkeypatch.rctiming.screens;

/** A known screen with its live state and the name of its assigned playlist (null if none), for the Screens page (#708). */
public record ScreenOverview(Screen screen, ScreenState state, String playlistName) {
}
