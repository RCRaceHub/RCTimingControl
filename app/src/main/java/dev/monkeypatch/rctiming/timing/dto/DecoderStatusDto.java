package dev.monkeypatch.rctiming.timing.dto;

/**
 * Broadcast on /topic/system/decoder-status when the decoder connection state or the state of its clock changes.
 * {@code decoderState} is "CONNECTED", "RECONNECTING" or "DISCONNECTED"; {@code clock} says how the decoder's clock
 * looks against the server's, or is null before any passing has been seen on the current connection settings.
 */
public record DecoderStatusDto(String decoderState, DecoderClockDto clock) {

    /** A connection state with no clock reading. */
    public DecoderStatusDto(String decoderState) {
        this(decoderState, null);
    }
}
