package dev.monkeypatch.rctiming.domain.competitor;

/** How a competitor's name is shown on public screens and in the live feed (#265). */
public enum NameVisibility {
    /** The full display name. */
    SHOW,
    /** Initials only, such as {@code A. L.}. */
    INITIALS,
    /** No name: the car number stands in. */
    HIDE
}
