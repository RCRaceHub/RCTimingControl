package dev.monkeypatch.rctiming.api.pub;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.championship.Championship;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipRepository;
import dev.monkeypatch.rctiming.domain.championship.ScoringSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PublicChampionshipController.
 * Tests: public GET /api/v1/championships/{id} returns standings without auth (CHAMP-05);
 *        unknown id returns 404 not 403.
 */
class PublicChampionshipControllerTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ChampionshipRepository championshipRepository;

    @Test
    void publicStandingsReturnWithoutAuth() {
        // Create a championship with no event links (empty standings)
        Championship champ = new Championship();
        champ.setName("Test Champ " + UUID.randomUUID());
        champ.setTqBonusPoints(0);
        champ.setAfinalWinnerBonusPoints(0);
        champ.setScoringSource(ScoringSource.FINALS);
        Instant now = Instant.now();
        champ.setCreatedAt(now);
        champ.setUpdatedAt(now);
        champ = championshipRepository.save(champ);

        // No Authorization header — TestRestTemplate sends no auth by default
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/championships/" + champ.getId(), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Championship with no event links returns empty standings list
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void unknownChampionshipIdReturns404NotForbidden() {
        // Must be 404 — public endpoint; unknown ID must not return 403 (which implies auth gate)
        ResponseEntity<Object> response = restTemplate.getForEntity(
                "/api/v1/championships/999999", Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
