package dev.monkeypatch.rctiming.simulator.relay;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal live feed relay for development and tests (#28). It is not the real relay, which RaceHub or a
 * separate service will run.
 *
 * <ul>
 *   <li>{@code /publish}: the app connects here over WebSocket, with {@code Authorization: Bearer <token>}.</li>
 *   <li>{@code /watch}: viewers connect here and get every message the app sends, starting with the latest
 *       one for each race.</li>
 *   <li>{@code /}: a viewer page that shows the running order as it changes.</li>
 * </ul>
 *
 * <p>It forwards each text message from a publisher to all viewers unchanged, and remembers only the latest message
 * per race, found by the first {@code "rctc_race_id"} number in the text; a message without one is forwarded but not
 * replayed to later viewers. Viewers cannot send. It does not parse or validate the Live Feed v1 payload, and it
 * keeps everything in memory.
 *
 * <p>Run it by hand with {@link LiveFeedRelayMain}, or start one on a free port ({@code port} 0) in a test and read
 * the counters to see what the app sent. Netty threads handle every connection; the counters are safe to read from
 * the test thread.
 */
public class LiveFeedTestRelay implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LiveFeedTestRelay.class);
    private static final Pattern RACE_ID = Pattern.compile("\"rctc_race_id\"\\s*:\\s*(\\d+)");
    private static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER = AttributeKey.valueOf("handshaker");
    private static final AttributeKey<Boolean> PUBLISHER = AttributeKey.valueOf("publisher");

    private final int requestedPort;
    private final String token;
    private final ChannelGroup viewers = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    /** The latest message for each race, for viewers who join part way through. */
    private final Map<Long, String> latest = new TreeMap<>();
    private final AtomicInteger publishers = new AtomicInteger();
    private final AtomicInteger received = new AtomicInteger();
    private final AtomicInteger closeFrames = new AtomicInteger();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /**
     * @param port  the port to listen on, or 0 for any free one
     * @param token the key the app must send, or null to accept any publisher
     */
    public LiveFeedTestRelay(int port, String token) {
        this.requestedPort = port;
        this.token = token;
    }

    /** Starts listening on 127.0.0.1 and returns the port. */
    public int start() throws InterruptedException {
        return start("127.0.0.1");
    }

    /** Starts listening on the given address and returns the port. */
    public int start(String bindAddress) throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);
        serverChannel = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                // A relay restarted straight after a stop can take the same port back
                .option(ChannelOption.SO_REUSEADDR, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec(), new HttpObjectAggregator(1 << 20), new Handler());
                    }
                })
                .bind(new InetSocketAddress(bindAddress, requestedPort))
                .sync()
                .channel();
        return port();
    }

    /** The port actually bound, which differs from the requested one when that was 0. */
    public int port() {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    /** How many publishers are connected now. */
    public int publisherCount() {
        return publishers.get();
    }

    /** How many messages publishers have sent since the relay started. */
    public int receivedCount() {
        return received.get();
    }

    /** How many WebSocket close frames connections have sent since the relay started (a clean goodbye). */
    public int closeFrameCount() {
        return closeFrames.get();
    }

    /** Stops listening, closes every connection and waits for the Netty threads to finish. */
    @Override
    public void close() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        viewers.close().syncUninterruptibly();
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    private final class Handler extends SimpleChannelInboundHandler<Object> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest request) {
                onRequest(ctx, request);
            } else if (msg instanceof WebSocketFrame frame) {
                onFrame(ctx, frame);
            }
        }

        private void onRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
            String path = new QueryStringDecoder(request.uri()).path();
            switch (path) {
                case "/", "/index.html" -> respond(ctx, request, HttpResponseStatus.OK, viewerPage(), "text/html; charset=utf-8");
                case "/publish" -> {
                    if (token != null && !("Bearer " + token).equals(request.headers().get(HttpHeaderNames.AUTHORIZATION))) {
                        log.warn("Refused a publisher with the wrong key");
                        respond(ctx, request, HttpResponseStatus.UNAUTHORIZED, "Wrong key", "text/plain");
                        return;
                    }
                    if (handshake(ctx, request)) {
                        ctx.channel().attr(PUBLISHER).set(true);
                        publishers.incrementAndGet();
                        ctx.channel().closeFuture().addListener(f -> publishers.decrementAndGet());
                        log.info("Publisher connected from {}", ctx.channel().remoteAddress());
                    }
                }
                case "/watch" -> {
                    if (handshake(ctx, request)) {
                        viewers.add(ctx.channel());
                        synchronized (latest) {
                            latest.values().forEach(m -> ctx.channel().writeAndFlush(new TextWebSocketFrame(m)));
                        }
                    }
                }
                default -> respond(ctx, request, HttpResponseStatus.NOT_FOUND, "Not found", "text/plain");
            }
        }

        private boolean handshake(ChannelHandlerContext ctx, FullHttpRequest request) {
            String location = "ws://" + request.headers().get(HttpHeaderNames.HOST) + request.uri();
            WebSocketServerHandshaker handshaker =
                    new WebSocketServerHandshakerFactory(location, null, true, 1 << 20).newHandshaker(request);
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                return false;
            }
            ctx.channel().attr(HANDSHAKER).set(handshaker);
            handshaker.handshake(ctx.channel(), request);
            return true;
        }

        private void onFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof CloseWebSocketFrame close) {
                closeFrames.incrementAndGet();
                ctx.channel().attr(HANDSHAKER).get().close(ctx.channel(), close.retain());
            } else if (frame instanceof PingWebSocketFrame ping) {
                ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
            } else if (frame instanceof TextWebSocketFrame text && Boolean.TRUE.equals(ctx.channel().attr(PUBLISHER).get())) {
                String message = text.text();
                received.incrementAndGet();
                Matcher raceId = RACE_ID.matcher(message);
                if (raceId.find()) {
                    synchronized (latest) {
                        latest.put(Long.parseLong(raceId.group(1)), message);
                    }
                }
                viewers.writeAndFlush(new TextWebSocketFrame(message));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("Relay connection error", cause);
            ctx.close();
        }
    }

    private static void respond(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status,
                                String body, String contentType) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        HttpUtil.setContentLength(response, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static String viewerPage() {
        try (InputStream in = LiveFeedTestRelay.class.getResourceAsStream("/relay/viewer.html")) {
            return in == null ? "Viewer page missing" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Viewer page unreadable";
        }
    }
}
