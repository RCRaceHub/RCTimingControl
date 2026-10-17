package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.timing.dto.DecoderStatusDto;
import org.springframework.stereotype.Component;

/**
 * Tracks the decoder TCP connection state and broadcasts it to /topic/system/decoder-status.
 * The race-control status bar subscribes to this topic. The last known state is cached so new
 * subscribers can read it over REST when they load the page.
 *
 * <p>Set by {@link DecoderListener}, the only source of decoder state.
 */
@Component
public class DecoderStatusPublisher {

    private final LiveTimingHub liveTimingHub;
    private volatile String decoderState = "DISCONNECTED";

    public DecoderStatusPublisher(LiveTimingHub liveTimingHub) {
        this.liveTimingHub = liveTimingHub;
    }

    /** The most recent state, for the status bar to read over REST before its first broadcast arrives. */
    public DecoderStatusDto getLastKnownStatus() {
        return new DecoderStatusDto(decoderState);
    }

    /**
     * Records the new state and broadcasts it. Called from {@link DecoderListener} on the Netty event-loop thread
     * (or the lifecycle thread when the decoder is unconfigured); {@code state} is the name of the TCP source's
     * connection state, such as {@code CONNECTED} or {@code DISCONNECTED}.
     */
    public void onDecoderStatus(String state) {
        decoderState = state;
        liveTimingHub.broadcastDecoderStatus(new DecoderStatusDto(state));
    }
}
