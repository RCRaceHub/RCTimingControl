package dev.monkeypatch.rctiming.config;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** With the flag at its default (off), the Javadoc is not served even if it is on the classpath (#352). */
class JavadocOffIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;

    @Test
    void doesNotServeTheJavadocByDefault() {
        ResponseEntity<String> page = restTemplate.getForEntity("/javadoc/index.html", String.class);
        // The frontend stand-in answers instead, as it does for any page that is not the server's
        assertThat(page.getBody()).doesNotContain("API docs").contains("<div id=\"root\">");
    }
}
