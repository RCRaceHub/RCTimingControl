package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.decoderprotocol.timing.ClockHealth;
import dev.monkeypatch.rctiming.timing.dto.DecoderClockDto;
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
    private volatile DecoderClockDto clock;

    public DecoderStatusPublisher(LiveTimingHub liveTimingHub) {
        this.liveTimingHub = liveTimingHub;
    }

    /** The most recent state, for the status bar to read over REST before its first broadcast arrives. */
    public DecoderStatusDto getLastKnownStatus() {
        return new DecoderStatusDto(decoderState, clock);
    }

    /**
     * Records the new state and broadcasts it. Called from {@link DecoderListener} on the Netty event-loop thread
     * (or the lifecycle thread when the decoder is unconfigured); {@code state} is the name of the TCP source's
     * connection state, such as {@code CONNECTED} or {@code DISCONNECTED}.
     */
    public void onDecoderStatus(String state) {
        decoderState = state;
        // A reading of the clock is only worth showing while the decoder is connected; the next one comes with a passing
        if (!"CONNECTED".equals(state)) {
            clock = null;
        }
        liveTimingHub.broadcastDecoderStatus(new DecoderStatusDto(state, clock));
    }

    /**
     * Records and broadcasts a change in how the decoder's clock looks against the server's. Called from
     * {@link DecoderListener} on the Netty event-loop thread, only when the status changes.
     *
     * @param health the new reading, or null to forget it (the decoder settings changed and a new source started)
     */
    public void onClockHealth(ClockHealth health) {
        clock = health == null ? null : DecoderClockDto.from(health);
        liveTimingHub.broadcastDecoderStatus(new DecoderStatusDto(decoderState, clock));
    }
}
