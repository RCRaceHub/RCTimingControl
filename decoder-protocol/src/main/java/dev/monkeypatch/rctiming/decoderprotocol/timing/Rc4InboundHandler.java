package dev.monkeypatch.rctiming.decoderprotocol.timing;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Netty channel handler for the RC-4 text protocol stream.
 *
 * <p>Strips the SOH (0x01) prefix byte, delegates line parsing to {@link Rc4TextParser},
 * converts relative timestamps via {@link EpochAnchor}, monitors sequence gaps via
 * {@link SeqGapDetector}, and passes {@link EpochCorrectedPassing} objects to the callback.
 *
 * <p>On {@code READER_IDLE} (no STATUS or PASSING for 8 s), closes the channel so that
 * the {@link AmbRc4TimingSource} reconnect logic can fire (TIMING-02).
 */
public class Rc4InboundHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(Rc4InboundHandler.class);

    private final Rc4TextParser                   parser;
    private final EpochAnchor                     epochAnchor;
    private final SeqGapDetector                  gapDetector;
    private final Consumer<EpochCorrectedPassing> onPassing;

    public Rc4InboundHandler(Rc4TextParser parser,
                             EpochAnchor epochAnchor,
                             SeqGapDetector gapDetector,
                             Consumer<EpochCorrectedPassing> onPassing) {
        this.parser      = parser;
        this.epochAnchor = epochAnchor;
        this.gapDetector = gapDetector;
        this.onPassing   = onPassing;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String line) {
        // Strip the SOH (0x01) byte if present; the parser rejects any other malformed input
        String stripped = (!line.isEmpty() && line.charAt(0) == 0x01) ? line.substring(1) : line;
        // Gap detection observes both STATUS and PASSING seq_num (shared counter) — must run
        // regardless of record type, or every STATUS heartbeat reads as a false gap.
        parser.extractSeqNum(stripped).ifPresent(gapDetector::observe);
        parser.parse(stripped).ifPresent(pp -> {
            if (epochAnchor.detectsRestart(pp.timeSinceStartSeconds())) {
                log.warn("Decoder restart detected (timeSinceStart regressed) — resetting epoch");
            }
            long rtcMicros = epochAnchor.toRtcTimeMicros(pp.timeSinceStartSeconds());
            onPassing.accept(new EpochCorrectedPassing(
                pp.transponderNumber(), rtcMicros, pp.seqNum(),
                pp.decoderId(), pp.signalStrength(), pp.hitCount()));
        });
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idle) {
            switch (idle.state()) {
                case READER_IDLE -> {
                    log.warn("READER_IDLE (no STATUS/PASSING for 8 s) — closing channel to trigger reconnect");
                    ctx.close();
                }
                default -> super.userEventTriggered(ctx, evt);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("RC-4 channel exception — closing: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
