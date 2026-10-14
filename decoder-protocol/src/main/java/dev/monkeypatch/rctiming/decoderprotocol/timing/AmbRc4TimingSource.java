package dev.monkeypatch.rctiming.decoderprotocol.timing;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * RC-4 text protocol TCP client (TIMING-05).
 *
 * <p>The RC-4 protocol has no client handshake.
 * The AMB decoder begins streaming STATUS and PASSING frames immediately upon TCP connection.
 * This class simply opens the TCP connection and starts receiving — no handshake frame is sent.
 * Port 5100 (firmware &lt; 4.5) confirmed from club hardware captures.
 *
 * <p>Auto-reconnects on TCP disconnect or {@code READER_IDLE} (8 s with no STATUS/PASSING)
 * using exponential backoff: 1 s, 2 s, 4 s, 8 s, then 16 s (capped).
 * Reconnect attempts are prevented once {@link #stop()} has been called.
 *
 * <p>Owns single instances of {@link Rc4TextParser}, {@link EpochAnchor}, and
 * {@link SeqGapDetector} that persist across reconnects so the epoch is not lost on
 * transient TCP drops.
 */
public class AmbRc4TimingSource implements TimingSource {

    private static final Logger log = LoggerFactory.getLogger(AmbRc4TimingSource.class);

    /** Connection state reported to the onStatus callback. */
    public enum ConnectionState {
        CONNECTED,
        RECONNECTING,
        DISCONNECTED
    }

    private final String                          host;
    private final int                             port;
    private final Consumer<EpochCorrectedPassing> onPassing;
    private final Consumer<ConnectionState>       onStatus;

    // Single instances reused across reconnects to preserve epoch
    private final Rc4TextParser   parser      = new Rc4TextParser();
    private final EpochAnchor     epochAnchor = new EpochAnchor();
    private final SeqGapDetector  gapDetector = new SeqGapDetector();

    private final NioEventLoopGroup group = new NioEventLoopGroup(1);

    private volatile boolean stopped     = false;
    private          int     backoffIdx  = 0;

    /**
     * Create a new {@code AmbRc4TimingSource}.
     *
     * @param host      AMB decoder hostname or IP (e.g. {@code "localhost"} or {@code "192.168.1.10"})
     * @param port      TCP port — 5100 for RC-4 firmware &lt; 4.5
     * @param onPassing callback invoked for each decoded PASSING record
     * @param onStatus  callback invoked on connection state changes
     */
    public AmbRc4TimingSource(String host, int port,
                              Consumer<EpochCorrectedPassing> onPassing,
                              Consumer<ConnectionState> onStatus) {
        this.host      = host;
        this.port      = port;
        this.onPassing = onPassing;
        this.onStatus  = onStatus;
    }

    /** {@inheritDoc} Start the Netty TCP client. Non-blocking. */
    @Override
    public void start() {
        log.info("AmbRc4TimingSource starting — connecting to {}:{}", host, port);
        connect();
    }

    /** {@inheritDoc} Stop the Netty event loop and prevent further reconnects. Idempotent. */
    @Override
    public void stop() {
        stopped = true;
        group.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS);
        log.info("AmbRc4TimingSource stopped");
        onStatus.accept(ConnectionState.DISCONNECTED);
    }

    private void connect() {
        if (stopped) return;

        Bootstrap bootstrap = new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
            .handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(
                        new IdleStateHandler(8, 0, 0, TimeUnit.SECONDS),
                        new LineBasedFrameDecoder(1024),
                        new StringDecoder(StandardCharsets.US_ASCII),
                        new Rc4InboundHandler(parser, epochAnchor, gapDetector, onPassing)
                    );
                }
            });

        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("Connected to RC-4 decoder at {}:{}", host, port);
                backoffIdx = 0;
                onStatus.accept(ConnectionState.CONNECTED);
                // When the channel closes, trigger a reconnect
                future.channel().closeFuture().addListener(closeFuture -> {
                    if (!stopped) {
                        log.warn("RC-4 decoder channel closed — scheduling reconnect");
                        onStatus.accept(ConnectionState.RECONNECTING);
                        scheduleReconnect();
                    }
                });
            } else {
                log.warn("Failed to connect to {}:{} — {}", host, port,
                         future.cause().getMessage());
                onStatus.accept(ConnectionState.RECONNECTING);
                scheduleReconnect();
            }
        });
    }

    /**
     * Schedule the next reconnect attempt with exponential backoff capped at 16 s.
     * Backoff sequence: 1, 2, 4, 8, 16, 16, 16 ...
     */
    private void scheduleReconnect() {
        if (stopped) return;
        backoffIdx++;
        long delaySeconds = 1L << Math.min(backoffIdx - 1, 4);
        log.info("Reconnecting to {}:{} in {} s (attempt {})", host, port, delaySeconds, backoffIdx);
        group.schedule(this::connect, delaySeconds, TimeUnit.SECONDS);
    }
}
