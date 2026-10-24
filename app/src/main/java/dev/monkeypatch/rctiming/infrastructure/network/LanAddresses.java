package dev.monkeypatch.rctiming.infrastructure.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The addresses phones, tablets and boards on the venue network should open (#23): one
 * {@code http://<ip>:<port>/} per network the laptop is on, plus one by its hostname, and one by its mDNS name such
 * as {@code timing.local} when {@code rctiming.network.mdns-name} is set (listed first, unless
 * {@code rctiming.network.addresses} names the addresses, which are then listed as given). Logged at
 * start-up and shown on the About page.
 *
 * <p>Inside a container the app only sees the container's own network, so
 * {@code rctiming.network.addresses} can name the addresses instead (#99): hosts such as
 * {@code 192.168.1.50} or {@code club-timing}, or full URLs when the published port differs.
 */
@Component
public class LanAddresses {

    private static final Logger log = LoggerFactory.getLogger(LanAddresses.class);

    /** Interfaces that never reach another device: container bridges, VM host-only networks. */
    private static final List<String> VIRTUAL_PREFIXES =
            List.of("docker", "veth", "br-", "virbr", "vmnet", "vboxnet", "cni", "flannel");

    private final String bindAddress;
    private final List<String> configuredAddresses;
    private final Optional<String> mdnsHost;
    private volatile int port;

    public LanAddresses(@Value("${server.address:}") String bindAddress,
                        @Value("${rctiming.network.addresses:}") String configuredAddresses,
                        @Value("${rctiming.network.mdns-name:}") String mdnsName) {
        this.mdnsHost = MdnsAdvertiser.hostLabel(mdnsName).map(label -> label + ".local");
        this.bindAddress = bindAddress == null ? "" : bindAddress.trim();
        this.configuredAddresses = configuredAddresses == null ? List.of()
                : Arrays.stream(configuredAddresses.split(","))
                        .map(String::trim).filter(a -> !a.isEmpty()).toList();
    }

    /** Learns the port the web server really bound to, which differs from the setting when it is 0 (random). */
    @EventListener
    public void onWebServerStarted(WebServerInitializedEvent event) {
        // A separate actuator port would start a second server; the app's own is the one to share
        if (!"management".equals(event.getApplicationContext().getServerNamespace())) {
            port = event.getWebServer().getPort();
        }
    }

    /** Writes the addresses to the log once the app is ready, so an installer or tester can find them. */
    @EventListener(ApplicationReadyEvent.class)
    public void logAddresses() {
        List<String> urls = urls();
        if (urls.isEmpty()) {
            log.info("RC Timing is running, but no network address was found for other devices to use");
        } else {
            log.info("RC Timing is running. Open it from other devices at: {}", String.join("  ", urls));
        }
    }

    /**
     * The URLs to open from other devices, IP addresses first; empty before the server starts. Configured addresses
     * win over anything found. A specific {@code server.address} is used as given; a wildcard bind lists the IPv4
     * address of each real network interface and then the machine's hostname. Looks at the network each time it is
     * called, so it follows a laptop moving between networks.
     */
    public List<String> urls() {
        if (port <= 0) {
            return List.of();
        }
        if (!configuredAddresses.isEmpty()) {
            return configuredAddresses.stream()
                    .map(a -> a.startsWith("http://") || a.startsWith("https://") ? a : url(a, port))
                    .toList();
        }
        Set<String> hosts = new LinkedHashSet<>();
        mdnsHost.ifPresent(hosts::add);
        if (!bindAddress.isEmpty() && !isWildcard(bindAddress)) {
            hosts.add(bindAddress);
        } else {
            hosts.addAll(interfaceAddresses());
            if (!hosts.isEmpty()) {
                hostname().ifPresent(hosts::add);
            }
        }
        List<String> urls = new ArrayList<>();
        for (String host : hosts) {
            urls.add(url(host, port));
        }
        return urls;
    }

    /** {@code http://host:port/}, with an IPv6 address in brackets and no port for 80. */
    static String url(String host, int port) {
        String authority = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        return "http://" + authority + (port == 80 ? "" : ":" + port) + "/";
    }

    private static boolean isWildcard(String address) {
        return address.equals("0.0.0.0") || address.equals("::") || address.equals("[::]");
    }

    /** The port the web server bound to, 0 before it has started. */
    int port() {
        return port;
    }

    /**
     * The addresses the mDNS name should resolve to: the {@code server.address} when one is set (nothing listens on
     * the others), otherwise the IPv4 address of each real network interface.
     */
    List<String> announceAddresses() {
        return !bindAddress.isEmpty() && !isWildcard(bindAddress) ? List.of(bindAddress) : interfaceAddresses();
    }

    private static List<String> interfaceAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || isVirtualName(nic.getName())) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    // IPv4 only: it is what people can read off a screen and type into a phone
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("Could not list network interfaces: {}", e.getMessage());
        }
        Collections.sort(addresses);
        return addresses;
    }

    private static boolean isVirtualName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return VIRTUAL_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    private static Optional<String> hostname() {
        try {
            String name = InetAddress.getLocalHost().getHostName();
            if (name == null || name.isBlank() || name.equals("localhost") || name.matches("[0-9.]+")) {
                return Optional.empty();
            }
            return Optional.of(name);
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }
}
