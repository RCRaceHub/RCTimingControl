package dev.monkeypatch.rctiming.domain.media;

import org.springframework.stereotype.Component;

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

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

    /**
     * Downloads the address.
     *
     * @param url     an {@code https} address
     * @param maxBytes the most to accept; a longer body fails rather than being cut
     * @return the body
     * @throws IOException if the host is not public, the answer is not 200, the body is too long or the request fails
     */
    public byte[] fetch(String url, int maxBytes) throws IOException {
        URI uri = URI.create(url.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IOException("Only https addresses are fetched");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (!isPublic(address)) {
                throw new IOException("The address is not on the public internet");
            }
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("The server answered " + response.statusCode());
                }
                byte[] bytes = body.readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes) {
                    throw new IOException("The picture is over " + maxBytes + " bytes");
                }
                return bytes;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching", e);
        }
    }

    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return false;
        }
        // fc00::/7 unique-local addresses are not reported as site-local by the JDK
        return !(a instanceof Inet6Address && (a.getAddress()[0] & 0xFE) == 0xFC);
    }
}
