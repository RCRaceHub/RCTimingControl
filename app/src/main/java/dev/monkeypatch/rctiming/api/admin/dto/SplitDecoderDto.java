package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.timing.split.SplitDecoder;

/**
 * A split decoder as the admin page sees it (#672). {@code state} is the connection state:
 * {@code CONNECTED}, {@code RECONNECTING} or {@code DISCONNECTED}.
 */
public record SplitDecoderDto(long id, String name, String host, int port, int splitNumber, long clockOffsetMs,
                              boolean enabled, String state) {

    public static SplitDecoderDto from(SplitDecoder decoder, String state) {
        return new SplitDecoderDto(decoder.id(), decoder.name(), decoder.host(), decoder.port(),
                decoder.splitNumber(), decoder.clockOffsetMs(), decoder.enabled(), state);
    }
}
