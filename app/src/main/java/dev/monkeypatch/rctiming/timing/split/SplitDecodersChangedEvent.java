package dev.monkeypatch.rctiming.timing.split;

/**
 * Published by {@link SplitDecoderService} when a split decoder is added, changed or removed, so that
 * {@link SplitLoopListener} reconnects after the change commits.
 */
public record SplitDecodersChangedEvent() {
}
