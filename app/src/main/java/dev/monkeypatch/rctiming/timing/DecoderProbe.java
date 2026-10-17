package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.decoderprotocol.timing.Rc4TextParser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Tests a decoder address the operator has typed in, without touching the live listener.
 *
 * <p>Opens a TCP connection and waits for one valid RC-4 record. The decoder sends a STATUS
 * record every few seconds, so a valid record within the timeout means the address is a working
 * decoder on the RC-4 protocol. The deadline covers the whole wait, including partial lines, and
 * each line is capped at {@link #MAX_RECORD_CHARS}.
 */
@Component
public class DecoderProbe {

    /** Long enough to catch the decoder's 5-second STATUS heartbeat. */
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(8);
    /** How long to wait for the TCP connection itself before giving up. */
    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** Longest RC-4 record we accept. Real records are a few dozen characters. */
    static final int MAX_RECORD_CHARS = 1024;

    /** STATUS: type, decoder_id, seq_num, noise_level, unknown, crc (see docs/AMB_DECODER_PROTOCOL.md). */
    private static final Pattern STATUS_RECORD =
            Pattern.compile("#\t\\d+\t\\d+\t\\d+\t\\d+\tx[0-9A-Fa-f]+");

    private final Duration timeout;
    private final Duration connectTimeout;
    private final Rc4TextParser parser = new Rc4TextParser();

    public DecoderProbe() {
        this(DEFAULT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
    }

    DecoderProbe(Duration timeout, Duration connectTimeout) {
        this.timeout = timeout;
        this.connectTimeout = connectTimeout;
    }

    /**
     * Probes a decoder address on the calling (request) thread, blocking for up to the connect timeout plus the
     * read timeout. Never throws for an unreachable or misbehaving host: every failure comes back as a
     * {@link Result} with an operator-readable message.
     */
    public Result probe(String host, int port, String protocol) {
        if (!"RC4".equals(protocol)) {
            return new Result(false, "Only the RC4 protocol is supported at the moment.");
        }
        InetSocketAddress address = new InetSocketAddress(host, port);
        if (address.isUnresolved()) {
            return new Result(false, "Could not find a host called " + host + ".");
        }

        try (Socket socket = new Socket()) {
            try {
                socket.connect(address, (int) connectTimeout.toMillis());
            } catch (IOException e) {
                return connectFailed(host, port);
            }

            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                String record = readLine(socket, deadline);
                if (record == null) {
                    return new Result(false, "The decoder closed the connection without sending a valid record.");
                }
                if (isRc4Record(record)) {
                    return new Result(true, "Decoder found at " + host + ":" + port + ".");
                }
            }
        } catch (SocketTimeoutException e) {
            return new Result(false, "Connected, but no valid decoder records arrived. Check the protocol and port.");
        } catch (IOException e) {
            return new Result(false, "The connection to " + host + ":" + port + " failed while reading.");
        }
    }

    private static Result connectFailed(String host, int port) {
        return new Result(false, "Could not connect to " + host + ":" + port
                + ". Check the address and that the decoder is on.");
    }

    /**
     * Reads one line (CRLF or LF terminated), returning it without the terminator. Returns null
     * when the peer closes the connection. Throws {@link SocketTimeoutException} once the deadline
     * passes, even if the peer is still sending bytes. Overlong lines are returned as empty
     * strings, which never match a record.
     */
    private static String readLine(Socket socket, long deadline) throws IOException {
        InputStream in = socket.getInputStream();
        StringBuilder line = new StringBuilder();
        boolean overlong = false;
        while (true) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new SocketTimeoutException("probe deadline reached");
            }
            socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            int b = in.read();
            if (b == -1) {
                return null;
            }
            if (b == '\n') {
                if (overlong) {
                    return "";
                }
                int end = line.length();
                if (end > 0 && line.charAt(end - 1) == '\r') {
                    line.setLength(end - 1);
                }
                return line.toString();
            }
            if (line.length() < MAX_RECORD_CHARS) {
                line.append((char) b);
            } else {
                overlong = true;
            }
        }
    }

    /** A record must start with SOH, then either a valid PASSING or a valid STATUS. */
    boolean isRc4Record(String line) {
        if (line.isEmpty() || line.charAt(0) != 0x01) {
            return false;
        }
        String record = line.substring(1);
        if (record.startsWith("@")) {
            return parser.parse(record).isPresent();
        }
        return STATUS_RECORD.matcher(record).matches();
    }

    /** Outcome of a probe. {@code message} is shown to the operator. */
    public record Result(boolean ok, String message) {}
}
