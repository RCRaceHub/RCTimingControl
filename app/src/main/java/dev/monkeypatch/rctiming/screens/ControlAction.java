package dev.monkeypatch.rctiming.screens;

import java.util.Locale;

/** The playback controls a screen offers, named as in its {@code /control/{action}} path (#708). */
public enum ControlAction {
    NEXT, PREVIOUS, GOTO, PAUSE, RESUME, RELOAD;

    /** The action called this, in any case. @throws IllegalArgumentException (400) for an unknown one */
    public static ControlAction parse(String name) {
        try {
            return valueOf(name == null ? "" : name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown screen control: " + name);
        }
    }

    String path() {
        return name().toLowerCase(Locale.ROOT);
    }
}
