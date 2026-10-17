package dev.monkeypatch.rctiming.api.setup.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The decoder address to test or save, the body of {@code POST /api/v1/setup/decoder-config/test} and
 * {@code PATCH /api/v1/setup/decoder-config}.
 *
 * @param decoderPort     TCP port, 1 to 65535
 * @param decoderProtocol {@code RC4} (text) or {@code P3} (binary)
 */
public record DecoderConfigUpdateRequest(
        @NotBlank @Size(max = 255) String decoderHost,
        @NotNull @Min(1) @Max(65535) Integer decoderPort,
        @NotNull @Pattern(regexp = "RC4|P3", message = "Protocol must be RC4 or P3") String decoderProtocol
) {}
