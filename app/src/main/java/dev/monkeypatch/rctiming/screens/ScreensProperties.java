package dev.monkeypatch.rctiming.screens;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * {@code rctiming.screens.*}: the screen manager for EventScreenDisplay viewers (#708).
 *
 * @param discovery     whether to browse the venue network for screens with mDNS ({@code _eventscreen._tcp}); turn off
 *                      where multicast is blocked, and add screens by hand instead
 * @param restrictPages whether a playlist or temporary page may only name this server's own venue-network addresses
 *                      and {@code allowedHosts}, so a mistyped or hostile address cannot be pushed to the TVs
 * @param allowedHosts  further host names or IP addresses pages may be on, such as a club website; comma-separated in
 *                      a property or environment variable
 */
@ConfigurationProperties(prefix = "rctiming.screens")
public record ScreensProperties(@DefaultValue("true") boolean discovery,
                                @DefaultValue("true") boolean restrictPages,
                                @DefaultValue List<String> allowedHosts) {
}
