package dev.monkeypatch.rctiming.domain.media;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Downloads one picture named by an entry import (#699). It exists apart from {@link PhotoImportService} so tests can
 * replace the network.
 *
 * <p>The file that names the address is not trusted, and RCTC sits on the venue network, so an address is fetched only
 * when it is {@code https} and its host resolves to public addresses alone: loopback, link-local, private (including
 * IPv6 unique-local) and multicast addresses are refused. Redirects are not followed, as one could lead somewhere
 * private. The body is read up to a limit and never returned to a caller that is not checking it as an image. A host
 * that changes its answer between the check and the connection is not caught; the fetch is ADMIN-only and its answer is
 * decoded as a PNG or JPEG or thrown away, so nothing the host sends is ever shown.
 */
@Component
public class PhotoFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEADLINE = Duration.ofSeconds(20);

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

    /** A picture that could not be fetched; the message is fixed text, never the host's own wording or the address. */
    public static class FetchException extends Exception {
        FetchException(String message) {
            super(message);
        }
    }

    /**
     * Downloads the address, giving up after {@link #DEADLINE} in total, however slowly the host sends.
     *
     * @param url      an {@code https} address
     * @param maxBytes the most to accept; a longer body fails rather than being cut
     * @return the body
     * @throws FetchException if the host is not public, the answer is not 200, the body is too long or too slow, or the
     *                        request fails
     */
    public byte[] fetch(String url, int maxBytes) throws FetchException {
        long deadline = System.nanoTime() + DEADLINE.toNanos();
        try {
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new FetchException("Only https addresses are fetched");
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (!isPublic(address)) {
                    throw new FetchException("The address is not on the public internet");
                }
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new FetchException("The server answered " + response.statusCode());
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = body.read(chunk)) != -1) {
                    if (out.size() + n > maxBytes) {
                        throw new FetchException("The picture is over " + maxBytes + " bytes");
                    }
                    if (System.nanoTime() > deadline) {
                        throw new FetchException("The server was too slow");
                    }
                    out.write(chunk, 0, n);
                }
                return out.toByteArray();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("The fetch was interrupted");
        } catch (IOException | IllegalArgumentException e) {
            throw new FetchException("The picture could not be fetched");
        }
    }

    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return false;
        }
        // fc00::/7 unique-local, 6to4 (2002::/16) and NAT64 (64:ff9b::/96) addresses are not reported as private
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            return (b[0] & 0xFE) != 0xFC && !(b[0] == 0x20 && b[1] == 0x02)
                    && !(b[0] == 0x00 && b[1] == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B);
        }
        int o1 = b[0] & 0xFF;
        int o2 = b[1] & 0xFF;
        // Carrier-grade NAT (100.64/10, used by Tailscale and Starlink), 192.0.0/24, benchmarking (198.18/15) and
        // 240/4 reserved space are not the public internet either
        return !(o1 == 100 && (o2 & 0xC0) == 0x40) && !(o1 == 192 && o2 == 0 && b[2] == 0)
                && !(o1 == 198 && (o2 & 0xFE) == 18) && o1 < 240;
    }
}
