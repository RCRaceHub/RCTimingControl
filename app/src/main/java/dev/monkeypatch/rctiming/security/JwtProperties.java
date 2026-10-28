package dev.monkeypatch.rctiming.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.jwt.*}: the sign-in tokens officials get.
 *
 * @param secret            base64 signing key; empty makes the app create one in its data folder on first start
 * @param accessTokenTtlMs  how long an access token is valid, in milliseconds
 * @param refreshTokenTtlMs how long a refresh token is valid, in milliseconds
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @DefaultValue("") String secret,
        @DefaultValue("900000") long accessTokenTtlMs,
        @DefaultValue("604800000") long refreshTokenTtlMs) {
}
