package dev.monkeypatch.rctiming.resultsexport;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rctiming.racehub.live.*}: the live results sent to RaceHub while a race runs.
 *
 * @param enabled       whether live results are sent at all; they also need the RaceHub address, the key and a
 *                      publishing choice that includes RaceHub, and only events imported from RaceHub are sent
 * @param minIntervalMs the shortest time between two snapshots of one race. A lap changes the order at once but the
 *                      snapshot waits for this, so a busy heat sends one request every few seconds, not one per passing
 * @param resendEveryMs how often a race whose order has not changed is sent again, so RaceHub can tell the feed is
 *                      alive and the race clock stays current
 */
@ConfigurationProperties(prefix = "rctiming.racehub.live")
public record LiveResultsProperties(@DefaultValue("true") boolean enabled,
                                    @DefaultValue("3000") long minIntervalMs,
                                    @DefaultValue("15000") long resendEveryMs) {

    public LiveResultsProperties {
        minIntervalMs = Math.max(1_000, minIntervalMs);
        resendEveryMs = Math.max(minIntervalMs, resendEveryMs);
    }
}
