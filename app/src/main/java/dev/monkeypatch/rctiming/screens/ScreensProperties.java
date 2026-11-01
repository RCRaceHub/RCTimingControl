package dev.monkeypatch.rctiming.screens;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rctiming.screens.*}: the screen manager for EventScreenDisplay viewers (#708).
 *
 * @param discovery whether to browse the venue network for screens with mDNS ({@code _eventscreen._tcp}); turn off
 *                  where multicast is blocked, and add screens by hand instead
 */
@ConfigurationProperties(prefix = "rctiming.screens")
public record ScreensProperties(@DefaultValue("true") boolean discovery) {
}
