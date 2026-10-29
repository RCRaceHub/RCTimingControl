package dev.monkeypatch.rctiming.infrastructure.tls;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Adds an HTTPS connector beside the web server's HTTP one when {@code rctiming.https.enabled} is true (#503), so
 * phones and boards keep using plain HTTP while a browser that needs a secure page (the audio output page's sound card
 * list) can use HTTPS. The certificate comes from {@link TlsService}, and the connector reloads it when the service
 * makes a new one, so regenerating from the Club page needs no restart. It listens on {@code server.address} like the
 * HTTP connector; other {@code server.tomcat.*} settings apply only to the HTTP one.
 */
@Component
public class HttpsConnectorCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private static final Logger log = LoggerFactory.getLogger(HttpsConnectorCustomizer.class);

    private final TlsService tls;
    private final ServerProperties server;

    public HttpsConnectorCustomizer(TlsService tls, ObjectProvider<ServerProperties> server) {
        this.tls = tls;
        this.server = server.getIfAvailable();
    }

    /** Runs while the web server is being built, before the app is up; does nothing when HTTPS is off. */
    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (!tls.enabled()) {
            return;
        }
        try {
            tls.ensure();
        } catch (RuntimeException e) {
            // A broken data folder must not stop the app serving HTTP
            log.error("HTTPS is switched on but its certificates could not be made, so it is off: {}", e.getMessage());
            return;
        }
        Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(tls.port());
        connector.setScheme("https");
        connector.setSecure(true);

        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        protocol.setSSLEnabled(true);
        SSLHostConfig hostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(hostConfig, SSLHostConfigCertificate.Type.RSA);
        certificate.setCertificateKeystore(tls.serverKeyStore());
        certificate.setCertificateKeyAlias(ClubCertificates.SERVER_ALIAS);
        certificate.setCertificateKeyPassword(tls.keyStorePassword());
        hostConfig.addCertificate(certificate);
        protocol.addSslHostConfig(hostConfig);
        if (server != null && server.getAddress() != null) {
            protocol.setAddress(server.getAddress());
        }
        factory.addAdditionalConnectors(connector);
        tls.markActive();

        tls.onChange(() -> {
            certificate.setCertificateKeystore(tls.serverKeyStore());
            protocol.reloadSslHostConfigs();
        });
    }
}
