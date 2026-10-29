package dev.monkeypatch.rctiming.config;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The developer Javadoc served at {@code /javadoc} when its flag is on (#352). The test classpath carries a
 * stand-in {@code javadoc/index.html}, as the packaged jar carries the real build.
 */
@TestPropertySource(properties = "rctiming.docs.javadoc.enabled=true")
class JavadocIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;

    @Test
    void servesTheJavadocWithoutSigningIn() {
        ResponseEntity<String> page = restTemplate.getForEntity("/javadoc/index.html", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("API docs");
    }

    @Test
    void theBarePathLeadsToTheIndex() {
        ResponseEntity<String> page = restTemplate.getForEntity("/javadoc", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("API docs");
    }

    @Test
    void aMissingJavadocFileIs404NotThePage() {
        assertThat(restTemplate.getForEntity("/javadoc/missing.html", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
