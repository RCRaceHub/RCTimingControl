package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** The officials' result endpoints carry real names, so an anonymous caller must never reach them. */
class OfficialResultsAccessIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;

    @Test
    void eventResultsNeedASignIn() {
        ResponseEntity<Object> response = restTemplate.getForEntity(
                "/api/v1/race-control/event/1/results", Object.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void raceResultNeedsASignIn() {
        ResponseEntity<Object> response = restTemplate.getForEntity(
                "/api/v1/race-control/race/1/result", Object.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
