package dev.monkeypatch.rctiming.infrastructure.tls;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rctiming.https.*}: the optional HTTPS connector beside the plain HTTP one (#503).
 *
 * @param enabled whether to serve HTTPS as well as HTTP; off by default
 * @param port    the port HTTPS listens on, which must differ from {@code server.port}
 */
@ConfigurationProperties(prefix = "rctiming.https")
public record HttpsProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("8443") int port) {
}
