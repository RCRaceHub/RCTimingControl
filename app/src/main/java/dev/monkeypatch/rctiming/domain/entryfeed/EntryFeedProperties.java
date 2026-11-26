package dev.monkeypatch.rctiming.domain.entryfeed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How often automatic entry feeds are fetched ({@code rctiming.entry-feed.*}). A feed is fetched every
 * {@code intervalMs} once its event is within {@code nearDays} days (or past), and every {@code farIntervalMs}
 * before that, because entries rarely need minute-level freshness weeks ahead.
 *
 * @param intervalMs    milliseconds between fetches of an event that is close, five minutes by default
 * @param farIntervalMs milliseconds between fetches of an event that is further away, an hour by default
 * @param nearDays      how many days before its date an event counts as close, seven by default
 */
@ConfigurationProperties("rctiming.entry-feed")
public record EntryFeedProperties(@DefaultValue("300000") long intervalMs,
                                  @DefaultValue("3600000") long farIntervalMs,
                                  @DefaultValue("7") int nearDays) {
}
