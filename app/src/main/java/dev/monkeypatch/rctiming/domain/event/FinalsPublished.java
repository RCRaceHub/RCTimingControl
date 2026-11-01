package dev.monkeypatch.rctiming.domain.event;

/**
 * Published by {@code FinalsLineupService.confirm} the first time an event's finals line-ups are confirmed, which is
 * the moment the finals lists are ready for the people at the track. It is published inside the confirming
 * transaction, so listeners that talk to the network wait for the commit (see {@code ScreenMessageHooks}).
 */
public record FinalsPublished(long eventId) {
}
