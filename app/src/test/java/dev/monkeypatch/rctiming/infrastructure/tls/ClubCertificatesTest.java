package dev.monkeypatch.rctiming.infrastructure.tls;

import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClubCertificatesTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    void theServerCertificateIsSignedByTheCaAndNamesEveryHost() throws Exception {
        var ca = ClubCertificates.newCertificateAuthority(NOW, 10);
        var server = ClubCertificates.newServerCertificate(ca,
                List.of("timing.local", "192.168.1.50", "Laptop", "localhost", "127.0.0.1"), NOW, 365);

        server.certificate().verify(ca.certificate().getPublicKey());
        assertThat(ClubCertificates.namesIn(server.certificate()))
                .containsExactlyInAnyOrder("timing.local", "192.168.1.50", "laptop", "localhost", "127.0.0.1");
        assertThat(server.certificate().getIssuerX500Principal()).isEqualTo(ca.certificate().getSubjectX500Principal());
        assertThat(server.certificate().getBasicConstraints()).isEqualTo(-1);
        assertThat(server.certificate().getExtendedKeyUsage()).contains("1.3.6.1.5.5.7.3.1");
    }

    @Test
    void theCaIsACaThatLastsTenYearsAndTheServerCertificateAYear() throws Exception {
        var ca = ClubCertificates.newCertificateAuthority(NOW, 10);
        var server = ClubCertificates.newServerCertificate(ca, List.of("timing.local"), NOW, 365);

        assertThat(ca.certificate().getBasicConstraints()).isGreaterThanOrEqualTo(0);
        assertThat(ca.certificate().getNotAfter().toInstant()).isAfter(NOW.plusSeconds(3650L * 86_400 - 3_600));
        assertThat(server.certificate().getNotAfter().toInstant()).isBefore(NOW.plusSeconds(366L * 86_400));
        // Started a day early, so a device with a slow clock accepts it
        assertThat(server.certificate().getNotBefore().toInstant()).isBefore(NOW);
    }

    @Test
    void aKeystoreRoundTripsTheKeyAndCertificate() throws Exception {
        var ca = ClubCertificates.newCertificateAuthority(NOW, 1);
        var store = ClubCertificates.keyStore(ClubCertificates.CA_ALIAS, ca.key(), ca.certificate());
        var bytes = new java.io.ByteArrayOutputStream();
        ClubCertificates.write(store, bytes);

        var loaded = ClubCertificates.entry(ClubCertificates.read(new java.io.ByteArrayInputStream(bytes.toByteArray())),
                ClubCertificates.CA_ALIAS);

        assertThat(loaded.certificate()).isEqualTo(ca.certificate());
        assertThat(loaded.key().getEncoded()).isEqualTo(ca.key().getEncoded());
    }

    @Test
    void pemAndFingerprintLookRight() {
        X509Certificate certificate = ClubCertificates.newCertificateAuthority(NOW, 1).certificate();

        assertThat(ClubCertificates.toPem(certificate)).startsWith("-----BEGIN CERTIFICATE-----\n")
                .endsWith("-----END CERTIFICATE-----\n");
        assertThat(ClubCertificates.fingerprint(certificate)).matches("([0-9A-F]{2}:){31}[0-9A-F]{2}");
    }

    @Test
    void tellsIpAddressesFromNames() {
        assertThat(ClubCertificates.isIpAddress("192.168.1.5")).isTrue();
        assertThat(ClubCertificates.isIpAddress("timing.local")).isFalse();
    }
}
