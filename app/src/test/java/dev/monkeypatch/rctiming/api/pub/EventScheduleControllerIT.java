package dev.monkeypatch.rctiming.api.pub;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventScheduleControllerIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void listEvents_anonymous_returns200() {
        // No Authorization header — anonymous call to public endpoint
        ResponseEntity<List> resp = restTemplate.getForEntity("/api/v1/events", List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        // V100 seed inserts "Test Open Event" with status OPEN — must appear in schedule
        assertThat(resp.getBody()).isNotEmpty();
    }

    @Test
    void listEvents_doesNotIncludeDraftEvents() {
        // V100 seeds event 1002 as DRAFT — must NOT appear in public schedule
        ResponseEntity<List> resp = restTemplate.getForEntity("/api/v1/events", List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> events = (List<Map<String, Object>>) resp.getBody();
        assertThat(events).isNotNull();

        boolean hasDraftName = events.stream()
                .anyMatch(e -> "Test Draft Event".equals(e.get("name")));
        assertThat(hasDraftName).as("DRAFT events must not appear in public schedule").isFalse();
    }
}
