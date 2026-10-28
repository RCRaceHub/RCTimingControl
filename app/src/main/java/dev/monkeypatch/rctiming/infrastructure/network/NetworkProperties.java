package dev.monkeypatch.rctiming.infrastructure.network;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * {@code rctiming.network.*}: how other devices on the venue network are told to reach the app (#99).
 *
 * @param addresses what to show on the About page and in the log instead of the addresses the app finds: hosts such
 *                  as {@code 192.168.1.50} or {@code club-timing}, or full URLs when a container publishes a
 *                  different port. Comma-separated in a property or environment variable; empty uses what it finds
 * @param mdnsName  the {@code .local} name to announce on the network, such as {@code timing}; empty announces nothing
 */
@ConfigurationProperties(prefix = "rctiming.network")
public record NetworkProperties(
        @DefaultValue List<String> addresses,
        @DefaultValue("") String mdnsName) {
}
