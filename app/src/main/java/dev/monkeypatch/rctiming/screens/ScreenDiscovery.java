package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.infrastructure.network.LanAddresses;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Finds EventScreenDisplay screens on the venue network (#708) by browsing the mDNS service {@code
 * _eventscreen._tcp}, which each screen's agent advertises on its API port with TXT records {@code id}, {@code api}
 * and {@code version}. Each hit is handed to {@link ScreenControlService#discovered}, which asks the screen for its
 * {@code /info} and adds it or updates its address, keyed by the screen's own id.
 *
 * <p>Like {@code MdnsAdvertiser} it listens on each real IPv4 interface ({@link LanAddresses#announceAddresses()}),
 * from one background thread that starts once the app is ready and looks at the interfaces and asks again every {@value
 * #RESCAN_SECONDS} seconds, so a screen that boots late, or a laptop that changes network, is picked up. Setting up
 * multicast can take seconds and must never delay or fail start-up: a failure is logged and the app carries on, and
 * screens can always be added by hand. Handling a hit calls the screen over HTTP, so that runs on its own single
 * thread rather than JmDNS's. It cannot cross a Docker bridge network; use host networking or add screens by hand.
 * Off when {@code rctiming.screens.discovery} is false.
 */
@Component
@EnableConfigurationProperties(ScreensProperties.class)
public class ScreenDiscovery {

    /** The DNS-SD service type the screens advertise. */
    static final String SERVICE_TYPE = "_eventscreen._tcp.local.";
    static final int RESCAN_SECONDS = 30;

    private static final Logger log = LoggerFactory.getLogger(ScreenDiscovery.class);

    private final boolean enabled;
    private final LanAddresses lan;
    private final ScreenControlService control;
    /** One browser per interface address; touched only under {@code this}. */
    private final Map<String, JmDNS> browsers = new HashMap<>();
    private final ExecutorService handler = Executors.newSingleThreadExecutor(r -> daemon(r, "screen-discovery-handle"));
    private ScheduledExecutorService scheduler;
    private boolean stopped;

    public ScreenDiscovery(ScreensProperties properties, LanAddresses lan, ScreenControlService control) {
        this.enabled = properties.discovery();
        this.lan = lan;
        this.control = control;
    }

    /** Starts browsing on a daemon thread once the web server is up. */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!enabled || stopped || scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "screen-discovery"));
        scheduler.scheduleWithFixedDelay(this::rescan, 0, RESCAN_SECONDS, TimeUnit.SECONDS);
    }

    /** Asks now instead of waiting for the next pass; the "Scan" button. Returns at once. */
    public synchronized void scanNow() {
        if (scheduler != null && !stopped) {
            scheduler.execute(this::rescan);
        }
    }

    /** Whether discovery is switched on in the settings, for the Screens page to say so. */
    public boolean enabled() {
        return enabled;
    }

    private void rescan() {
        try {
            List<String> wanted = lan.announceAddresses();
            for (String address : wanted) {
                if (!hasBrowser(address)) {
                    browseOn(address);
                }
            }
            closeExcept(wanted);
            for (JmDNS jmdns : snapshot()) {
                for (ServiceInfo found : jmdns.list(SERVICE_TYPE, 2000)) {
                    handle(found);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Screen discovery pass failed: {}", e.getMessage());
        }
    }

    private synchronized boolean hasBrowser(String address) {
        return browsers.containsKey(address);
    }

    private synchronized List<JmDNS> snapshot() {
        return List.copyOf(browsers.values());
    }

    private void browseOn(String address) {
        JmDNS jmdns = null;
        try {
            jmdns = JmDNS.create(InetAddress.getByName(address));
            JmDNS owner = jmdns;
            jmdns.addServiceListener(SERVICE_TYPE, new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    owner.requestServiceInfo(event.getType(), event.getName(), true);
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    // A screen that has gone stays in the list as offline; the poller notices
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    handle(event.getInfo());
                }
            });
            synchronized (this) {
                if (!stopped) {
                    browsers.put(address, jmdns);
                    jmdns = null;
                }
            }
            log.info("Looking for display screens on {}", address);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not look for display screens on {}: {}", address, e.getMessage());
        } finally {
            closeQuietly(jmdns);
        }
    }

    /** Queues one found service: its IPv4 address is used, since the JVM cannot resolve {@code .local} names itself. */
    private void handle(ServiceInfo info) {
        if (info == null) {
            return;
        }
        String host = null;
        for (Inet4Address address : info.getInet4Addresses()) {
            host = address.getHostAddress();
            break;
        }
        if (host == null) {
            return;
        }
        String found = host;
        int port = info.getPort();
        handler.execute(() -> {
            try {
                control.discovered(found, port);
            } catch (RuntimeException e) {
                log.debug("Ignoring {}:{} found by mDNS: {}", found, port, e.getMessage());
            }
        });
    }

    private void closeExcept(List<String> keep) {
        List<JmDNS> gone;
        synchronized (this) {
            gone = browsers.entrySet().stream().filter(e -> !keep.contains(e.getKey())).map(Map.Entry::getValue).toList();
            browsers.keySet().removeIf(address -> !keep.contains(address));
        }
        gone.forEach(ScreenDiscovery::closeQuietly);
    }

    private static void closeQuietly(JmDNS jmdns) {
        if (jmdns == null) {
            return;
        }
        try {
            jmdns.close();
        } catch (IOException | RuntimeException e) {
            log.debug("Closing screen browser: {}", e.getMessage());
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread thread = new Thread(r, name);
        thread.setDaemon(true);
        return thread;
    }

    /** Stops browsing at shutdown. */
    @PreDestroy
    public void stop() {
        List<JmDNS> open;
        synchronized (this) {
            stopped = true;
            open = List.copyOf(browsers.values());
            browsers.clear();
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        }
        handler.shutdownNow();
        open.forEach(ScreenDiscovery::closeQuietly);
    }
}
