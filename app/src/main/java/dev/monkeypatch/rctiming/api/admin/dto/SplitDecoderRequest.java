package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body for adding or changing a split decoder (#672). */
public record SplitDecoderRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 255) String host,
        @Min(1) @Max(65535) int port,
        @Min(1) @Max(9) int splitNumber,
        @Min(-5000) @Max(5000) long clockOffsetMs,
        boolean enabled) {
}
