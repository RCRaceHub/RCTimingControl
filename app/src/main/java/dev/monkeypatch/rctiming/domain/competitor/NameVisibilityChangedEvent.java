package dev.monkeypatch.rctiming.domain.competitor;

/**
 * Published after a competitor's public name setting has been saved (#265), so live timing can refresh the names it
 * is holding for races in progress.
 */
public record NameVisibilityChangedEvent(Long competitorId) {
}
