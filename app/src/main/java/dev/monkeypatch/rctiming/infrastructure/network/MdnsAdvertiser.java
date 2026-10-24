package dev.monkeypatch.rctiming.infrastructure.network;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Announces the app on the venue network by an mDNS name such as {@code timing.local}, so phones, tablets and
 * boards can open {@code http://timing.local:8080/} without knowing the laptop's address. Off unless
 * {@code rctiming.network.mdns-name} is set, for example in {@code application.properties}. Windows 10 and later,
 * macOS, iOS and Android resolve {@code .local} names themselves.
 *
 * <p>The announcement goes out on each real IPv4 interface (the ones {@link LanAddresses} lists), from a
 * background thread started once the app is ready, because setting up multicast can take a few seconds and must
 * never delay or fail start-up: a failure is logged and the app carries on at its addresses. It cannot cross a
 * Docker bridge network (multicast does not), so a container needs host networking for it to work.
 *
 * <p>The name is not secure context for the browser: a page at {@code http://timing.local} is plain HTTP, so
 * browser features that need HTTPS or localhost (the audio output page's sound card list) do not work there.
 */
@Component
public class MdnsAdvertiser {

    private static final Logger log = LoggerFactory.getLogger(MdnsAdvertiser.class);

    private final Optional<String> label;
    private final LanAddresses lan;
    private final List<JmDNS> announcers = new CopyOnWriteArrayList<>();

    public MdnsAdvertiser(@Value("${rctiming.network.mdns-name:}") String name, LanAddresses lan) {
        this.label = hostLabel(name);
        this.lan = lan;
        if (!name.isBlank() && label.isEmpty()) {
            log.warn("rctiming.network.mdns-name '{}' is not a usable name (letters, digits and hyphens); not announcing", name);
        }
    }

    /**
     * Turns a configured name into a host label: {@code Timing.local} becomes {@code timing}. Empty when the setting is
     * blank or has nothing a host name can hold.
     */
    static Optional<String> hostLabel(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".local")) {
            lower = lower.substring(0, lower.length() - ".local".length());
        }
        return lower.matches("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?") ? Optional.of(lower) : Optional.empty();
    }

    /** Starts the announcements on a daemon thread once the web server is up. */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (label.isEmpty()) {
            return;
        }
        Thread thread = new Thread(this::announce, "mdns-announce");
        thread.setDaemon(true);
        thread.start();
    }

    private void announce() {
        int port = lan.port();
        List<String> announced = new ArrayList<>();
        for (String address : LanAddresses.lanIpv4Addresses()) {
            try {
                JmDNS jmdns = JmDNS.create(InetAddress.getByName(address), label.get());
                jmdns.registerService(ServiceInfo.create("_http._tcp.local.", "RC Timing", port, "RC Timing"));
                announcers.add(jmdns);
                announced.add(address);
            } catch (IOException | RuntimeException e) {
                log.warn("Could not announce {}.local on {}: {}", label.get(), address, e.getMessage());
            }
        }
        if (announced.isEmpty()) {
            log.warn("RC Timing is not announced as {}.local: no network interface took the announcement", label.get());
        } else {
            log.info("RC Timing is announced as {}.local on {}", label.get(), String.join(", ", announced));
        }
    }

    /** Withdraws the announcements at shutdown so the name stops answering at once. */
    @PreDestroy
    public void stop() {
        for (JmDNS jmdns : announcers) {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
            } catch (IOException | RuntimeException e) {
                log.debug("Closing mDNS announcer: {}", e.getMessage());
            }
        }
        announcers.clear();
    }
}
