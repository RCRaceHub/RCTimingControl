package dev.monkeypatch.rctiming.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;

/**
 * Records what a real RC-4 text decoder sends to a file the simulator's playback mode can replay (the {@code record}
 * command), so real race-day data can become test data.
 *
 * <p>Two files are written. The {@code .dump} file holds every record exactly as it arrived (SOH, tab-separated
 * fields, CRLF), which is the format {@link PlaybackMode} reads. A sidecar {@code .dump.times} file holds the same
 * records one per line, each after the UTC wall-clock time in epoch milliseconds that the recorder received it, a tab,
 * and the record without its SOH or CRLF. Both are flushed after every record, so a crash or a pulled plug loses at
 * most the record in flight (a power cut can lose more, as flushing hands bytes to the operating system only). Existing files are appended to, never overwritten.
 *
 * <p>The recorder only reads: it sends nothing to the decoder. With a listen port it also acts as a proxy, passing each
 * record on to any number of other programs. It runs on the thread that calls {@link #run}; if the
 * connection drops or cannot be made it waits and tries again until {@link #stop()} is called or the thread is interrupted.
 */
public class DecoderRecorder {

    private static final Logger log = LoggerFactory.getLogger(DecoderRecorder.class);

    private static final long RECONNECT_DELAY_MS = 3_000;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    /** The decoder sends a STATUS record every 5 s, so silence for this long means the connection is dead. */
    private static final int READ_TIMEOUT_MS = 30_000;
    /** A real record is well under 200 bytes; a longer "line" means this isn't an RC-4 decoder. */
    private static final int MAX_LINE_BYTES = 8_192;

    private final String host;
    private final int port;
    private final Path dumpFile;
    private final Path timesFile;
    private final Clock clock;
    private volatile boolean stopped;
    private volatile Socket current;
    private final RecordFanOut fanOut;

    /** A recorder for the decoder at {@code host:port}, writing {@code dumpFile} and {@code dumpFile + ".times"}. */
    public DecoderRecorder(String host, int port, Path dumpFile) {
        this(host, port, dumpFile, Clock.systemUTC(), null);
    }

    /**
     * As above, and also re-serving every record to other programs on {@code listenPort} (see {@link RecordFanOut}),
     * so they can use this recorder as their decoder and the decoder keeps a single connection.
     */
    public DecoderRecorder(String host, int port, Path dumpFile, int listenPort) throws IOException {
        this(host, port, dumpFile, Clock.systemUTC(), new RecordFanOut(listenPort));
    }

    DecoderRecorder(String host, int port, Path dumpFile, Clock clock) {
        this(host, port, dumpFile, clock, null);
    }

    DecoderRecorder(String host, int port, Path dumpFile, Clock clock, RecordFanOut fanOut) {
        this.fanOut = fanOut;
        this.host = host;
        this.port = port;
        this.dumpFile = dumpFile;
        this.timesFile = dumpFile.resolveSibling(dumpFile.getFileName() + ".times");
        this.clock = clock;
    }

    /**
     * Connects and records until {@link #stop()} is called or the thread is interrupted, reconnecting after a drop or
     * after 30 s of silence from the decoder.
     *
     * @throws IOException if an output file cannot be opened or written (a decoder that cannot be reached is retried,
     *                     not thrown)
     */
    public void run() throws IOException {
        try (OutputStream dump = open(dumpFile); OutputStream times = open(timesFile)) {
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                try (Socket socket = new Socket()) {
                    current = socket;
                    socket.setSoTimeout(READ_TIMEOUT_MS);
                    socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                    log.info("[RECORDER] Connected to {}:{}, writing {}", host, port, dumpFile);
                    long count = record(new BufferedInputStream(socket.getInputStream()), dump, times);
                    log.warn("[RECORDER] Decoder closed the connection after {} records", count);
                } catch (UncheckedIOException e) {
                    throw e.getCause();
                } catch (IOException e) {
                    if (!stopped) {
                        log.warn("[RECORDER] Connection to {}:{} failed: {}", host, port, e.getMessage());
                    }
                } finally {
                    current = null;
                }
                if (!stopped) {
                    Rc4Lines.pause(RECONNECT_DELAY_MS);
                }
            }
        }
        log.info("[RECORDER] Stopped");
    }

    /** Ends {@link #run()} promptly from another thread (the shutdown hook) by closing the blocked socket. */
    public void stop() {
        stopped = true;
        if (fanOut != null) {
            fanOut.close();
        }
        Socket socket = current;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing only to unblock the read
            }
        }
    }

    /**
     * Copies records from {@code in} to the two outputs until the stream ends. A partial record at the end, or a line
     * longer than {@value #MAX_LINE_BYTES} bytes, is dropped with a warning.
     *
     * @return the number of complete records written
     * @throws IOException on a read failure; a write failure is thrown as {@link UncheckedIOException} so the caller
     *                     can tell a bad disk from a bad connection
     */
    long record(InputStream in, OutputStream dump, OutputStream times) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        long count = 0;
        boolean tooLong = false;
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                if (tooLong) {
                    log.warn("[RECORDER] Dropped a line over {} bytes; is this an RC-4 decoder?", MAX_LINE_BYTES);
                } else {
                    write(line.toByteArray(), b, dump, times);
                    count++;
                    if (count % 100 == 0) {
                        log.info("[RECORDER] {} records this connection", count);
                    }
                }
                line.reset();
                tooLong = false;
            } else if (!tooLong) {
                line.write(b);
                tooLong = line.size() > MAX_LINE_BYTES;
                if (tooLong) {
                    line.reset();
                }
            }
        }
        if (line.size() > 0) {
            log.warn("[RECORDER] Dropped {} bytes of an unfinished record", line.size());
        }
        return count;
    }

    private void write(byte[] body, int newline, OutputStream dump, OutputStream times) {
        try {
            dump.write(body);
            dump.write(newline);
            dump.flush();
            if (fanOut != null) {
                byte[] record = new byte[body.length + 1];
                System.arraycopy(body, 0, record, 0, body.length);
                record[body.length] = (byte) newline;
                fanOut.send(record);
            }
            String text = new String(body, StandardCharsets.US_ASCII);
            if (text.startsWith("\u0001")) {
                text = text.substring(1);
            }
            if (text.endsWith("\r")) {
                text = text.substring(0, text.length() - 1);
            }
            times.write((clock.millis() + "\t" + text + "\n").getBytes(StandardCharsets.US_ASCII));
            times.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static OutputStream open(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
