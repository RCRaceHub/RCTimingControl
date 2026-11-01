package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/v1/admin/screens} (#708): add a screen by hand from its host name or IP address and
 * port (8080 when left out), for venues where mDNS is blocked. The token is for a screen that asks for one.
 */
public record AddScreenRequest(@NotBlank @Size(max = 255) String host, Integer port, @Size(max = 255) String token) {
}
