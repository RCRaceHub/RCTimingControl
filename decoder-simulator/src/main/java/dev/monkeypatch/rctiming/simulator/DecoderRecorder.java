package dev.monkeypatch.rctiming.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
 * most the record in flight. Existing files are appended to, never overwritten.
 *
 * <p>The recorder only reads: it sends nothing to the decoder. It runs on the thread that calls {@link #run}; if the
 * connection drops or cannot be made it waits and tries again until the thread is interrupted.
 */
public class DecoderRecorder {

    private static final Logger log = LoggerFactory.getLogger(DecoderRecorder.class);

    private static final long RECONNECT_DELAY_MS = 3_000;
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final String host;
    private final int port;
    private final Path dumpFile;
    private final Path timesFile;
    private final Clock clock;

    /** A recorder for the decoder at {@code host:port}, writing {@code dumpFile} and {@code dumpFile + ".times"}. */
    public DecoderRecorder(String host, int port, Path dumpFile) {
        this(host, port, dumpFile, Clock.systemUTC());
    }

    DecoderRecorder(String host, int port, Path dumpFile, Clock clock) {
        this.host = host;
        this.port = port;
        this.dumpFile = dumpFile;
        this.timesFile = dumpFile.resolveSibling(dumpFile.getFileName() + ".times");
        this.clock = clock;
    }

    /**
     * Connects and records until the thread is interrupted, reconnecting after a drop.
     *
     * @throws IOException if the output files cannot be written (a decoder that cannot be reached is retried, not thrown)
     */
    public void run() throws IOException {
        try (OutputStream dump = open(dumpFile); OutputStream times = open(timesFile)) {
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                    log.info("[RECORDER] Connected to {}:{}, writing {}", host, port, dumpFile);
                    long count = record(socket.getInputStream(), dump, times);
                    log.warn("[RECORDER] Decoder closed the connection after {} records", count);
                } catch (IOException e) {
                    if (e instanceof java.nio.file.FileSystemException) {
                        throw e;
                    }
                    log.warn("[RECORDER] Connection to {}:{} failed: {}", host, port, e.getMessage());
                }
                Rc4Lines.pause(RECONNECT_DELAY_MS);
            }
        }
        log.info("[RECORDER] Stopped");
    }

    /**
     * Copies records from {@code in} to the two outputs until the stream ends or the thread is interrupted.
     *
     * @return the number of complete records written
     */
    long record(InputStream in, OutputStream dump, OutputStream times) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        long count = 0;
        int b;
        while ((b = in.read()) != -1) {
            line.write(b);
            if (b == '\n') {
                write(line.toByteArray(), dump, times);
                line.reset();
                count++;
                if (count % 100 == 0) {
                    log.info("[RECORDER] {} records this connection", count);
                }
            }
        }
        return count;
    }

    private void write(byte[] raw, OutputStream dump, OutputStream times) throws IOException {
        dump.write(raw);
        dump.flush();
        String text = new String(raw, StandardCharsets.US_ASCII).replaceAll("^\u0001|\r?\n$", "");
        times.write((clock.millis() + "\t" + text + "\n").getBytes(StandardCharsets.US_ASCII));
        times.flush();
    }

    private static OutputStream open(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return new BufferedOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
    }
}
