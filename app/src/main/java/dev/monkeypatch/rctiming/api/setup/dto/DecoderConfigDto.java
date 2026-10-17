package dev.monkeypatch.rctiming.api.setup.dto;

/**
 * Current decoder connection settings, answer of {@code GET /api/v1/setup/decoder-config}. All fields are null
 * until the decoder is configured. {@code decoderProtocol} is {@code RC4} or {@code P3}.
 */
public record DecoderConfigDto(String decoderHost, Integer decoderPort, String decoderProtocol) {}
