package dev.monkeypatch.rctiming.infrastructure.tls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Makes the club's own certificate authority (CA) and the certificates it signs for the app's HTTPS address (#503).
 * Spring-free and pure apart from the random keys, so it can be tested on its own; {@link TlsService} decides when to
 * call it and where the files live.
 *
 * <p>A device that trusts the CA once trusts every certificate the CA issues, so the server certificate can be
 * renewed or re-issued for a new address without touching the devices. Keys are RSA (the widest support, including
 * older Android phones); both certificates are signed with SHA-256.
 */
final class ClubCertificates {

    /** The keystore password: the files are protected by the operating system's permissions, not by this. */
    static final char[] PASSWORD = "rctiming".toCharArray();

    static final String CA_ALIAS = "ca";
    static final String SERVER_ALIAS = "server";

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private ClubCertificates() {
    }

    /** A private key and its certificate. */
    record KeyAndCertificate(PrivateKey key, X509Certificate certificate) {
    }

    /** Whether {@code host} is an IPv4 address (a certificate names those apart from host names). */
    static boolean isIpAddress(String host) {
        return IPV4.matcher(host).matches();
    }

    /**
     * A new CA, valid from a day ago (so a device whose clock is a little behind still accepts it) for {@code years}.
     *
     * @throws IllegalStateException if the keys or certificate cannot be made, which would be a broken Java runtime
     */
    static KeyAndCertificate newCertificateAuthority(Instant now, int years) {
        try {
            KeyPair pair = keyPair(3072);
            X500Name name = new X500Name("CN=RC Timing club CA");
            JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, serial(),
                    Date.from(now.minus(Duration.ofDays(1))), Date.from(now.plus(Duration.ofDays(365L * years))),
                    name, pair.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            builder.addExtension(Extension.subjectKeyIdentifier, false, utils.createSubjectKeyIdentifier(pair.getPublic()));
            return new KeyAndCertificate(pair.getPrivate(), sign(builder, pair.getPrivate()));
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new IllegalStateException("Could not make the club certificate authority", e);
        }
    }

    /**
     * A certificate for the app's HTTPS address, signed by {@code ca}, naming every host in {@code hosts} (IPv4
     * addresses and host names alike).
     *
     * @param ca    the club's CA
     * @param hosts at least one host name or IPv4 address; the first is the certificate's common name
     * @param now   when it starts, less a day for clock skew
     * @param days  how long it lasts; Apple devices refuse more than 825 days
     * @throws IllegalStateException if the keys or certificate cannot be made
     */
    static KeyAndCertificate newServerCertificate(KeyAndCertificate ca, List<String> hosts, Instant now, int days) {
        try {
            KeyPair pair = keyPair(2048);
            X500Name issuer = X500Name.getInstance(ca.certificate().getSubjectX500Principal().getEncoded());
            X500Name subject = new X500Name("CN=" + hosts.get(0).replace(",", " ").replace("=", " "));
            JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, serial(),
                    Date.from(now.minus(Duration.ofDays(1))), Date.from(now.plus(Duration.ofDays(days))),
                    subject, pair.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
            builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
            builder.addExtension(Extension.subjectKeyIdentifier, false, utils.createSubjectKeyIdentifier(pair.getPublic()));
            builder.addExtension(Extension.authorityKeyIdentifier, false, utils.createAuthorityKeyIdentifier(ca.certificate()));
            builder.addExtension(Extension.subjectAlternativeName, false, names(hosts));
            return new KeyAndCertificate(pair.getPrivate(), sign(builder, ca.key()));
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new IllegalStateException("Could not make the server certificate", e);
        }
    }

    /** The host names and IPv4 addresses a certificate is valid for, lower case. */
    static Set<String> namesIn(X509Certificate certificate) {
        Set<String> names = new LinkedHashSet<>();
        try {
            Collection<List<?>> alternatives = certificate.getSubjectAlternativeNames();
            if (alternatives != null) {
                for (List<?> alternative : alternatives) {
                    // 2 is a DNS name, 7 an IP address
                    int type = (Integer) alternative.get(0);
                    if (type == 2 || type == 7) {
                        names.add(String.valueOf(alternative.get(1)).toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (GeneralSecurityException e) {
            // An unreadable name list reads as no names, so the certificate is made again
        }
        return names;
    }

    /** The certificate as PEM text, the form every operating system's certificate import takes. */
    static String toPem(X509Certificate certificate) {
        try {
            String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate.getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encode the certificate", e);
        }
    }

    /** The certificate's SHA-256 fingerprint as colon-separated upper-case hex, for checking a download by eye. */
    static String fingerprint(X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not fingerprint the certificate", e);
        }
    }

    /** A PKCS12 keystore holding {@code alias}: the key, its certificate and any chain above it. */
    static KeyStore keyStore(String alias, PrivateKey key, Certificate... chain) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            store.setKeyEntry(alias, key, PASSWORD, chain);
            return store;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Could not build the keystore", e);
        }
    }

    static void write(KeyStore store, OutputStream out) throws IOException {
        try {
            store.store(out, PASSWORD);
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not write the keystore", e);
        }
    }

    static KeyStore read(InputStream in) throws IOException {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(in, PASSWORD);
            return store;
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not read the keystore", e);
        }
    }

    /** The key and certificate under {@code alias}, or null when the store has none. */
    static KeyAndCertificate entry(KeyStore store, String alias) throws GeneralSecurityException {
        if (!store.isKeyEntry(alias)) {
            return null;
        }
        return new KeyAndCertificate((PrivateKey) store.getKey(alias, PASSWORD), (X509Certificate) store.getCertificate(alias));
    }

    private static GeneralNames names(List<String> hosts) {
        return new GeneralNames(hosts.stream()
                .map(host -> isIpAddress(host)
                        ? new GeneralName(GeneralName.iPAddress, host)
                        : new GeneralName(GeneralName.dNSName, host.toLowerCase(Locale.ROOT)))
                .toArray(GeneralName[]::new));
    }

    private static KeyPair keyPair(int bits) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits, RANDOM);
        return generator.generateKeyPair();
    }

    private static BigInteger serial() {
        return new BigInteger(120, RANDOM).add(BigInteger.ONE);
    }

    private static X509Certificate sign(X509v3CertificateBuilder builder, PrivateKey key)
            throws OperatorCreationException, GeneralSecurityException {
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(key);
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
