package dev.monkeypatch.rctiming.timing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rctiming.timing.*}: how live timing judges a car that has stopped.
 *
 * @param stalledAfterLaps how many of its own average laps a car may go without a counted lap before it is judged
 *                         stopped. A stopped car is not waited for when a race ends, so the heat closes, and it is a
 *                         DNF in the result once the race has reached its end. 0 turns the check off
 */
@ConfigurationProperties(prefix = "rctiming.timing")
public record TimingProperties(
        @DefaultValue("4") int stalledAfterLaps) {
}
