package dev.monkeypatch.rctiming.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Re-serves the records the {@link DecoderRecorder} reads to any number of other programs (RCTimingControl, RCResults),
 * so the decoder itself sees one connection however many programs want its data.
 *
 * <p>It listens on a TCP port. Each client that connects gets every record from then on, as the decoder sent it
 * (RC-4 text has no handshake or history to replay). {@link #send} is called on the recorder's reading thread and never
 * blocks on a client: each client has its own writer thread and a bounded queue, and a client that falls too far
 * behind or whose socket fails is dropped, without affecting the recording or the other clients. Clients only receive;
 * anything they send is ignored.
 */
final class RecordFanOut implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(RecordFanOut.class);
    private static final int QUEUE_LIMIT = 1_000;

    private final ServerSocket server;
    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();

    /** Starts listening on {@code port} ({@code 0} picks a free one, see {@link #port()}). */
    RecordFanOut(int port) throws IOException {
        server = new ServerSocket(port);
        Thread acceptor = new Thread(this::acceptLoop, "record-fanout-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        log.info("[RECORDER] Re-serving records on port {}", server.getLocalPort());
    }

    int port() {
        return server.getLocalPort();
    }

    int clientCount() {
        return clients.size();
    }

    /** Hands one record (with its SOH and CRLF) to every connected client; never blocks. */
    void send(byte[] record) {
        for (Client client : clients) {
            if (!client.queue.offer(record)) {
                log.warn("[RECORDER] Dropping slow client {}", client.socket.getRemoteSocketAddress());
                client.close();
            }
        }
    }

    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException ignored) {
            // shutting down
        }
        clients.forEach(Client::close);
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                Client client = new Client(socket);
                clients.add(client);
                log.info("[RECORDER] Client connected: {}", socket.getRemoteSocketAddress());
                Thread writer = new Thread(client::writeLoop, "record-fanout-client");
                writer.setDaemon(true);
                writer.start();
            } catch (IOException e) {
                if (!server.isClosed()) {
                    log.warn("[RECORDER] Accept failed: {}", e.getMessage());
                }
            }
        }
    }

    private final class Client {
        final Socket socket;
        final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(QUEUE_LIMIT);
        private volatile boolean closed;

        Client(Socket socket) {
            this.socket = socket;
        }

        void writeLoop() {
            try {
                OutputStream out = socket.getOutputStream();
                while (!closed) {
                    byte[] record = queue.take();
                    out.write(record);
                    out.flush();
                }
            } catch (IOException e) {
                log.info("[RECORDER] Client {} gone: {}", socket.getRemoteSocketAddress(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                close();
            }
        }

        void close() {
            closed = true;
            clients.remove(this);
            queue.offer(new byte[0]);
            try {
                socket.close();
            } catch (IOException ignored) {
                // already gone
            }
        }
    }
}
