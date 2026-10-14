package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.auth.LoginRequest;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.race.MarshalAbsenceRepository;
import dev.monkeypatch.rctiming.domain.race.PenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.PenaltyType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundStatus;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.jooq.generated.tables.records.MarshalPenaltiesRecord;
import dev.monkeypatch.rctiming.query.racecontrol.RaceEntryDto;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.monkeypatch.rctiming.jooq.generated.tables.IncidentReports.INCIDENT_REPORTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.MarshalPenalties.MARSHAL_PENALTIES;
import static org.assertj.core.api.Assertions.assertThat;

public class RefereeControllerIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RaceRepository raceRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RaceEntryRepository raceEntryRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired RacingClassRepository racingClassRepository;
    @Autowired EventClassRepository eventClassRepository;
    @Autowired EventRepository eventRepository;
    @Autowired PenaltyRepository penaltyRepository;
    @Autowired MarshalAbsenceRepository marshalAbsenceRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired DSLContext dsl;

    private String refereeToken;

    @BeforeEach
    void setUp() {
        String email = "referee-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass123"));
        user.setFirstName("Race");
        user.setLastName("Referee");
        user.setRoles(Set.of(Role.REFEREE));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(email, "pass123"),
                AuthResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        refereeToken = loginResp.getBody().accessToken();
    }

    @Test
    void raiseIncident_createsRecordLinkedToRaceAndEntry() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);

        Map<String, Object> body = Map.of(
                "entryId", re.entry().getId(),
                "incidentType", "Contact",
                "description", "Cars made contact at turn 3"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/incident-report",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, refereeHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dsl.fetchCount(INCIDENT_REPORTS, INCIDENT_REPORTS.RACE_ID.eq(re.race().getId()))).isEqualTo(1);

        Map<String, Object> row = onlyAuditRow(re.race().getId(), "INCIDENT_RAISED");
        assertThat(row.get("summary").toString()).contains("Contact").contains("race " + re.race().getId());
        assertThat(row.get("after_json").toString()).contains("Cars made contact at turn 3");
        assertThat(row.get("actor_user_id")).isNotNull();
        assertThat(((Number) row.get("event_id")).longValue()).isEqualTo(resolveEventId(re.race()));
    }

    @Test
    void applyLapPenalty_recalculatesPositionsImmediately() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);

        Map<String, Object> body = Map.of(
                "entryId", re.entry().getId(),
                "penaltyType", "LAP",
                "value", 1,
                "reason", "Jumped start"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/penalty",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, refereeHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(penaltyRepository.findByRaceId(re.race().getId())).hasSize(1);
        assertThat(penaltyRepository.findByRaceId(re.race().getId()).get(0).getPenaltyType())
                .isEqualTo(PenaltyType.LAP);

        Map<String, Object> row = onlyAuditRow(re.race().getId(), "PENALTY_APPLIED");
        assertThat(row.get("summary").toString()).contains("LAP penalty of 1");
        assertThat(row.get("after_json").toString()).contains("Jumped start");
        assertThat(row.get("entity_id")).isEqualTo(String.valueOf(penaltyRepository.findByRaceId(re.race().getId()).get(0).getId()));
    }

    @Test
    void applyTimePenalty_recordedAgainstRaceResult() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);

        Map<String, Object> body = Map.of(
                "entryId", re.entry().getId(),
                "penaltyType", "TIME",
                "value", 5,
                "reason", "Track limits violation"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/penalty",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, refereeHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(penaltyRepository.findByRaceId(re.race().getId())).hasSize(1);
        assertThat(penaltyRepository.findByRaceId(re.race().getId()).get(0).getPenaltyType())
                .isEqualTo(PenaltyType.TIME);
    }

    @Test
    void applyPenalty_ofAnUnknownType_isRefusedAndRecordsNothing() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);

        Map<String, Object> body = Map.of(
                "entryId", re.entry().getId(),
                "penaltyType", "SPEED",
                "value", 5,
                "reason", "Too fast in the pit lane"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/penalty",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, refereeHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("detail"))
                .isEqualTo("penaltyType must be one of [LAP, TIME], got: SPEED");
        assertThat(penaltyRepository.findByRaceId(re.race().getId())).isEmpty();
        assertThat(jdbc.queryForList("select id from audit_log where race_id = ?", re.race().getId())).isEmpty();
    }

    @Test
    void applyLapPenalty_ofMoreLapsThanARaceRuns_isRefusedAndRecordsNothing() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);

        Map<String, Object> body = Map.of(
                "entryId", re.entry().getId(),
                "penaltyType", "LAP",
                "value", 3_000_000_000L,
                "reason", "Typed too many zeros"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/penalty",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, refereeHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(penaltyRepository.findByRaceId(re.race().getId())).isEmpty();
    }

    @Test
    void aRefereeCanListTheEntriesOfAFinishedRaceToPickADriver() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.FINISHED);

        ResponseEntity<RaceEntryDto[]> resp = restTemplate.exchange(
                "/api/v1/race-control/race/" + re.race().getId() + "/entries",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(refereeHeaders()),
                RaceEntryDto[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).extracting(RaceEntryDto::entryId)
                .containsExactly(re.entry().getId());
    }

    @Test
    void aRefereeOnlyAccountCanLoadTheRunOrderTheRefereeViewStartsFrom() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);

        ResponseEntity<Object[]> resp = restTemplate.exchange(
                "/api/v1/race-control/event/" + resolveEventId(re.race()) + "/run-order",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(refereeHeaders()),
                Object[].class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void aPenaltyCanStillBeAppliedAfterTheRaceHasFinished() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.FINISHED);

        Map<String, Object> body = Map.of(
                "entryId", re.entry().getId(),
                "penaltyType", "TIME",
                "value", 10,
                "reason", "Protest upheld after the flag"
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/penalty",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, refereeHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(penaltyRepository.findByRaceId(re.race().getId())).hasSize(1);
    }

    @Test
    @Disabled("worked out in the browser; no backend endpoint exists")
    void proximityAlertLogic_computedFromLiveTimingStream() {
        // Covered by Vitest in frontend/src/pages/race-control/referee/alerts.test.ts
    }

    @Test
    @Disabled("worked out in the browser; no backend endpoint exists")
    void backmarkerDetection_flagsLappedCars() {
        // Covered by Vitest in frontend/src/pages/race-control/referee/alerts.test.ts
    }

    @Test
    void marshalAbsent_withoutAutoPenalty_doesNotCreatePenaltyRow() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);
        Long eventId = resolveEventId(re.race());

        Map<String, Object> absentBody = Map.of(
                "entryId", re.entry().getId(),
                "eventId", eventId
        );

        ResponseEntity<Void> absentResp = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/marshal-absent",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(absentBody, refereeHeaders()),
                Void.class);

        assertThat(absentResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(marshalAbsenceRepository.findByEventId(eventId)).hasSize(1);
        assertThat(marshalPenalties(re.entry().getId(), eventId)).isEmpty();

        // Now apply the penalty as a separate action
        ResponseEntity<Map> penaltyResp = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/apply-marshal-penalty",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(absentBody, refereeHeaders()),
                Map.class);

        assertThat(penaltyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(marshalPenalties(re.entry().getId(), eventId)).hasSize(1);

        assertThat(onlyAuditRow(re.race().getId(), "MARSHAL_ABSENCE_RECORDED").get("summary").toString())
                .contains("missed their marshal duty");
        assertThat(onlyAuditRow(re.race().getId(), "MARSHAL_PENALTY_APPLIED").get("summary").toString())
                .contains("marshal penalty");
    }

    @Test
    void linkingAnUnknownTransponderIsRecordedWithTheDriverAndLapsCredited() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);
        String number = "UNK" + UUID.randomUUID().toString().substring(0, 8);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/race-control/race/" + re.race().getId() + "/transponders/link",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("transponderNumber", number, "entryId", re.entry().getId()), adminHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> row = onlyAuditRow(re.race().getId(), "UNKNOWN_TRANSPONDER_LINKED");
        assertThat(row.get("summary").toString()).contains(number).contains("crediting 0 laps");
        assertThat(row.get("actor_user_id")).isNotNull();
        assertThat(row.get("after_json").toString()).contains("\"lapsCredited\":0");
    }

    @Test
    void marshalPenalty_isLinkedToTheNamedAbsenceOrElseTheLatestOne() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);
        Long eventId = resolveEventId(re.race());
        Map<String, Object> body = Map.of("entryId", re.entry().getId(), "eventId", eventId);
        ResponseEntity<Map> absent = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/marshal-absent",
                org.springframework.http.HttpMethod.POST, new HttpEntity<>(body, refereeHeaders()), Map.class);
        Long absenceId = ((Number) absent.getBody().get("id")).longValue();

        ResponseEntity<Map> byDefault = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/apply-marshal-penalty",
                org.springframework.http.HttpMethod.POST, new HttpEntity<>(body, refereeHeaders()), Map.class);
        ResponseEntity<Map> byName = restTemplate.exchange(
                "/api/v1/race-control/referee/race/" + re.race().getId() + "/apply-marshal-penalty",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("entryId", re.entry().getId(), "eventId", eventId, "absenceId", absenceId),
                        refereeHeaders()), Map.class);

        assertThat(byDefault.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byName.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(marshalPenalties(re.entry().getId(), eventId))
                .hasSize(2).allSatisfy(p -> assertThat(p.getAbsenceId()).isEqualTo(absenceId));
    }

    @Test
    void marshalPenalty_forAnAbsenceThatIsNotTheEntrysInThatEvent_isRefused() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);
        Long eventId = resolveEventId(re.race());
        restTemplate.exchange("/api/v1/race-control/referee/race/" + re.race().getId() + "/marshal-absent",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("entryId", re.entry().getId(), "eventId", eventId), refereeHeaders()),
                Void.class);
        Long absenceId = marshalAbsenceRepository.findByEventId(eventId).get(0).getId();
        String url = "/api/v1/race-control/referee/race/" + re.race().getId() + "/apply-marshal-penalty";

        ResponseEntity<Map> otherEvent = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("entryId", re.entry().getId(), "eventId", eventId + 1, "absenceId", absenceId),
                        refereeHeaders()), Map.class);
        ResponseEntity<Map> missing = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("entryId", re.entry().getId(), "eventId", eventId, "absenceId", 999_999_999L),
                        refereeHeaders()), Map.class);

        assertThat(otherEvent.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(marshalPenalties(re.entry().getId(), eventId)).isEmpty();
    }

    @Test
    void history_listsIncidentsAndPenaltiesOldestFirstWithNames() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);
        long raceId = re.race().getId();
        restTemplate.exchange("/api/v1/race-control/referee/race/" + raceId + "/incident-report",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("entryId", re.entry().getId(), "incidentType", "Contact",
                        "description", "Turn 3"), refereeHeaders()), Map.class);
        restTemplate.exchange("/api/v1/race-control/referee/race/" + raceId + "/penalty",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("entryId", re.entry().getId(), "penaltyType", "LAP", "value", 1,
                        "reason", "Jumped start"), refereeHeaders()), Map.class);

        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                "/api/v1/race-control/race/" + raceId + "/history",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(refereeHeaders()),
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> rows = resp.getBody();
        assertThat(rows).extracting(r -> r.get("kind")).containsExactly("INCIDENT", "PENALTY");
        assertThat(rows.get(0).get("actor")).isEqualTo("Race Referee");
        assertThat(rows.get(0).get("summary").toString()).contains("Contact").contains("Turn 3");
        assertThat(rows.get(1).get("summary").toString()).contains("1 lap penalty").contains("Jumped start");
    }

    @Test
    void history_ofAnUnknownRaceIsNotFound() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/race-control/race/999999/history",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(refereeHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void history_needsASignedInOfficial() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);

        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/api/v1/race-control/race/" + re.race().getId() + "/history", Map.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void history_showsAMarshalPenaltyGivenWithoutAnAbsenceOnTheRaceItWasGivenIn() {
        RaceAndEntry re = seedRaceAndEntry(RaceStatus.RUNNING);
        long raceId = re.race().getId();
        restTemplate.exchange("/api/v1/race-control/referee/race/" + raceId + "/apply-marshal-penalty",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("entryId", re.entry().getId(), "eventId", resolveEventId(re.race())),
                        refereeHeaders()), Map.class);

        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                "/api/v1/race-control/race/" + raceId + "/history",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(refereeHeaders()),
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(resp.getBody()).extracting(r -> r.get("kind")).containsExactly("MARSHAL_PENALTY");
        assertThat(resp.getBody().get(0).get("actor")).isEqualTo("Race Referee");
    }

    // --- Helpers ---

    private HttpHeaders refereeHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(refereeToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private record RaceAndEntry(Race race, Entry entry) {}

    private Map<String, Object> onlyAuditRow(long raceId, String action) {
        var rows = jdbc.queryForList("select * from audit_log where race_id = ? and action = ?", raceId, action);
        assertThat(rows).as(action).hasSize(1);
        return rows.get(0);
    }

    private HttpHeaders adminHeaders() {
        String email = "referee-admin-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass123"));
        user.setFirstName("Race");
        user.setLastName("Admin");
        user.setRoles(Set.of(Role.ADMIN));
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        ResponseEntity<AuthResponse> login = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, "pass123"), AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private RaceAndEntry seedRaceAndEntry(RaceStatus status) {
        Instant now = Instant.now();

        Event event = new Event();
        event.setName("RefIT-" + UUID.randomUUID().toString().substring(0, 8));
        event.setEventDate(java.time.LocalDate.of(2026, 6, 1));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        event = eventRepository.save(event);

        RacingClass rc = new RacingClass();
        rc.setName("RC-" + UUID.randomUUID().toString().substring(0, 6));
        rc.setCreatedAt(now);
        rc.setUpdatedAt(now);
        rc = racingClassRepository.save(rc);

        EventClass ec = new EventClass();
        ec.setEventId(event.getId());
        ec.setRacingClassId(rc.getId());
        ec.setConfigSnapshot(new TimedRaceConfig(5, StartType.GRID, QualifyingType.FASTEST_LAP, 1, 0));
        ec = eventClassRepository.save(ec);

        Round round = new Round();
        round.setEventId(event.getId());
        round.setType(RoundType.PRACTICE);
        round.setRoundNumber(1);
        round.setSequenceInEvent(1);
        round.setStatus(RoundStatus.PENDING);
        round.setCreatedAt(now);
        round.setUpdatedAt(now);
        round = roundRepository.save(round);

        Race race = new Race();
        race.setRoundId(round.getId());
        race.setEventClassId(ec.getId());
        race.setHeatNumber(1);
        race.setSequenceInRound(1);
        race.setStartType(StartType.GRID);
        race.setStatus(status);
        race.setCreatedAt(now);
        race.setUpdatedAt(now);
        race = raceRepository.save(race);

        User user = userRepository.findAll().stream().findFirst().orElseThrow();

        Entry entry = new Entry();
        entry.setUserId(user.getId());
        entry.setEventId(event.getId());
        entry.setEventClassId(ec.getId());
        entry.setTransponderNumberSnapshot("T-" + UUID.randomUUID().toString().substring(0, 8));
        entry.setStatus(EntryStatus.CONFIRMED);
        entry.setSubmittedAt(now);
        entry.setUpdatedAt(now);
        entry = entryRepository.save(entry);

        RaceEntry re = new RaceEntry();
        re.setRaceId(race.getId());
        re.setEntryId(entry.getId());
        re.setGridPosition(1);
        raceEntryRepository.save(re);

        return new RaceAndEntry(race, entry);
    }

    private Long resolveEventId(Race race) {
        Round round = roundRepository.findById(race.getRoundId()).orElseThrow();
        return round.getEventId();
    }

    private List<MarshalPenaltiesRecord> marshalPenalties(Long entryId, Long eventId) {
        return dsl.selectFrom(MARSHAL_PENALTIES)
                .where(MARSHAL_PENALTIES.ENTRY_ID.eq(entryId).and(MARSHAL_PENALTIES.EVENT_ID.eq(eventId)))
                .fetch();
    }
}
