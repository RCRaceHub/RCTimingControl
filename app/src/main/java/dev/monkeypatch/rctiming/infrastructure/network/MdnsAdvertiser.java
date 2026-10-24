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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Announces the app on the venue network by an mDNS name such as {@code timing.local}, so phones, tablets and
 * boards can open {@code http://timing.local:8080/} without knowing the laptop's address. Off unless
 * {@code rctiming.network.mdns-name} is set, for example in {@code application.properties}. Windows 10 and later,
 * macOS, iOS and Android resolve {@code .local} names themselves.
 *
 * <p>The announcement goes out on each real IPv4 interface (the ones {@link LanAddresses} lists, or just the
 * {@code server.address} when one is set), from a single background thread that starts once the app is ready and
 * looks at the interfaces again every {@value #RESCAN_SECONDS} seconds. Setting up multicast can take a few
 * seconds and must never delay or fail start-up, and a laptop that boots before its network is up, or moves to
 * another network, picks the name up then: a failure is logged and the app carries on at its addresses. It cannot
 * cross a Docker bridge network (multicast does not), so a container needs host networking for it to work.
 *
 * <p>A page at {@code http://timing.local} is plain HTTP, so it is not a secure context: browser features that need
 * HTTPS or localhost (the audio output page's sound card list) do not work there.
 */
@Component
public class MdnsAdvertiser {

    private static final Logger log = LoggerFactory.getLogger(MdnsAdvertiser.class);

    /** How often the network interfaces are looked at again. */
    static final int RESCAN_SECONDS = 30;

    private final Optional<String> label;
    private final LanAddresses lan;
    /** One announcer per interface address; touched only under {@code this}. */
    private final Map<String, JmDNS> announcers = new HashMap<>();
    private ScheduledExecutorService scheduler;
    private boolean stopped;

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
    public synchronized void start() {
        if (label.isEmpty() || stopped || scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "mdns-announce");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::rescan, 0, RESCAN_SECONDS, TimeUnit.SECONDS);
    }

    /** Announces on interface addresses that have appeared and withdraws from ones that have gone. */
    private void rescan() {
        try {
            int port = lan.port();
            if (port <= 0) {
                return;
            }
            List<String> wanted = lan.announceAddresses();
            for (String address : wanted) {
                if (!hasAnnouncer(address)) {
                    announceOn(address, port);
                }
            }
            withdrawExcept(wanted);
        } catch (RuntimeException e) {
            log.warn("mDNS rescan failed: {}", e.getMessage());
        }
    }

    private synchronized boolean hasAnnouncer(String address) {
        return announcers.containsKey(address);
    }

    private void announceOn(String address, int port) {
        JmDNS jmdns = null;
        try {
            // Creating one probes the network for the name, which takes a few seconds, so it is not under the lock
            jmdns = JmDNS.create(InetAddress.getByName(address), label.get());
            jmdns.registerService(ServiceInfo.create("_http._tcp.local.", "RC Timing", port, "RC Timing"));
            synchronized (this) {
                if (!stopped) {
                    announcers.put(address, jmdns);
                    jmdns = null;
                }
            }
            if (jmdns == null) {
                reportName(address);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not announce {}.local on {}: {}", label.get(), address, e.getMessage());
        } finally {
            // Not kept: the app was stopped while this was starting, or it failed part way
            closeQuietly(jmdns);
        }
    }

    /** Says what the name ended up as, since another device already using it makes JmDNS pick another. */
    private void reportName(String address) {
        JmDNS jmdns;
        synchronized (this) {
            jmdns = announcers.get(address);
        }
        String actual = jmdns == null ? null : jmdns.getHostName();
        String wanted = label.get() + ".local.";
        if (actual != null && !actual.equalsIgnoreCase(wanted)) {
            log.warn("RC Timing wanted the name {} on {}, but it is already taken and it is announced as {}",
                    wanted, address, actual);
        } else {
            log.info("RC Timing is announced as {}.local on {}", label.get(), address);
        }
    }

    private void withdrawExcept(List<String> keep) {
        List<JmDNS> gone;
        synchronized (this) {
            gone = announcers.entrySet().stream().filter(e -> !keep.contains(e.getKey())).map(Map.Entry::getValue).toList();
            announcers.keySet().removeIf(address -> !keep.contains(address));
        }
        gone.forEach(MdnsAdvertiser::closeQuietly);
    }

    private static void closeQuietly(JmDNS jmdns) {
        if (jmdns == null) {
            return;
        }
        try {
            // Closing also withdraws every service it registered
            jmdns.close();
        } catch (IOException | RuntimeException e) {
            log.debug("Closing mDNS announcer: {}", e.getMessage());
        }
    }

    /** Withdraws the announcements at shutdown so the name stops answering at once. */
    @PreDestroy
    public void stop() {
        List<JmDNS> open;
        synchronized (this) {
            stopped = true;
            open = List.copyOf(announcers.values());
            announcers.clear();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        open.parallelStream().forEach(MdnsAdvertiser::closeQuietly);
    }
}
