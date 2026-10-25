package dev.monkeypatch.rctiming.query.racecontrol;

import java.time.Instant;

/**
 * Whether race control must review the event's finals line-ups before the finals can be run: the answer behind the
 * dedicated line-up screen that replaces the cockpit once qualifying has ended.
 *
 * @param required    qualifying is over for every class with finals, no final has been called, and the line-ups
 *                    have not been confirmed, so the cockpit shows the line-up screen
 * @param confirmedAt when the line-ups were confirmed, null while unconfirmed (and after the finals were calculated
 *                    again)
 */
public record FinalsReviewDto(boolean required, Instant confirmedAt) {
}
