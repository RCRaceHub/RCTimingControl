package dev.monkeypatch.rctiming.domain.format;

/**
 * How a race starts (FORMAT-08). {@link #STAGGER}: car numbers are called one after another at the format's stagger
 * interval. {@link #GRID}: all cars start together on the buzzer. {@link #ROLLING}: time starts on a car's first
 * crossing after the buzzer.
 */
public enum StartType {
    STAGGER, GRID, ROLLING
}
