package dev.monkeypatch.rctiming.api.admin;

import org.springframework.context.ApplicationEventPublisher;
import dev.monkeypatch.rctiming.timing.RaceClockService;
import com.fasterxml.jackson.databind.JsonNode;
import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.jooq.generated.tables.records.EntryAuditLogRecord;
import dev.monkeypatch.rctiming.query.racecontrol.LapThresholdQuery;
import dev.monkeypatch.rctiming.service.RoundGeneratorService;
import dev.monkeypatch.rctiming.service.dto.RoundGenerationRequest;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveTimingHub;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static dev.monkeypatch.rctiming.jooq.generated.tables.EntryAuditLog.ENTRY_AUDIT_LOG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Walk-in entries added by hand (L9, #17). */
class WalkInEntryIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntryRepository entryRepository;
    @Autowired CompetitorRepository competitorRepository;
    @Autowired RaceEntryRepository raceEntryRepository;
    @Autowired RoundGeneratorService roundGeneratorService;
    @Autowired JdbcTemplate jdbc;
    @Autowired DSLContext dsl;

    private String run;
    private String adminToken;
    private long eventId;
    private long classId;

    @BeforeEach
    void setUp() {
        run = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        adminToken = loginAs(Set.of(Role.RACE_DIRECTOR));
        eventId = jdbc.queryForObject("""
                insert into events (name, event_date, status)
                values (?, date('now'), 'IN_PROGRESS') returning id""",
                Long.class, "Walk-in event " + run);
        long racingClassId = jdbc.queryForObject("""
                insert into racing_classes (name) values (?) returning id""",
                Long.class, "Walk-in class " + run);
        classId = jdbc.queryForObject("""
                insert into event_classes (event_id, racing_class_id, config_snapshot)
                values (?, ?, '{"type":"TIMED"}') returning id""",
                Long.class, eventId, racingClassId);
    }

    @Test
    void createsAWalkInWithANewCompetitor() {
        ResponseEntity<JsonNode> resp = create(Map.of(
                "competitorName", "  Wendy Walkin  ", "primaryTransponder", "61" + run,
                "secondaryTransponder", "62" + run));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("warnings")).isEmpty();
        long entryId = resp.getBody().get("entry").get("id").asLong();

        Entry entry = entryRepository.findById(entryId).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.CONFIRMED);
        assertThat(entry.getExternalSource()).isNull();
        assertThat(entry.getEventClassId()).isEqualTo(classId);
        assertThat(entry.getTransponderNumberSnapshot()).isEqualTo("61" + run);
        assertThat(entry.getSecondaryTransponderNumber()).isEqualTo("62" + run);

        Competitor competitor = competitorRepository.findById(entry.getCompetitorId()).orElseThrow();
        assertThat(competitor.getDisplayName()).isEqualTo("Wendy Walkin");
        assertThat(competitor.getExternalSource()).isNull();

        assertThat(entryLog(entryId))
                .extracting(EntryAuditLogRecord::getAction).containsExactly("ADMIN_CREATE");
    }

    @Test
    void createsAWalkInForAnExistingCompetitor() {
        long competitorId = createCompetitor("Existing Driver " + run);

        ResponseEntity<JsonNode> resp = create(Map.of("competitorId", competitorId, "primaryTransponder", "63" + run));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Entry entry = entryRepository.findById(resp.getBody().get("entry").get("id").asLong()).orElseThrow();
        assertThat(entry.getCompetitorId()).isEqualTo(competitorId);
        // With no secondary transponder the audit row records JSON null, not the string "null"
        String after = entryLog(entry.getId()).get(0).getAfterSnapshot();
        assertThat(after).contains("\"secondaryTransponderNumber\":null");
    }

    @Test
    void lastTranspondersAreThoseOfTheNewestEntry_andEmptyForANewDriver() {
        long competitorId = createCompetitor("Returning " + run);
        assertThat(lastTransponders(competitorId).get("primaryTransponder").isNull()).isTrue();

        create(Map.of("competitorId", competitorId, "primaryTransponder", "71" + run));
        long secondClass = jdbc.queryForObject("""
                insert into event_classes (event_id, racing_class_id, config_snapshot)
                select event_id, racing_class_id, config_snapshot from event_classes where id = ? returning id""",
                Long.class, classId);
        Map<String, Object> second = Map.of("eventId", eventId, "eventClassId", secondClass,
                "competitorId", competitorId, "primaryTransponder", "72" + run,
                "secondaryTransponder", "73" + run, "thirdTransponder", "74" + run);
        restTemplate.exchange("/api/v1/admin/entries", HttpMethod.POST,
                new HttpEntity<>(second, headers(adminToken)), JsonNode.class);

        JsonNode last = lastTransponders(competitorId);
        assertThat(last.get("primaryTransponder").asText()).isEqualTo("72" + run);
        assertThat(last.get("secondaryTransponder").asText()).isEqualTo("73" + run);
        assertThat(last.get("thirdTransponder").asText()).isEqualTo("74" + run);
        assertThat(last.get("fourthTransponder").isNull()).isTrue();
    }

    private JsonNode lastTransponders(long competitorId) {
        return restTemplate.exchange("/api/v1/admin/competitors/" + competitorId + "/last-transponders",
                HttpMethod.GET, new HttpEntity<>(headers(adminToken)), JsonNode.class).getBody();
    }

    @Test
    void rejectsASecondEntryForTheSameCompetitorInTheClass() {
        long competitorId = createCompetitor("Twice " + run);
        assertThat(create(Map.of("competitorId", competitorId, "primaryTransponder", "64" + run)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        var second = create(Map.of("competitorId", competitorId, "primaryTransponder", "65" + run));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(entryRepository.findByEventId(eventId)).hasSize(1);
    }

    @Test
    void warnsWhenTheTransponderIsAlreadyUsedInTheEvent() {
        create(Map.of("competitorName", "First " + run, "primaryTransponder", "66" + run,
                "secondaryTransponder", "67" + run));

        ResponseEntity<JsonNode> resp = create(Map.of("competitorName", "Second " + run, "primaryTransponder", "67" + run));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("warnings")).hasSize(1);
        assertThat(resp.getBody().get("warnings").get(0).asText()).contains("67" + run);
    }

    @Test
    void walkInIsGriddedAndGetsLaps() {
        String transponder = "68" + run;
        long entryId = create(Map.of("competitorName", "Gridded " + run, "primaryTransponder", transponder))
                .getBody().get("entry").get("id").asLong();

        // The round generator puts the walk-in in a qualifying heat
        roundGeneratorService.generate(new RoundGenerationRequest(eventId, 0, 1, 8,
                List.of(new RoundGenerationRequest.ClassFinalsConfig(classId, 1))));
        long raceId = jdbc.queryForObject("""
                select r.id from race_entries re
                join races r on r.id = re.race_id join rounds ro on ro.id = r.round_id
                where re.entry_id = ? and ro.type = 'QUALIFIER'""", Long.class, entryId);

        // Run that heat, as race control does, and feed it passings
        jdbc.update("update races set status = 'RUNNING' where id = ?", raceId);

        // Called directly so the passings are processed synchronously (the bean's listener is @Async)
        LapTimingService timing = new LapTimingService(mock(LiveTimingHub.class), raceEntryRepository,
                entryRepository, competitorRepository, mock(LapThresholdQuery.class),
                mock(RaceClockService.class), mock(ApplicationEventPublisher.class));
        timing.onLapPassing(new LapPassingEvent(raceId, transponder, 1_000_000L));
        timing.onLapPassing(new LapPassingEvent(raceId, transponder, 31_000_000L));

        LiveTimingRowDto row = timing.peek(raceId).orElseThrow().calculatePositions().stream()
                .filter(r -> r.entryId() == entryId).findFirst().orElseThrow();
        assertThat(row.lapsCompleted()).isEqualTo(1);
    }

    @Test
    void aTypedNameThatMatchesAnExistingDriverIsRefusedWithTheMatches() {
        long existing = createCompetitor("Alex Rowe " + run);

        ResponseEntity<JsonNode> resp = create(Map.of("competitorName", "  alex   ROWE " + run,
                "primaryTransponder", "75" + run));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().get("code").asText()).isEqualTo("POSSIBLE_DUPLICATE_COMPETITOR");
        assertThat(resp.getBody().get("matches")).hasSize(1);
        assertThat(resp.getBody().get("matches").get(0).get("id").asLong()).isEqualTo(existing);
        assertThat(resp.getBody().get("matches").get(0).get("displayName").asText()).isEqualTo("Alex Rowe " + run);
        assertThat(entryRepository.findByEventId(eventId)).isEmpty();
        assertThat(competitorRepository.findBySameName("Alex Rowe " + run)).hasSize(1);
    }

    @Test
    void confirmingItIsADifferentPersonCreatesTheNewDriver() {
        long existing = createCompetitor("Sam Ito " + run);

        ResponseEntity<JsonNode> resp = create(Map.of("competitorName", "Sam Ito " + run,
                "confirmNewCompetitor", true, "primaryTransponder", "76" + run));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long entryId = resp.getBody().get("entry").get("id").asLong();
        assertThat(entryRepository.findById(entryId).orElseThrow().getCompetitorId()).isNotEqualTo(existing);
        assertThat(competitorRepository.findBySameName("Sam Ito " + run)).hasSize(2);
    }

    @Test
    void pickingTheExistingDriverReusesThem() {
        long existing = createCompetitor("Pat Lee " + run);

        ResponseEntity<JsonNode> resp = create(Map.of("competitorId", existing, "primaryTransponder", "77" + run));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long entryId = resp.getBody().get("entry").get("id").asLong();
        assertThat(entryRepository.findById(entryId).orElseThrow().getCompetitorId()).isEqualTo(existing);
        assertThat(competitorRepository.findBySameName("Pat Lee " + run)).hasSize(1);
    }

    @Test
    void requiresACompetitor() {
        var resp = create(Map.of("primaryTransponder", "69" + run));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsACompetitorIdAndANameTogether() {
        long competitorId = createCompetitor("Both " + run);
        var resp = create(Map.of("competitorId", competitorId, "competitorName", "Someone Else " + run,
                "primaryTransponder", "72" + run));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(entryRepository.findByEventId(eventId)).isEmpty();
    }

    @Test
    void rejectsTheSameTransponderAsPrimaryAndSecondary() {
        var resp = create(Map.of("competitorName", "Same " + run, "primaryTransponder", "73" + run,
                "secondaryTransponder", " 73" + run + " "));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(entryRepository.findByEventId(eventId)).isEmpty();
    }

    @Test
    void rejectsAWalkInForACompletedEvent() {
        jdbc.update("update events set status = 'COMPLETED' where id = ?", eventId);
        var resp = create(Map.of("competitorName", "Late " + run, "primaryTransponder", "74" + run));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(entryRepository.findByEventId(eventId)).isEmpty();
    }

    @Test
    void rejectsAClassFromAnotherEvent() {
        Map<String, Object> body = new HashMap<>(Map.of(
                "eventId", eventId, "eventClassId", 2001L, "competitorName", "Wrong " + run,
                "primaryTransponder", "70" + run));
        var resp = restTemplate.exchange("/api/v1/admin/entries", HttpMethod.POST,
                new HttpEntity<>(body, headers(adminToken)), JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refereeCannotAddEntries() {
        String refereeToken = loginAs(Set.of(Role.REFEREE));
        Map<String, Object> body = new HashMap<>(Map.of(
                "eventId", eventId, "eventClassId", classId, "competitorName", "Ref " + run,
                "primaryTransponder", "71" + run));
        var resp = restTemplate.exchange("/api/v1/admin/entries", HttpMethod.POST,
                new HttpEntity<>(body, headers(refereeToken)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private ResponseEntity<JsonNode> create(Map<String, Object> fields) {
        Map<String, Object> body = new HashMap<>(fields);
        body.put("eventId", eventId);
        body.put("eventClassId", classId);
        return restTemplate.exchange("/api/v1/admin/entries", HttpMethod.POST,
                new HttpEntity<>(body, headers(adminToken)), JsonNode.class);
    }

    private long createCompetitor(String name) {
        Competitor c = new Competitor();
        c.setDisplayName(name);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        return competitorRepository.save(c).getId();
    }

    private String loginAs(Set<Role> roles) {
        String email = "walkin-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass12345"));
        user.setFirstName("Staff");
        user.setLastName("User");
        user.setRoles(roles);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        var login = restTemplate.postForEntity("/api/v1/auth/login", new LoginRequest(email, "pass12345"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().accessToken();
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private List<EntryAuditLogRecord> entryLog(Long entryId) {
        return dsl.selectFrom(ENTRY_AUDIT_LOG)
                .where(ENTRY_AUDIT_LOG.ENTRY_ID.eq(entryId))
                .orderBy(ENTRY_AUDIT_LOG.CREATED_AT, ENTRY_AUDIT_LOG.ID)
                .fetch();
    }
}
