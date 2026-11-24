package dev.monkeypatch.rctiming.api.admin.dto;

import java.time.Instant;
import java.util.List;

/**
 * The state of HTTPS: what is running, and what the settings file says for the next start. {@code switchedOn} and
 * {@code configuredPort} are the file's values; {@code restartNeeded} is true when they differ from what is running.
 */
public record TlsStateDto(boolean enabled, int port, List<String> urls, List<String> names,
                          Instant caExpires, Instant serverExpires, String caFingerprint,
                          boolean switchedOn, int configuredPort, boolean restartNeeded) {}
