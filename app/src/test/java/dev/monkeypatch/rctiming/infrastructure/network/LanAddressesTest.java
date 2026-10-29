package dev.monkeypatch.rctiming.infrastructure.network;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The addresses other devices are told to open (#23) must be URLs a browser accepts. */
class LanAddressesTest {

    @Test
    void buildsUrlsForIpv4Ipv6AndHostnames() {
        assertThat(LanAddresses.url("192.168.1.20", 8080)).isEqualTo("http://192.168.1.20:8080/");
        assertThat(LanAddresses.url("2001:db8::50", 8080)).isEqualTo("http://[2001:db8::50]:8080/");
        assertThat(LanAddresses.url("[2001:db8::50]", 8080)).isEqualTo("http://[2001:db8::50]:8080/");
        assertThat(LanAddresses.url("timing-laptop", 80)).isEqualTo("http://timing-laptop/");
    }

    @Test
    void configuredAddressesReplaceTheDetectedOnes() {
        // In a container the detected addresses are the container's own (#99)
        LanAddresses addresses = new LanAddresses("", new NetworkProperties(
                List.of(" 192.168.1.50", "club-timing ", "http://192.168.1.50:9000/", ""), ""));
        started(addresses, 8080);

        assertThat(addresses.urls()).containsExactly(
                "http://192.168.1.50:8080/", "http://club-timing:8080/", "http://192.168.1.50:9000/");
    }

    @Test
    void anMdnsNameIsListedFirstAndDoesNotNeedTheDotLocal() {
        LanAddresses addresses = new LanAddresses("192.168.1.9", new NetworkProperties(List.of(), "Timing"));
        started(addresses, 8080);

        assertThat(addresses.urls()).containsExactly("http://timing.local:8080/", "http://192.168.1.9:8080/");
    }

    @Test
    void noMdnsNameLeavesTheListAsItWas() {
        LanAddresses addresses = new LanAddresses("192.168.1.9", new NetworkProperties(List.of(), ""));
        started(addresses, 8080);

        assertThat(addresses.urls()).containsExactly("http://192.168.1.9:8080/");
    }

    @Test
    void theNameIsAnnouncedOnlyOnTheBindAddressWhenOneIsSet() {
        LanAddresses addresses = new LanAddresses("192.168.1.9", new NetworkProperties(List.of(), "timing"));
        assertThat(addresses.announceAddresses()).containsExactly("192.168.1.9");
    }

    private static void started(LanAddresses addresses, int port) {
        WebServer server = mock(WebServer.class);
        when(server.getPort()).thenReturn(port);
        WebServerApplicationContext context = mock(WebServerApplicationContext.class);
        when(context.getServerNamespace()).thenReturn(null);
        WebServerInitializedEvent event = mock(WebServerInitializedEvent.class);
        when(event.getWebServer()).thenReturn(server);
        when(event.getApplicationContext()).thenReturn(context);
        addresses.onWebServerStarted(event);
    }
}
