package dev.monkeypatch.rctiming.infrastructure.tls;

import dev.monkeypatch.rctiming.infrastructure.network.LanAddresses;
import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The club's certificate authority (CA) and the HTTPS certificate it signs (#503), kept in a {@code tls} folder
 * beside the database. Off unless {@code rctiming.https.enabled} is true, when the app serves HTTPS on
 * {@code rctiming.https.port} (default 8443) as well as HTTP.
 *
 * <p>The CA is made once and lasts ten years; devices install it once to trust the app. The server certificate lasts a
 * year and is made again, signed by the same CA, when it is missing a name the laptop can now be reached by (a new
 * address, say) or is within 30 days of expiring, so devices that trust the CA need nothing done. An admin can also
 * make either again from the Club page: a new server certificate is invisible to devices, but a new CA means every
 * device must install the CA again. The files are PKCS12 keystores whose operating-system permissions are the
 * protection; the CA's private key never leaves the laptop and only its certificate is offered for download.
 *
 * <p>Threading: every change is {@code synchronized}; the web server's HTTPS connector reads the keystore through
 * {@link #serverKeyStore()} and is told to reload by {@link #onChange(Runnable)} listeners. A background thread
 * checks the names every ten minutes.
 */
@Component
@EnableConfigurationProperties(HttpsProperties.class)
public class TlsService {

    private static final Logger log = LoggerFactory.getLogger(TlsService.class);

    static final int CA_YEARS = 10;
    static final int SERVER_DAYS = 365;
    private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofDays(30);
    private static final int CHECK_MINUTES = 10;

    private final boolean enabled;
    private final int port;
    private final Path folder;
    private final LanAddresses lan;
    private final Clock clock;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private ClubCertificates.KeyAndCertificate ca;
    private ClubCertificates.KeyAndCertificate server;
    private ScheduledExecutorService scheduler;
    private volatile String caPem;
    private volatile boolean active;

    @Autowired
    public TlsService(HttpsProperties https, DatabaseProperties database, LanAddresses lan) {
        this(https.enabled(), https.port(), database.effectiveDataDirectory().resolve("tls"), lan, Clock.systemUTC());
    }

    TlsService(boolean enabled, int port, Path folder, LanAddresses lan, Clock clock) {
        this.enabled = enabled;
        this.port = port;
        this.folder = folder;
        this.lan = lan;
        this.clock = clock;
    }

    /** Whether HTTPS is switched on in the settings. */
    public boolean enabled() {
        return enabled;
    }

    /** Whether the HTTPS connector is really serving: switched on and its certificates could be made at start-up. */
    public boolean active() {
        return enabled && active;
    }

    /** Called by the connector setup once the HTTPS connector has been added. */
    void markActive() {
        active = true;
    }

    /** The port the HTTPS connector listens on. */
    public int port() {
        return port;
    }

    /** Registers something to run after the server certificate has been replaced, so the connector can reload it. */
    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    /**
     * Loads the CA and the server certificate, making whichever is missing, and makes the server certificate again if
     * it lacks a name the laptop can be reached by now or is about to expire. Called by the web server's setup, so it
     * runs before the app is up, and every ten minutes after.
     *
     * @return true when the server certificate was replaced
     * @throws IllegalStateException if the files cannot be written
     */
    public synchronized boolean ensure() {
        if (ca == null) {
            ca = load(caFile(), ClubCertificates.CA_ALIAS);
            if (ca == null) {
                createCa();
            } else {
                caPem = ClubCertificates.toPem(ca.certificate());
            }
        }
        if (server == null) {
            server = load(serverFile(), ClubCertificates.SERVER_ALIAS);
        }
        if (server == null || needsReissue(server.certificate()) || !signedBy(server.certificate(), ca.certificate())) {
            issueServer();
            return true;
        }
        return false;
    }

    /** Makes the server certificate again, signed by the same CA, so no device has to do anything. */
    public synchronized void regenerateServer() {
        ensure();
        issueServer();
    }

    /** Makes a new CA and a server certificate from it. Every device that trusted the old CA must install this one. */
    public synchronized void regenerateCa() {
        createCa();
        issueServer();
    }

    /** The keystore holding the server key and its certificate chain, under the alias {@code server}. */
    public synchronized KeyStore serverKeyStore() {
        ensure();
        return ClubCertificates.keyStore(ClubCertificates.SERVER_ALIAS, server.key(),
                server.certificate(), ca.certificate());
    }

    /** The keystore password. */
    public String keyStorePassword() {
        return new String(ClubCertificates.PASSWORD);
    }

    /**
     * The CA's certificate as PEM text, for download. Never the key. Read without the lock, because anyone on the
     * venue network can ask for it and the lock may be held while certificates are made.
     */
    public String caPem() {
        String pem = caPem;
        if (pem == null) {
            synchronized (this) {
                ensure();
                pem = caPem;
            }
        }
        return pem;
    }

    /** What the Club page shows: whether HTTPS is on, where, and when the certificates run out. */
    public synchronized Status status() {
        if (!active()) {
            return new Status(false, port, List.of(), List.of(), null, null, null);
        }
        ensure();
        return new Status(true, port, lan.httpsUrls(port), List.copyOf(ClubCertificates.namesIn(server.certificate())),
                ca.certificate().getNotAfter().toInstant(), server.certificate().getNotAfter().toInstant(),
                ClubCertificates.fingerprint(ca.certificate()));
    }

    /**
     * The state of HTTPS. {@code urls} are the HTTPS addresses to open; {@code names} the hosts the server certificate
     * is valid for; the expiries are UTC; {@code caFingerprint} is the CA's SHA-256 fingerprint to check a downloaded
     * file against. Everything after {@code port} is empty or null when HTTPS is off.
     */
    public record Status(boolean enabled, int port, List<String> urls, List<String> names, Instant caExpires,
                         Instant serverExpires, String caFingerprint) {
    }

    /** Starts the ten-minute check of the names once the app is up. */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void startChecking() {
        if (!enabled || scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "tls-check");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (ensure()) {
                    log.info("RC Timing made its HTTPS certificate again, for: {}", wantedHosts());
                }
            } catch (RuntimeException e) {
                log.warn("Could not check the HTTPS certificate: {}", e.getMessage());
            }
        }, CHECK_MINUTES, CHECK_MINUTES, TimeUnit.MINUTES);
    }

    /** Stops the check at shutdown. */
    @PreDestroy
    public synchronized void stopChecking() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private static boolean signedBy(X509Certificate certificate, X509Certificate issuer) {
        try {
            certificate.verify(issuer.getPublicKey());
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private boolean needsReissue(X509Certificate certificate) {
        Instant soon = clock.instant().plus(RENEW_BEFORE_EXPIRY);
        if (certificate.getNotAfter().toInstant().isBefore(soon)) {
            return true;
        }
        // A name the certificate already has is never dropped when the laptop briefly loses one, only added
        Set<String> present = ClubCertificates.namesIn(certificate);
        return wantedHosts().stream().anyMatch(host -> !present.contains(host.toLowerCase(Locale.ROOT)));
    }

    private List<String> wantedHosts() {
        return lan.hostsForCertificate();
    }

    private void createCa() {
        ca = ClubCertificates.newCertificateAuthority(clock.instant(), CA_YEARS);
        save(caFile(), ClubCertificates.keyStore(ClubCertificates.CA_ALIAS, ca.key(), ca.certificate()));
        caPem = ClubCertificates.toPem(ca.certificate());
        log.info("RC Timing made a new club certificate authority; devices need to install it to trust the HTTPS address");
    }

    private void issueServer() {
        // Names the old certificate had stay, so an address that comes and goes does not make a new one each time
        Set<String> hosts = new LinkedHashSet<>(wantedHosts());
        if (server != null) {
            hosts.addAll(ClubCertificates.namesIn(server.certificate()));
        }
        server = ClubCertificates.newServerCertificate(ca, new ArrayList<>(hosts), clock.instant(), SERVER_DAYS);
        save(serverFile(), ClubCertificates.keyStore(ClubCertificates.SERVER_ALIAS, server.key(),
                server.certificate(), ca.certificate()));
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.error("The HTTPS connector could not load the new certificate; a restart will: {}", e.getMessage());
            }
        }
    }

    private Path caFile() {
        return folder.resolve("ca.p12");
    }

    private Path serverFile() {
        return folder.resolve("server.p12");
    }

    /** The key and certificate in the file, or null when there is no readable file (which is then made again). */
    private ClubCertificates.KeyAndCertificate load(Path file, String alias) {
        if (!Files.exists(file)) {
            return null;
        }
        try (var in = Files.newInputStream(file)) {
            return ClubCertificates.entry(ClubCertificates.read(in), alias);
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            log.warn("Could not read {}; making it again: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }

    private void save(Path file, KeyStore store) {
        try {
            Files.createDirectories(folder);
            Path temporary = Files.createTempFile(folder, file.getFileName().toString(), ".tmp");
            try (OutputStream out = Files.newOutputStream(temporary)) {
                ClubCertificates.write(store, out);
                ownerOnly(temporary);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file, e);
        }
    }

    /** Keeps other users of the machine out of the key where the file system has permissions to do it. */
    private static void ownerOnly(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows has no POSIX permissions; the data folder's own access rules apply there
        }
    }
}
