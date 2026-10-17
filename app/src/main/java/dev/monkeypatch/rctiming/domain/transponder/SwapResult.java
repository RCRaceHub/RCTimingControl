package dev.monkeypatch.rctiming.domain.transponder;

import dev.monkeypatch.rctiming.domain.entry.Entry;

/**
 * Outcome of swapping one of an entry's transponders (L11). A swap that cannot go ahead is a value here, not an
 * exception, so the caller can say why; nothing is saved for any result but {@link Success}.
 */
public sealed interface SwapResult {

    /** The swap is saved. {@code newNumber} is null when a secondary transponder was removed. */
    record Success(Entry entry, TransponderSlot slot, String oldNumber, String newNumber) implements SwapResult {}

    /** No entry with that id in this event. */
    record EntryNotFound() implements SwapResult {}

    /** The entry was withdrawn. */
    record EntryWithdrawn() implements SwapResult {}

    /** Another competitor's entry in this event already uses the number. */
    record TransponderAlreadyAssigned() implements SwapResult {}

    /** The number is already this entry's other transponder. */
    record SameAsOtherSlot() implements SwapResult {}

    /** The primary transponder cannot be removed, only replaced. */
    record PrimaryRequired() implements SwapResult {}
}
