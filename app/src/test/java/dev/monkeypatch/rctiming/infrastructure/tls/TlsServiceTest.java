package dev.monkeypatch.rctiming.infrastructure.tls;

import dev.monkeypatch.rctiming.infrastructure.network.LanAddresses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TlsServiceTest {

    @TempDir
    Path folder;

    private final LanAddresses lan = mock(LanAddresses.class);
    private Clock clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);

    private TlsService service() {
        TlsService service = new TlsService(true, 8443, folder.resolve("tls"), lan, clock);
        service.markActive();
        return service;
    }

    @Test
    void makesTheCaAndServerCertificateOnFirstUseAndKeepsThemAcrossRestarts() throws Exception {
        when(lan.hostsForCertificate()).thenReturn(List.of("timing.local", "192.168.1.50", "localhost"));

        TlsService first = service();
        assertThat(first.ensure()).isTrue();
        String ca = first.caPem();
        assertThat(Files.exists(folder.resolve("tls/ca.p12"))).isTrue();
        assertThat(Files.exists(folder.resolve("tls/server.p12"))).isTrue();

        TlsService restarted = service();
        assertThat(restarted.ensure()).isFalse();
        assertThat(restarted.caPem()).isEqualTo(ca);
        assertThat(restarted.status().names()).contains("timing.local", "192.168.1.50", "localhost");
    }

    @Test
    void makesTheServerCertificateAgainForANewAddressWithoutChangingTheCa() {
        when(lan.hostsForCertificate()).thenReturn(List.of("timing.local", "localhost"));
        TlsService service = service();
        service.ensure();
        String ca = service.caPem();
        AtomicInteger reloads = new AtomicInteger();
        service.onChange(reloads::incrementAndGet);

        when(lan.hostsForCertificate()).thenReturn(List.of("timing.local", "10.0.0.7", "localhost"));

        assertThat(service.ensure()).isTrue();
        assertThat(service.status().names()).contains("10.0.0.7");
        assertThat(service.caPem()).isEqualTo(ca);
        assertThat(reloads.get()).isEqualTo(1);
    }

    @Test
    void aNameThatDisappearsForAWhileDoesNotCauseANewCertificate() {
        when(lan.hostsForCertificate()).thenReturn(List.of("timing.local", "10.0.0.7", "localhost"));
        TlsService service = service();
        service.ensure();

        when(lan.hostsForCertificate()).thenReturn(List.of("timing.local", "localhost"));

        assertThat(service.ensure()).isFalse();
    }

    @Test
    void aNewCertificateKeepsTheNamesTheOldOneHad() {
        when(lan.hostsForCertificate()).thenReturn(List.of("timing.local", "10.0.0.7"));
        TlsService service = service();
        service.ensure();

        when(lan.hostsForCertificate()).thenReturn(List.of("timing.local"));
        service.regenerateServer();

        assertThat(service.status().names()).contains("timing.local", "10.0.0.7");
    }

    @Test
    void makesTheServerCertificateAgainWhenItIsAboutToExpire() {
        when(lan.hostsForCertificate()).thenReturn(List.of("timing.local"));
        TlsService service = service();
        service.ensure();

        clock = Clock.fixed(Instant.parse("2027-10-01T10:00:00Z"), ZoneOffset.UTC);
        TlsService later = service();

        assertThat(later.ensure()).isTrue();
    }

    @Test
    void regeneratingTheServerKeepsTheCaAndRegeneratingTheCaChangesIt() {
        when(lan.hostsForCertificate()).thenReturn(List.of("timing.local"));
        TlsService service = service();
        service.ensure();
        String ca = service.caPem();

        service.regenerateServer();
        assertThat(service.caPem()).isEqualTo(ca);

        service.regenerateCa();
        assertThat(service.caPem()).isNotEqualTo(ca);
        assertThat(service.status().names()).contains("timing.local");
    }

    @Test
    void anUnreadableFileIsMadeAgainInsteadOfStoppingTheApp() throws Exception {
        when(lan.hostsForCertificate()).thenReturn(List.of("timing.local"));
        Files.createDirectories(folder.resolve("tls"));
        Files.writeString(folder.resolve("tls/ca.p12"), "not a keystore");

        assertThat(service().caPem()).startsWith("-----BEGIN CERTIFICATE-----");
    }

    @Test
    void reportsNothingWhenHttpsIsOff() {
        TlsService off = new TlsService(false, 8443, folder.resolve("tls"), lan, clock);

        assertThat(off.status().enabled()).isFalse();
        assertThat(off.status().urls()).isEmpty();
        assertThat(Files.exists(folder.resolve("tls"))).isFalse();
    }
}
